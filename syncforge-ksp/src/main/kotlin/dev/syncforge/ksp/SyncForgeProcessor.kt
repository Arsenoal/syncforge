package dev.syncforge.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile


class SyncForgeProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor =
        SyncForgeProcessor(environment.codeGenerator, environment.logger)
}

private sealed class EntityBinding {
    abstract val adapterName: String
    abstract val adapterPackage: String
    abstract val paramName: String
    abstract val sourceFile: KSFile?

    data class Dao(
        override val adapterName: String,
        override val adapterPackage: String,
        override val paramName: String,
        override val sourceFile: KSFile?,
    ) : EntityBinding()

    data class Store(
        override val adapterName: String,
        override val adapterPackage: String,
        override val paramName: String,
        override val sourceFile: KSFile?,
    ) : EntityBinding()
}

private data class EntityCodegenInfo(
    val entityName: String,
    val packageName: String,
    val entityType: String,
    val qualifiedEntity: String,
    val handlerName: String,
    val binding: EntityBinding,
    val sourceFiles: List<KSFile>,
)

class SyncForgeProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val entities = resolver.getSymbolsWithAnnotation(SYNCFORGE_ENTITY)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.hasAnnotation("SyncForgeEntity") }
            .sortedBy { it.qualifiedName?.asString().orEmpty() }
            .toList()

        if (entities.isEmpty()) return emptyList()

        val daosByEntity = resolver.getSymbolsWithAnnotation(SYNCFORGE_DAO)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.hasAnnotation("SyncForgeDao") }
            .associateBy { dao -> dao.annotationEntityClass("SyncForgeDao") }

        val storesByEntity = resolver.getSymbolsWithAnnotation(SYNCFORGE_STORE)
            .filterIsInstance<KSClassDeclaration>()
            .filter { it.hasAnnotation("SyncForgeStore") }
            .associateBy { store -> store.annotationEntityClass("SyncForgeStore") }

        val codegenInfos = mutableListOf<EntityCodegenInfo>()

        entities.forEach { entity ->
            val entityName = entity.simpleName.asString()
            val packageName = entity.packageName.asString()
            val entityType = entity.annotationEntityType()
            val qualifiedEntity = "$packageName.$entityName"
            val handlerName = "${entityName}SyncHandler"

            val dao = daosByEntity[qualifiedEntity]
            val store = storesByEntity[qualifiedEntity]

            val binding = when {
                dao != null && store != null -> {
                    logger.error(
                        "@SyncForgeEntity $qualifiedEntity has both @SyncForgeDao and @SyncForgeStore — use one adapter",
                        entity,
                    )
                    return@forEach
                }
                dao != null -> {
                    val daoName = dao.simpleName.asString()
                    EntityBinding.Dao(
                        adapterName = daoName,
                        adapterPackage = dao.packageName.asString(),
                        paramName = entityName.daoParamName(),
                        sourceFile = dao.containingFile,
                    )
                }
                store != null -> {
                    if (!store.implementsEntityStoreFor(qualifiedEntity)) {
                        logger.error(
                            "@SyncForgeStore ${store.qualifiedName?.asString()} must implement EntityStore<$qualifiedEntity>",
                            store,
                        )
                        return@forEach
                    }
                    val storeName = store.simpleName.asString()
                    EntityBinding.Store(
                        adapterName = storeName,
                        adapterPackage = store.packageName.asString(),
                        paramName = storeName.replaceFirstChar { it.lowercase() },
                        sourceFile = store.containingFile,
                    )
                }
                else -> {
                    logger.error(
                        "No @SyncForgeDao or @SyncForgeStore found for $qualifiedEntity",
                        entity,
                    )
                    return@forEach
                }
            }

            val sourceFiles = listOfNotNull(entity.containingFile, binding.sourceFile)
            val info = EntityCodegenInfo(
                entityName = entityName,
                packageName = packageName,
                entityType = entityType,
                qualifiedEntity = qualifiedEntity,
                handlerName = handlerName,
                binding = binding,
                sourceFiles = sourceFiles,
            )
            codegenInfos += info
            generateHandler(info)
        }

        if (codegenInfos.isNotEmpty()) {
            generateHandlersRegistry(codegenInfos)
        }

        return emptyList()
    }

    private fun generateHandler(info: EntityCodegenInfo) {
        val handlerBody = when (val binding = info.binding) {
            is EntityBinding.Dao -> daoHandlerSource(info, binding)
            is EntityBinding.Store -> storeHandlerSource(info, binding)
        }

        codeGenerator.createNewFile(
            dependencies = Dependencies(false, *info.sourceFiles.toTypedArray()),
            packageName = info.packageName,
            fileName = info.handlerName,
        ).use { stream ->
            stream.write(handlerBody.trimIndent().toByteArray())
        }
    }

    private fun daoHandlerSource(info: EntityCodegenInfo, binding: EntityBinding.Dao): String =
        """
        package ${info.packageName}

        import dev.syncforge.entity.TypedEntitySyncHandler
        import dev.syncforge.model.SyncState
        import kotlinx.serialization.json.Json
        import kotlinx.serialization.encodeToString
        import kotlinx.serialization.decodeFromString

        /**
         * Auto-generated by SyncForge KSP — do not edit manually.
         */
        class ${info.handlerName}(
            private val dao: ${binding.adapterPackage}.${binding.adapterName},
            private val json: Json = Json { ignoreUnknownKeys = true },
        ) : TypedEntitySyncHandler<${info.entityName}>() {

            override val entityType: String = "${info.entityType}"

            override fun toJson(entity: ${info.entityName}): String = json.encodeToString(entity)

            override fun fromJson(jsonString: String): ${info.entityName} = json.decodeFromString(jsonString)

            override suspend fun findById(id: String): ${info.entityName}? = dao.findById(id)

            override suspend fun insert(entity: ${info.entityName}) { dao.insert(entity) }

            override suspend fun update(entity: ${info.entityName}) { dao.update(entity) }

            override suspend fun deleteById(id: String) { dao.deleteById(id) }

            override fun withSyncState(entity: ${info.entityName}, state: SyncState): ${info.entityName} =
                entity.copy(syncState = state)
        }
        """

    private fun storeHandlerSource(info: EntityCodegenInfo, binding: EntityBinding.Store): String =
        """
        package ${info.packageName}

        import dev.syncforge.entity.EntityStoreSyncHandler
        import dev.syncforge.model.SyncState
        import kotlinx.serialization.json.Json
        import kotlinx.serialization.encodeToString
        import kotlinx.serialization.decodeFromString

        /**
         * Auto-generated by SyncForge KSP — do not edit manually.
         */
        class ${info.handlerName}(
            store: ${binding.adapterPackage}.${binding.adapterName},
            private val json: Json = Json { ignoreUnknownKeys = true },
        ) : EntityStoreSyncHandler<${info.entityName}>(store) {

            override val entityType: String = "${info.entityType}"

            override fun toJson(entity: ${info.entityName}): String = json.encodeToString(entity)

            override fun fromJson(jsonString: String): ${info.entityName} = json.decodeFromString(jsonString)

            override fun withSyncState(entity: ${info.entityName}, state: SyncState): ${info.entityName} =
                entity.copy(syncState = state)
        }
        """

    private fun generateHandlersRegistry(infos: List<EntityCodegenInfo>) {
        val packageName = infos.first().packageName
        val allSourceFiles = infos.flatMap { it.sourceFiles }.distinct().toTypedArray()

        val factoryFunctions = infos.joinToString(separator = "\n\n    ") { info ->
            val binding = info.binding
            """
            fun ${info.entityType}(${binding.paramName}: ${binding.adapterPackage}.${binding.adapterName}): ${info.handlerName} =
                ${info.handlerName}(${binding.paramName})
            """.trimIndent()
        }

        val registryParams = infos.joinToString(separator = ",\n        ") { info ->
            "${info.binding.paramName}: ${info.binding.adapterPackage}.${info.binding.adapterName}"
        }
        val registryHandlers = infos.joinToString(separator = ", ") { info ->
            "${info.entityType}(${info.binding.paramName})"
        }
        val handlerImports = infos
            .distinctBy { it.packageName to it.handlerName }
            .joinToString(separator = "\n") { info ->
                "import ${info.packageName}.${info.handlerName}"
            }

        codeGenerator.createNewFile(
            dependencies = Dependencies(false, *allSourceFiles),
            packageName = packageName,
            fileName = "SyncForgeHandlers",
        ).use { stream ->
            stream.write(
                """
                package $packageName

                import dev.syncforge.entity.EntityRegistry
                $handlerImports

                /**
                 * Auto-generated by SyncForge KSP — wires all @SyncForgeEntity handlers for this module.
                 */
                object SyncForgeHandlers {

                    $factoryFunctions

                    fun registry(
                        $registryParams,
                    ): EntityRegistry = EntityRegistry.of($registryHandlers)
                }
                """.trimIndent().toByteArray(),
            )
        }
    }

    private fun KSClassDeclaration.hasAnnotation(shortName: String): Boolean =
        annotations.any { it.shortName.getShortName() == shortName }

    private fun KSClassDeclaration.annotationEntityClass(annotationShortName: String): String {
        val annotation = annotations.first { it.shortName.getShortName() == annotationShortName }
        return annotation.arguments.first().value as String
    }

    private fun KSClassDeclaration.annotationEntityType(): String =
        annotationEntityClass("SyncForgeEntity")

    private fun String.daoParamName(): String =
        removeSuffix("Entity")
            .replaceFirstChar { if (it.isUpperCase()) it.lowercase() else it.toString() } + "Dao"

    private fun KSClassDeclaration.implementsEntityStoreFor(qualifiedEntity: String): Boolean =
        superTypes.any { superType ->
            val resolved = superType.resolve()
            val declaration = resolved.declaration as? KSClassDeclaration ?: return@any false
            if (declaration.qualifiedName?.asString() != ENTITY_STORE_INTERFACE) {
                return@any false
            }
            val entityTypeArg = resolved.arguments.firstOrNull()?.type?.resolve() ?: return@any false
            val entityDeclaration = entityTypeArg.declaration as? KSClassDeclaration ?: return@any false
            entityDeclaration.qualifiedName?.asString() == qualifiedEntity
        }

    private companion object {
        const val SYNCFORGE_ENTITY = "dev.syncforge.annotations.SyncForgeEntity"
        const val SYNCFORGE_DAO = "dev.syncforge.annotations.SyncForgeDao"
        const val SYNCFORGE_STORE = "dev.syncforge.annotations.SyncForgeStore"
        const val ENTITY_STORE_INTERFACE = "dev.syncforge.entity.EntityStore"
    }
}