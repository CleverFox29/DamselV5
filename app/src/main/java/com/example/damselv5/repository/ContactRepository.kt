package com.example.damselv5.repository

import com.example.damselv5.data.ContactDao
import com.example.damselv5.data.ContactEntity
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {

    val allContacts: Flow<List<ContactEntity>> = contactDao.getAllContacts()

    suspend fun insert(contact: ContactEntity): Long {
        return contactDao.insertContact(contact)
    }

    suspend fun delete(contact: ContactEntity) {
        contactDao.deleteContact(contact)
    }
}