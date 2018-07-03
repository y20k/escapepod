package org.y20k.escapepods.adapter

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.FileHelper


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Main class variables */
    val collectionLiveData: MutableLiveData<Collection> = MutableLiveData<Collection>()
    private val context = application


    fun loadCollection() {
        collectionLiveData.setValue(FileHelper().readCollection(context)) // todo replace
        // collectionLiveData.value(FileHelper().readCollection(context))
    }

//    fun getCollectionLiveData

}