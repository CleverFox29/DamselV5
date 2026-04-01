package com.example.damselv5.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts")
    fun gAC(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts")
    suspend fun gACS(): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun iC(c: ContactEntity): Long

    @Delete
    suspend fun dC(c: ContactEntity)
}