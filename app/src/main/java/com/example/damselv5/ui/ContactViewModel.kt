package com.example.damselv5.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.damselv5.data.AppDatabase
import com.example.damselv5.data.ContactEntity
import com.example.damselv5.repository.ContactRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ContactRepository
    val allContacts: LiveData<List<ContactEntity>>

    init {
        val contactDao = AppDatabase.getDatabase(application).contactDao()
        repository = ContactRepository(contactDao)
        allContacts = repository.allContacts.asLiveData()
    }

    /**
     * Inserts a contact and returns the row ID.
     * Returns -1 if the contact already exists (conflict).
     */
    suspend fun insert(name: String, phoneNumber: String): Long {
        return repository.insert(ContactEntity(name = name, phoneNumber = phoneNumber))
    }

    fun delete(contact: ContactEntity) = viewModelScope.launch {
        repository.delete(contact)
    }
}