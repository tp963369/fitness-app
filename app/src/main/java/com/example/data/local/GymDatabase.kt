package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "kv_snapshots")
data class KvSnapshotEntity(
    @PrimaryKey val key: String,
    val jsonPayload: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs_table",
    indices = [Index(value = ["entityType", "entityId"])]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val actorId: String,
    val actorRole: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val reason: String,
    val beforeJson: String,
    val afterJson: String,
    val createdAtEpochMs: Long
)

@Dao
interface GymDao {
    @Query("SELECT * FROM kv_snapshots")
    fun observeSnapshots(): Flow<List<KvSnapshotEntity>>

    @Query("SELECT * FROM kv_snapshots")
    suspend fun getAllSnapshots(): List<KvSnapshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnapshot(entity: KvSnapshotEntity)

    @Query("SELECT * FROM audit_logs_table ORDER BY createdAtEpochMs DESC")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs_table ORDER BY createdAtEpochMs DESC")
    suspend fun getAllAuditLogs(): List<AuditLogEntity>

    // Append-only: only INSERT and SELECT are defined for audit_logs_table
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(entity: AuditLogEntity)
}

@Database(
    entities = [KvSnapshotEntity::class, AuditLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class GymDatabase : RoomDatabase() {
    abstract fun gymDao(): GymDao
}
