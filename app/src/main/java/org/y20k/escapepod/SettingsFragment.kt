/*
 * SettingsFragment.kt
 * Implements the SettingsFragment fragment
 * A SettingsFragment displays the user accessible settings of the app
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-20 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import org.y20k.escapepod.core.Collection
import org.y20k.escapepod.dialogs.YesNoDialog
import org.y20k.escapepod.helpers.*
import org.y20k.escapepod.xml.OpmlHelper


/*
 * SettingsFragment class
 */
class SettingsFragment: PreferenceFragmentCompat(), YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(SettingsFragment::class.java)


    /* Overrides onViewCreated from PreferenceFragmentCompat */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // set the background color
        view.setBackgroundColor(resources.getColor(R.color.app_window_background, null))
        // show action bar
        (activity as AppCompatActivity).supportActionBar?.show()
        (activity as AppCompatActivity).supportActionBar?.setDisplayHomeAsUpEnabled(true)
        (activity as AppCompatActivity).supportActionBar?.title = getString(R.string.fragment_settings_title)
    }


    /* Overrides onCreatePreferences from PreferenceFragmentCompat */
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)


        // set up "App Theme" preference
        val preferenceThemeSelection: ListPreference = ListPreference(activity as Context)
        preferenceThemeSelection.title = getString(R.string.pref_theme_selection_title)
        preferenceThemeSelection.setIcon(R.drawable.ic_smartphone_24dp)
        preferenceThemeSelection.key = Keys.PREF_THEME_SELECTION
        preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${AppThemeHelper.getCurrentTheme(activity as Context)}"
        preferenceThemeSelection.entries = arrayOf(getString(R.string.pref_theme_selection_mode_device_default), getString(R.string.pref_theme_selection_mode_light), getString(R.string.pref_theme_selection_mode_dark))
        preferenceThemeSelection.entryValues = arrayOf(Keys.STATE_THEME_FOLLOW_SYSTEM, Keys.STATE_THEME_LIGHT_MODE, Keys.STATE_THEME_DARK_MODE)
        preferenceThemeSelection.setDefaultValue(Keys.STATE_THEME_FOLLOW_SYSTEM)
        preferenceThemeSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index: Int = preference.entryValues.indexOf(newValue)
                preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${preference.entries.get(index)}"
                return@setOnPreferenceChangeListener true
            } else {
                return@setOnPreferenceChangeListener false
            }
        }


        // set up "OPML Export" preference
        val preferenceOpmlExport: Preference = Preference(activity as Context)
        preferenceOpmlExport.title = getString(R.string.pref_opml_title)
        preferenceOpmlExport.setIcon(R.drawable.ic_share_24dp)
        preferenceOpmlExport.summary = getString(R.string.pref_opml_summary)
        preferenceOpmlExport.setOnPreferenceClickListener{
            OpmlHelper.shareOpml(activity as Activity)
            return@setOnPreferenceClickListener true
        }


        // set up "Delete All" preference
        val preferenceDeleteAll: Preference = Preference(activity as Context)
        preferenceDeleteAll.title = getString(R.string.pref_delete_all_title)
        preferenceDeleteAll.setIcon(R.drawable.ic_delete_24dp)
        preferenceDeleteAll.summary = getString(R.string.pref_delete_all_summary)
        preferenceDeleteAll.setOnPreferenceClickListener{
            // stop playback using intent (we have no media controller reference here)
            val intent = Intent(activity, PlayerService::class.java)
            intent.action = Keys.ACTION_STOP
            (activity as Context).startService(intent)
            // show dialog
            YesNoDialog(this).show(context = activity as Context, type = Keys.DIALOG_DELETE_DOWNLOADS, message = R.string.dialog_yes_no_message_delete_downloads, yesButton = R.string.dialog_yes_no_positive_button_delete_downloads)
            return@setOnPreferenceClickListener true
        }


        // set preference categories
        val preferenceCategoryGeneral: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryGeneral.title = getString(R.string.pref_general_title)
        preferenceCategoryGeneral.contains(preferenceThemeSelection)

        val preferenceCategoryMaintenance: PreferenceCategory = PreferenceCategory(activity as Context)
        preferenceCategoryMaintenance.title = getString(R.string.pref_maintenance_title)
        preferenceCategoryMaintenance.contains(preferenceOpmlExport)
        preferenceCategoryMaintenance.contains(preferenceDeleteAll)


        // setup preference screen
        screen.addPreference(preferenceCategoryGeneral)
        screen.addPreference(preferenceThemeSelection)
        screen.addPreference(preferenceCategoryMaintenance)
        screen.addPreference(preferenceOpmlExport)
        screen.addPreference(preferenceDeleteAll)
        preferenceScreen = screen
    }



    /* Overrides onYesNoDialog from YesNoDialogListener */
    override fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String) {
        super.onYesNoDialog(type, dialogResult, payload, payloadString)

        when (type) {
            Keys.DIALOG_DELETE_DOWNLOADS -> {
                when (dialogResult) {
                    // user tapped: delete all downloads
                    true -> {
                        deleteAllEpisodes(activity as Context, FileHelper.readCollection(activity as Context))
                    }
                }
            }
        }

    }


    /* Deletes all episode audio files - deep clean */
    private fun deleteAllEpisodes(context: Context, collection: Collection) {
        val newCollection = collection.deepCopy()
        // delete all episodes
        CollectionHelper.deleteAllAudioFile(context, newCollection)
        // update player state if necessary
        PreferencesHelper.updatePlayerState(context, newCollection)
        // save collection and broadcast changes
        CollectionHelper.saveCollection(context, newCollection)
    }

}