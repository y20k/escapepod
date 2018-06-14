package org.y20k.escapepods.helpers

import android.app.Activity
import android.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import org.y20k.escapepods.R


/**
 * DialogAdd class
 */
object DialogAdd {

    /* Define log tag */
    private val LOG_TAG = DialogAdd::class.java.simpleName


    /* Construct and show dialog */
    @JvmStatic
    fun show(activity: Activity) {
        // prepare dialog builder
        val inflater = LayoutInflater.from(activity)
        val builder = AlertDialog.Builder(activity)

        // get input field
        val view = inflater.inflate(R.layout.dialog_add_podcast, null)

        val inputField = view.findViewById<View>(R.id.dialog_add_podcast_input) as EditText

        // set dialog view
        builder.setView(view)

        // add "add" button
        builder.setPositiveButton(R.string.dialog_add_podcast_button) { _, id ->
            if (inputField.text != null) {
                val input = inputField.text.toString()
                // download new podcast
                //                    StationFetcher stationFetcher = new StationFetcher(activity, folder, Uri.parse(input.trim()), null);
                //                    stationFetcher.execute();
            }
        }

        // add cancel button
        builder.setNegativeButton(R.string.dialog_generic_button_cancel) { _, _ ->
            // listen for click on cancel button
            // do nothing
        }

        // display add dialog
        builder.show()
    }

}