package dev.syncforge.persistence

import app.cash.sqldelight.driver.worker.WebWorkerDriver
import dev.syncforge.api.ExperimentalSyncForgeApi
import org.w3c.dom.Worker

/**
 * Browser persistence via SQLDelight web-worker driver (SQL.js in a dedicated worker).
 *
 * [databaseName] is reserved for future IndexedDB-backed naming; the worker driver uses
 * in-memory SQL.js storage for 1.6-01 compile verification.
 */
@ExperimentalSyncForgeApi
actual fun createDefaultSyncForgePersistence(databaseName: String): SyncForgePersistence {
    val driver = WebWorkerDriver(
        Worker(
            js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)"""),
        ),
    )
    // Schema is created lazily on first query via the async driver; explicit awaitCreate
    // runs from suspend entry points (1.6-02). Compile-only factory for 1.6-01.
    return SyncForgePersistence.create(driver)
}