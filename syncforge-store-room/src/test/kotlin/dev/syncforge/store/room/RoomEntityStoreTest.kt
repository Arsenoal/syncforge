package dev.syncforge.store.room

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.test.core.app.ApplicationProvider
import dev.syncforge.api.ExperimentalSyncForgeApi
import dev.syncforge.entity.SyncedEntity
import dev.syncforge.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalSyncForgeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoomEntityStoreTest {

    @Entity(tableName = "test_items")
    data class TestEntity(
        @PrimaryKey override val id: String,
        val label: String,
        override val localVersion: Long = 0,
        override val updatedAtMillis: Long = 0,
        override val syncState: SyncState = SyncState.SYNCED,
    ) : SyncedEntity

    @Dao
    interface TestDao : SyncForgeRoomDao<TestEntity> {
        @Query("SELECT * FROM test_items WHERE id = :id LIMIT 1")
        override suspend fun findById(id: String): TestEntity?

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        override suspend fun insert(entity: TestEntity)

        @Update
        override suspend fun update(entity: TestEntity)

        @Query("DELETE FROM test_items WHERE id = :id")
        override suspend fun deleteById(id: String)
    }

    @Database(entities = [TestEntity::class], version = 1, exportSchema = false)
    abstract class TestDatabase : RoomDatabase() {
        abstract fun testDao(): TestDao
    }

    private lateinit var database: TestDatabase
    private lateinit var store: RoomEntityStore<TestEntity>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TestDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomEntityStore(database.testDao(), database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun findById_returnsNullWhenMissing() = runTest {
        assertNull(store.findById("missing"))
    }

    @Test
    fun upsert_insertsThenUpdates() = runTest {
        val created = TestEntity(id = "t1", label = "A", localVersion = 1, updatedAtMillis = 10)
        store.upsert(created)
        assertEquals("A", store.findById("t1")?.label)

        store.upsert(created.copy(label = "B", localVersion = 2))
        assertEquals("B", store.findById("t1")?.label)
        assertEquals(2L, store.findById("t1")?.localVersion)
    }

    @Test
    fun delete_removesRow() = runTest {
        store.upsert(TestEntity(id = "t1", label = "A"))
        store.delete("t1")
        assertNull(store.findById("t1"))
    }

    @Test
    fun transaction_runsInsideRoomTransaction() = runTest {
        store.transaction {
            store.upsert(TestEntity(id = "t1", label = "inside"))
        }
        assertEquals("inside", store.findById("t1")?.label)
    }

    @Test
    fun asEntityStore_wrapsDao() = runTest {
        val wrapped = database.testDao().asEntityStore(database)
        wrapped.upsert(TestEntity(id = "t2", label = "wrapped"))
        assertEquals("wrapped", wrapped.findById("t2")?.label)
    }
}