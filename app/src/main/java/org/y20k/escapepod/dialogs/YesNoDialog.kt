/*
 * YesNoDialog
 * Implements the YesNoDialog class
 * A YesNoDialog asks the user if he/she wants to do something or not
 *
 * This file is part of
 * ESCAPEPODS - Free and Open Podcast App
 *
 * Copyright (c) 2018-19 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod.dialogs

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.y20k.escapepod.Keys
import org.y20k.escapepod.R
import org.y20k.escapepod.helpers.LogHelper


/*
 * YesNoDialog class
 */
class YesNoDialog (private var yesNoDialogListener: YesNoDialogListener) {

    /* Interface used to communicate back to activity */
    interface YesNoDialogListener {
        fun onYesNoDialog(dialogType: Int, dialogResult: Boolean, dialogPayloadInt: Int, dialogPayloadString: String) {
        }
    }


    /* Define log tag */
    private val TAG = LogHelper.makeLogTag(YesNoDialog::class.java.simpleName)


    /* Construct and show dialog */
    fun show(context: Context, dialogType: Int, yesNoTitleRessouce: Int, yesNoMessageRessouce: Int, yesButtonRessouce: Int, noButtonRessouce: Int = R.string.dialog_generic_button_cancel, dialogPayloadInt: Int = Keys.DIALOG_EMPTY_PAYLOAD_INT, dialogPayloadString: String = Keys.DIALOG_EMPTY_PAYLOAD_STRING) {
        // variant of "show" that only accepts ressource ints
        show(context, dialogType, yesNoTitleRessouce, context.getString(yesNoMessageRessouce), yesButtonRessouce, noButtonRessouce, dialogPayloadInt, dialogPayloadString)
    }


//    /* Construct and show dialog */
//    fun show(context: Context, dialogType: Int, dialogPayloadInt: Int, yesNoTitleRessouce: Int, yesNoMessageString: String, yesButtonRessouce: Int, noButtonRessouce: Int = R.string.dialog_generic_button_cancel) {
//        // variant of "show" that only accepts a int payload
//        show(context, dialogType, yesNoTitleRessouce, yesNoMessageString, yesButtonRessouce, noButtonRessouce, dialogPayloadInt, Keys.DIALOG_EMPTY_PAYLOAD_STRING)
//    }
//
//
    /* Construct and show dialog */
    fun show(context: Context, dialogType: Int, yesNoTitleRessouce: Int, yesNoMessageString: String, yesButtonRessouce: Int, noButtonRessouce: Int = R.string.dialog_generic_button_cancel, dialogPayloadString: String) {
        // variant of "show" that only accepts a string payload
        show(context, dialogType, yesNoTitleRessouce, yesNoMessageString, yesButtonRessouce, noButtonRessouce, Keys.DIALOG_EMPTY_PAYLOAD_INT, dialogPayloadString)
    }


    /* Construct and show dialog */
    fun show(context: Context, dialogType: Int, yesNoTitleRessouce: Int, yesNoMessageString: String, yesButtonRessouce: Int, noButtonRessouce: Int = R.string.dialog_generic_button_cancel, dialogPayloadInt: Int = Keys.DIALOG_EMPTY_PAYLOAD_INT, dialogPayloadString: String = Keys.DIALOG_EMPTY_PAYLOAD_STRING) {
        // prepare dialog builder
        val builder: MaterialAlertDialogBuilder = MaterialAlertDialogBuilder(context)

        // set title and message
        builder.setTitle(context.getString(yesNoTitleRessouce))
        builder.setMessage(yesNoMessageString)

        // add yes button
        builder.setPositiveButton(yesButtonRessouce) { _, _ ->
            // listen for click on yes button
            yesNoDialogListener.onYesNoDialog(dialogType, true, dialogPayloadInt, dialogPayloadString)
        }

        // add no button
        builder.setNegativeButton(noButtonRessouce) { _, _ ->
            // listen for click on no button
            yesNoDialogListener.onYesNoDialog(dialogType, false, dialogPayloadInt, dialogPayloadString)
        }

        // handle outside-click as "no"
        builder.setOnCancelListener(){
            yesNoDialogListener.onYesNoDialog(dialogType, false, dialogPayloadInt, dialogPayloadString)
        }

        // display dialog
        builder.show()
    }
}