package com.example.damselv5.repository

import com.example.damselv5.data.ContactDao
import com.example.damselv5.data.ContactEntity
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val d: ContactDao) {

    val allContacts: Flow<List<ContactEntity>> = d.gAC()

    suspend fun insert(c: ContactEntity): Long {
        
        return d.iC(c)
    }

    suspend fun delete(c: ContactEntity) {
        
        d.dC(c)
    }
}