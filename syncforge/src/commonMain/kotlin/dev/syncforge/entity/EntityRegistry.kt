package dev.syncforge.entity

/**
 * Maps entity type strings (e.g. `"tasks"`) to their [EntitySyncHandler].
 */
class EntityRegistry(handlers: List<EntitySyncHandler>) {

    private val handlersByType: Map<String, EntitySyncHandler> =
        handlers.associateBy { it.entityType }

    init {
        require(handlers.isNotEmpty()) { "At least one EntitySyncHandler must be registered" }
        require(handlersByType.size == handlers.size) {
            "Duplicate entityType registrations: ${handlers.groupingBy { it.entityType }.eachCount().filter { it.value > 1 }.keys}"
        }
    }

    fun requireHandler(entityType: String): EntitySyncHandler =
        handlersByType[entityType]
            ?: error("No EntitySyncHandler registered for entityType='$entityType'")

    fun entityTypes(): Set<String> = handlersByType.keys

    companion object {
        fun of(vararg handlers: EntitySyncHandler): EntityRegistry =
            EntityRegistry(handlers.toList())

        fun of(handlers: Iterable<EntitySyncHandler>): EntityRegistry =
            EntityRegistry(handlers.toList())
    }
}