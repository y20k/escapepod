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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.FileHelper
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(CollectionViewModel::class.java)


    /* Main class variables */
    val collectionLiveData: MutableLiveData<Collection>
    private var collectionChangedReceiver: BroadcastReceiver
    private val backgroundJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + backgroundJob)


    /* Init constructor */
    init {
        // create empty view model
        collectionLiveData = MutableLiveData<Collection>()
        // load collection
        loadCollection()
        // create and register collection changed receiver
        collectionChangedReceiver = createCollectionChangedReceiver()
        LocalBroadcastManager.getInstance(application).registerReceiver(collectionChangedReceiver, IntentFilter(Keys.ACTION_COLLECTION_CHANGED))
    }


    /* Overrides onCleared */
    override fun onCleared() {
        super.onCleared()
        backgroundJob.cancel()
        LocalBroadcastManager.getInstance(getApplication()).unregisterReceiver(collectionChangedReceiver)
    }


    /* Creates the collectionChangedReceiver - handles Keys.ACTION_COLLECTION_CHANGED */
    private fun createCollectionChangedReceiver(): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // load collection and update collectionLiveData
                loadCollection()
            }
        }
    }


    /* Reads podcast collection from storage using GSON */
    private fun loadCollection() {
        LogHelper.v(TAG, "Loading podcast collection from storage")
        uiScope.launch {
            // load collection on background thread
            val deferred: Deferred<Collection> = async(Dispatchers.Default) { FileHelper.readCollectionSuspended(getApplication()) }
            // wait for result and update collection view model
            collectionLiveData.value = deferred.await()
        }
    }

}