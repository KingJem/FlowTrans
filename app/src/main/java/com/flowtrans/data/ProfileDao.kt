package com.flowtrans.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY lastUsedAt DESC, createdAt DESC")
    fun observeAll(): Flow<List<ProxyProfile>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: Long): ProxyProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProxyProfile): Long

    @Update
    suspend fun update(profile: ProxyProfile)

    @Delete
    suspend fun delete(profile: ProxyProfile)

    @Query("UPDATE profiles SET lastUsedAt = :ts WHERE id = :id")
    suspend fun touch(id: Long, ts: Long)
}
