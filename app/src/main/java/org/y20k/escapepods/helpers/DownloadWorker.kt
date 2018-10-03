package org.y20k.escapepods.helpers

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.y20k.escapepods.DownloadService

class DownloadWorker(context : Context, params : WorkerParameters): Worker(context, params) {

    override fun doWork(): Result {

        // Do the work here--in this case, compress the stored images.
        // In this example no parameters are passed; the task is
        // assumed to be "compress the whole library."
        // myCompress()

        // Indicate success or failure with your return value:
        return Result.SUCCESS

        // (Returning RETRY tells WorkManager to try this task again
        // later; FAILURE says not to try again.)

    }


    fun updateCollection() {
        val intent: Intent = Intent(applicationContext, DownloadService::class.java)
        intent.setAction(Keys.ACTION_UPDATE_COLLECTION)
        applicationContext.startService(intent)
    }

}
