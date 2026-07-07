package dev.syncforge.ksp

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType

internal enum class FieldMergeKind {
    LastWriteWins,
    ObservedRemoveSet,
    GrowOnlyCounter,
}

internal data class FieldMergeSpec(
    val propertyName: String,
    val kind: FieldMergeKind,
)

internal object FieldMergeCodegen {

    private val syncedMetadataFields = setOf("id", "localVersion", "updatedAtMillis", "syncState")

    fun collectMergeFields(
        entity: KSClassDeclaration,
        logger: KSPLogger,
    ): List<FieldMergeSpec> {
        val specs = mutableListOf<FieldMergeSpec>()
        entity.declarations
            .filterIsInstance<KSPropertyDeclaration>()
            .forEach { property ->
            val kind = property.resolveMergeKind(logger) ?: return@forEach
            if (property.simpleName.asString() in syncedMetadataFields) {
                logger.warn(
                    "Field merge annotation on SyncedEntity metadata property '${property.simpleName.asString()}' is ignored",
                    property,
                )
                return@forEach
            }
            if (!property.validateTypeForKind(kind, logger)) return@forEach
            specs += FieldMergeSpec(property.simpleName.asString(), kind)
        }
        return specs.sortedBy { it.propertyName }
    }

    fun generateSource(
        packageName: String,
        entityName: String,
        entityType: String,
        fields: List<FieldMergeSpec>,
    ): String {
        val mergeName = "${entityName}FieldMerge"
        val copyArgs = buildList {
            fields.forEach { field ->
                add(fieldMergeExpression(field))
            }
            add("updatedAtMillis = maxOf(local.updatedAtMillis, remote.updatedAtMillis)")
            add("localVersion = maxOf(local.localVersion, remote.localVersion)")
        }.joinToString(separator = ",\n            ")

        return """
            package $packageName

            /**
             * Auto-generated field merge for $entityName — wire in `conflicts { }`:
             *
             * ```
             * entity("$entityType") {
             *     merge<$entityName> { local, remote ->
             *         $mergeName.merge(local, remote, remoteMeta.updatedAtMillis)
             *     }
             * }
             * ```
             */
            object $mergeName {

                fun merge(
                    local: $entityName,
                    remote: $entityName,
                    remoteUpdatedAtMillis: Long,
                ): $entityName = local.copy(
                    $copyArgs
                )
            }
            """.trimIndent()
    }

    private fun fieldMergeExpression(field: FieldMergeSpec): String =
        when (field.kind) {
            FieldMergeKind.LastWriteWins ->
                "${field.propertyName} = if (remoteUpdatedAtMillis >= local.updatedAtMillis) " +
                    "remote.${field.propertyName} else local.${field.propertyName}"

            FieldMergeKind.ObservedRemoveSet ->
                "${field.propertyName} = (local.${field.propertyName} + remote.${field.propertyName}).distinct()"

            FieldMergeKind.GrowOnlyCounter ->
                "${field.propertyName} = maxOf(local.${field.propertyName}, remote.${field.propertyName})"
        }

    private fun KSPropertyDeclaration.resolveMergeKind(logger: KSPLogger): FieldMergeKind? {
        val kinds = buildList {
            if (hasAnnotation("LastWriteWins")) add(FieldMergeKind.LastWriteWins)
            if (hasAnnotation("ObservedRemoveSet")) add(FieldMergeKind.ObservedRemoveSet)
            if (hasAnnotation("GrowOnlyCounter")) add(FieldMergeKind.GrowOnlyCounter)
        }
        return when {
            kinds.isEmpty() -> null
            kinds.size > 1 -> {
                logger.error(
                    "Property '${simpleName.asString()}' has multiple field-merge annotations — use one of " +
                        "LastWriteWins, ObservedRemoveSet, GrowOnlyCounter",
                    this,
                )
                null
            }
            else -> kinds.single()
        }
    }

    private fun KSPropertyDeclaration.validateTypeForKind(
        kind: FieldMergeKind,
        logger: KSPLogger,
    ): Boolean {
        val resolved = type.resolve()
        return when (kind) {
            FieldMergeKind.LastWriteWins -> true
            FieldMergeKind.ObservedRemoveSet -> {
                if (resolved.isListOrSet()) true
                else {
                    logger.error(
                        "@ObservedRemoveSet on '${simpleName.asString()}' requires List<T> or Set<T>",
                        this,
                    )
                    false
                }
            }
            FieldMergeKind.GrowOnlyCounter -> {
                if (resolved.isIntOrLong()) true
                else {
                    logger.error(
                        "@GrowOnlyCounter on '${simpleName.asString()}' requires Int or Long",
                        this,
                    )
                    false
                }
            }
        }
    }

    private fun KSPropertyDeclaration.hasAnnotation(shortName: String): Boolean =
        annotations.any { it.shortName.getShortName() == shortName }

    private fun KSType.isListOrSet(): Boolean {
        val name = declaration.qualifiedName?.asString() ?: return false
        return name == "kotlin.collections.List" || name == "kotlin.collections.Set"
    }

    private fun KSType.isIntOrLong(): Boolean {
        val name = declaration.qualifiedName?.asString() ?: return false
        return name == "kotlin.Int" || name == "kotlin.Long"
    }
}