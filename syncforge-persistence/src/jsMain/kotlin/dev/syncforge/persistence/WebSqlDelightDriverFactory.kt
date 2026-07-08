package dev.syncforge.persistence

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

internal fun createWebSqlDelightDriver(): SqlDriver =
    WebWorkerDriver(
        Worker(
            js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)"""),
        ),
    )