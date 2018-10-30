/*
 * CollectionViewModel.kt
 * Implements the CollectionViewModel class
 * A CollectionViewModel stores the podcast collection as live data
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepods.adapter

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.FileHelper
import org.y20k.escapepods.helpers.LogHelper


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {


    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionViewModel::class.java)


    /* Main class variables */
    private lateinit var collectionViewModel: MutableLiveData<Collection>


    /* Initializes collection live data */
    fun getCollection(): LiveData<Collection> {
        if (!::collectionViewModel.isInitialized) {
            collectionViewModel = MutableLiveData()
            loadCollection()
        }
        return collectionViewModel
    }


    /* Reloads collection from storage */
    fun reload() {
        loadCollection()
    }


    /* Reads podcast collection from storage using GSON */
    private fun loadCollection() = runBlocking<Unit> {
        LogHelper.v(TAG, "Loading podcast collection from storage async - setting view model")
        // get JSON from text file async
        val result = async { FileHelper.readCollection(getApplication()) }
        // wait for result and update collection view model
        collectionViewModel.value = result.await()
    }

}