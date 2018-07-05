package org.y20k.escapepods.adapter

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.FileHelper



/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Main class variables */
    val collectionLiveData: MutableLiveData<Collection> = MutableLiveData<Collection>()
    val applicationContext = application


    /* Reads collection from storage using GSON - async via coroutine */
    fun loadCollectionAsync() {
        var json: String = ""
        launch(UI) {
            val result = async(CommonPool) {
                // get JSON from text file
                FileHelper().readCollection(applicationContext)
            }.await()
            collectionLiveData.setValue(result)
        }
    }

}