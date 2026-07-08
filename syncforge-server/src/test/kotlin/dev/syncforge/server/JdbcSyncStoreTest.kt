package dev.syncforge.server

import dev.syncforge.model.ChangeType
import dev.syncforge.network.api.OutboxEntryDto
import org.h2.jdbcx.JdbcDataSource
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class JdbcSyncStoreTest : SyncStoreContractTest() {

    private lateinit var dataSource: JdbcDataSource

    @Before
    fun setUp() {
        dataSource = JdbcDataSource().apply {
            setURL("jdbc:h2:mem:syncforge-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
            user = "sa"
            password = ""
        }
        JdbcSyncStore.ensureSchema(dataSource)
    }

    override fun createStore(): SyncStore = JdbcSyncStore(dataSource)

    override fun tombstoneEntity(store: SyncStore, entityType: String, entityId: String, nowMillis: Long) {
        store.push(
            listOf(
                OutboxEntryDto(
                    id = 99,
                    entityType = entityType,
                    entityId = entityId,
                    changeType = ChangeType.DELETE,
                    payloadJson = null,
                    localVersion = 2,
                    createdAtMillis = nowMillis,
                ),
            ),
            nowMillis = nowMillis,
        )
    }

    @Test
    fun push_persistsAcrossStoreInstances() {
        val first = JdbcSyncStore(dataSource)
        first.push(
            listOf(
                OutboxEntryDto(
                    id = 1,
                    entityType = "tasks",
                    entityId = "task-1",
                    changeType = ChangeType.CREATE,
                    payloadJson = """{"title":"Persisted"}""",
                    localVersion = 1,
                    createdAtMillis = 1,
                ),
            ),
            nowMillis = 100L,
        )

        val second = JdbcSyncStore(dataSource)
        val pull = second.pull(0L, setOf("tasks"), 200L)
        assertTrue(pull.deltas.single().payloadJson!!.contains("Persisted"))
    }
}