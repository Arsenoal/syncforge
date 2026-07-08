package dev.syncforge.persistence

import app.cash.sqldelight.async.coroutines.awaitCreate
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import dev.syncforge.api.ExperimentalSyncForgeApi
import org.w3c.dom.Worker

/**
 * Suspend browser persistence factory — prefer this over [createDefaultSyncForgePersistence] on JS
 * so SQLDelight schema creation runs before the first query (1.6-02+).
 */
@ExperimentalSyncForgeApi
suspend fun createWebSyncForgePersistence(databaseName: String = "syncforge.db"): SyncForgePersistence {
    val driver = WebWorkerDriver(
        Worker(
            js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)"""),
        ),
    )
    SyncForgePersistenceDatabase.Schema.awaitCreate(driver)
    return SyncForgePersistence.create(driver)
}