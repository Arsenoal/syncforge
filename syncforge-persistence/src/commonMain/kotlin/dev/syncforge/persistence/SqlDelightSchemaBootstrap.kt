package dev.syncforge.persistence

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * [generateAsync] makes [SyncForgePersistenceDatabase.Schema] async-only; platform drivers that
 * require a synchronous [SqlSchema] at construction get a no-op stub, then real DDL via [awaitCreate].
 */
internal object SyncForgePersistenceNoOpSyncSchema : SqlSchema<QueryResult.Value<Unit>> {
    override val version: Long
        get() = SyncForgePersistenceDatabase.Schema.version

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Value(Unit)

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.Value<Unit> = QueryResult.Value(Unit)
}

internal expect fun bootstrapSyncForgePersistenceSchema(driver: SqlDriver)