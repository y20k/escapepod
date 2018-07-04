package org.y20k.escapepods.adapter

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.os.AsyncTask
import org.y20k.escapepods.core.Collection
import org.y20k.escapepods.helpers.FileHelper
import org.y20k.escapepods.helpers.Keys
import org.y20k.escapepods.helpers.LogHelper


/*
 * CollectionViewModel.class
 */
class CollectionViewModel(application: Application) : AndroidViewModel(application) {

    /* Main class variables */
    val collectionLiveData: MutableLiveData<Collection> = MutableLiveData<Collection>()
    private val context = application


    fun loadCollection() {
        CollectionReader().execute()
//        collectionLiveData.setValue(FileHelper().readCollection(context)) // todo replace
    }

    /*
     * Inner class: Reads collection from storage - AsyncTask
     */
    inner class CollectionReader(): AsyncTask<Void, Void, Collection>() {
        private val TAG: String = LogHelper.makeLogTag(CollectionReader::class.java)

        override fun doInBackground(vararg params: Void?): Collection {
            LogHelper.v(TAG, "Loading ${Keys.COLLECTION_FILE} from storage.")
            return FileHelper().readCollection(context)
        }

        override fun onPostExecute(result: Collection) {
            collectionLiveData.setValue(result)
        }
    }
    /*
     * End of inner class
     */



}