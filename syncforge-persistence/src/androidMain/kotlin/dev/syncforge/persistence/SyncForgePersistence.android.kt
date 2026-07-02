package dev.syncforge.persistence

import android.content.Context
import dev.syncforge.api.ExperimentalSyncForgeApi
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/**
 * Creates file-backed SQLDelight persistence on Android.
 *
 * Default database name is `syncforge.db` — separate from Room's `syncforge_outbox.db`,
 * so both stores can coexist during migration.
 *
 * Opt in via [dev.syncforge.SyncForge.android]:
 * ```
 * SyncForge.android(context) {
 *     useSqlDelightPersistence()
 *     // or: persistence(createSyncForgePersistence(context))
 * }
 * ```
 */
@ExperimentalSyncForgeApi
fun createSyncForgePersistence(
    context: Context,
    databaseName: String = DEFAULT_DATABASE_NAME,
): SyncForgePersistence {
    val driver = AndroidSqliteDriver(
        schema = SyncForgePersistenceDatabase.Schema,
        context = context.applicationContext,
        name = databaseName,
    )
    return SyncForgePersistence.create(driver)
}

/** Android persistence factory — parallel to iOS [createDefaultSyncForgePersistence]. */
@ExperimentalSyncForgeApi
object SyncForgePersistenceFactory {

    @ExperimentalSyncForgeApi
    fun create(
        context: Context,
        databaseName: String = DEFAULT_DATABASE_NAME,
    ): SyncForgePersistence = createSyncForgePersistence(context, databaseName)
}

const val DEFAULT_DATABASE_NAME: String = "syncforge.db"

@ExperimentalSyncForgeApi
actual fun createDefaultSyncForgePersistence(databaseName: String): SyncForgePersistence =
    error(
        "createDefaultSyncForgePersistence() requires Context on Android. " +
            "Use createSyncForgePersistence(context) or SyncForge.android { } (Room default).",
    )