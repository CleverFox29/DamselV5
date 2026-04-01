package com.example.damselv5.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.damselv5.data.AppDatabase
import com.example.damselv5.data.ContactDao
import com.example.damselv5.data.ContactEntity
import com.example.damselv5.repository.ContactRepository
import kotlinx.coroutines.launch


class ContactViewModel(a: Application) : AndroidViewModel(a) {

    private val r: ContactRepository
    val all: LiveData<List<ContactEntity>>


    init {
        val db: AppDatabase = AppDatabase.getDatabase(a)
        val d: ContactDao = db.contactDao()
        r = ContactRepository(d)
        all = r.allContacts.asLiveData()

    }


    suspend fun insert(n: String, p: String): Long {
        val c: ContactEntity = ContactEntity(0, n, p)
        val result: Long = r.insert(c)

        return result

    }


    fun delete(c: ContactEntity) {

        viewModelScope.launch {
            r.delete(c)

        }

    }
}