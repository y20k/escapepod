/*
 * SettingsFragment.kt
 * Implements the SettingsFragment fragment
 * A SettingsFragment displays the user accessible settings of the app
 *
 * This file is part of
 * ESCAPEPOD - Free and Open Podcast App
 *
 * Copyright (c) 2018-21 - Y20K.org
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */


package org.y20k.escapepod

import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.preference.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import org.y20k.escapepod.database.CollectionDatabase
import org.y20k.escapepod.dialogs.YesNoDialog
import org.y20k.escapepod.helpers.*
import org.y20k.escapepod.playback.PlayerService
import org.y20k.escapepod.xml.OpmlHelper


/*
 * SettingsFragment class
 */
class SettingsFragment: PreferenceFragmentCompat(), YesNoDialog.YesNoDialogListener {

    /* Define log tag */
    private val TAG: String = LogHelper.makeLogTag(SettingsFragment::class.java)


    /* Main class variables */
    private lateinit var preferenceThemeSelection: ListPreference
    private lateinit var preferenceBackgroundRefresh: SwitchPreferenceCompat
    private lateinit var preferenceEpisodeDownloadOverMobile: SwitchPreferenceCompat
    private lateinit var preferenceOpmlExport: Preference
    private lateinit var preferenceOpmlImport: Preference
    private lateinit var preferenceUpdateCovers: Preference
    private lateinit var preferenceDeleteAll: Preference
    private lateinit var preferenceSearchProviderSelection: ListPreference
    private lateinit var preferenceAppVersion: Preference
    private lateinit var preferenceReportIssue: Preference


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

        val screen = preferenceManager.createPreferenceScreen(preferenceManager.context)

        // set up "App Theme" preference
        preferenceThemeSelection = ListPreference(activity as Context)
        preferenceThemeSelection.title = getString(R.string.pref_theme_selection_title)
        preferenceThemeSelection.setIcon(R.drawable.ic_smartphone_24dp)
        preferenceThemeSelection.key = Keys.PREF_THEME_SELECTION
        preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${PreferencesHelper.getCurrentTheme(preferenceManager.context)}"
        preferenceThemeSelection.entries = arrayOf(getString(R.string.pref_theme_selection_mode_device_default), getString(R.string.pref_theme_selection_mode_light), getString(R.string.pref_theme_selection_mode_dark))
        preferenceThemeSelection.entryValues = arrayOf(Keys.STATE_THEME_FOLLOW_SYSTEM, Keys.STATE_THEME_LIGHT_MODE, Keys.STATE_THEME_DARK_MODE)
        preferenceThemeSelection.setDefaultValue(Keys.STATE_THEME_FOLLOW_SYSTEM)
        preferenceThemeSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index: Int = preference.entryValues.indexOf(newValue)
                preferenceThemeSelection.summary = "${getString(R.string.pref_theme_selection_summary)} ${preference.entries[index]}"
                return@setOnPreferenceChangeListener true
            } else {
                return@setOnPreferenceChangeListener false
            }
        }

        // set up "Background Refresh" preference
        preferenceBackgroundRefresh = SwitchPreferenceCompat(activity as Context)
        preferenceBackgroundRefresh.title = getString(R.string.pref_background_refresh_title)
        preferenceBackgroundRefresh.setIcon(R.drawable.ic_autorenew_24dp)
        preferenceBackgroundRefresh.key = Keys.PREF_BACKGROUND_REFRESH
        preferenceBackgroundRefresh.summaryOn = getString(R.string.pref_background_refresh_summary_enabled)
        preferenceBackgroundRefresh.summaryOff = getString(R.string.pref_background_refresh_summary_disabled)
        preferenceBackgroundRefresh.setDefaultValue(Keys.DEFAULT_BACKGROUND_REFRESH_MODE)

        // set up "episode Download over mobile" preference
        preferenceEpisodeDownloadOverMobile = SwitchPreferenceCompat(activity as Context)
        preferenceEpisodeDownloadOverMobile.title = getString(R.string.pref_episode_download_over_mobile_title)
        preferenceEpisodeDownloadOverMobile.setIcon(R.drawable.ic_signal_cellular_24dp)
        preferenceEpisodeDownloadOverMobile.key = Keys.PREF_EPISODE_DOWNLOAD_OVER_MOBILE
        preferenceEpisodeDownloadOverMobile.summaryOn = getString(R.string.pref_episode_download_over_mobile_summary_enabled)
        preferenceEpisodeDownloadOverMobile.summaryOff = getString(R.string.pref_episode_download_over_mobile_summary_disabled)
        preferenceEpisodeDownloadOverMobile.setDefaultValue(Keys.DEFAULT_EPISODE_DOWNLOAD_OVER_MOBILE_MODE)

        // set up "OPML Export" preference
        preferenceOpmlExport = Preference(activity as Context)
        preferenceOpmlExport.title = getString(R.string.pref_opml_export_title)
        preferenceOpmlExport.setIcon(R.drawable.ic_save_24dp)
        preferenceOpmlExport.summary = getString(R.string.pref_opml_export_summary)
        preferenceOpmlExport.setOnPreferenceClickListener {
            openSaveOpmlDialog()
            return@setOnPreferenceClickListener true
        }


        // set up "OPML Import" preference
        preferenceOpmlImport = Preference(activity as Context)
        preferenceOpmlImport.title = getString(R.string.pref_opml_import_title)
        preferenceOpmlImport.setIcon(R.drawable.ic_folder_24)
        preferenceOpmlImport.summary = getString(R.string.pref_opml_import_summary)
        preferenceOpmlImport.setOnPreferenceClickListener {
            openImportOpmlDialog()
            return@setOnPreferenceClickListener true
        }



        // set up "Update Covers" preference
        preferenceUpdateCovers = Preference(activity as Context)
        preferenceUpdateCovers.title = getString(R.string.pref_update_covers_title)
        preferenceUpdateCovers.setIcon(R.drawable.ic_image_24dp)
        preferenceUpdateCovers.summary = getString(R.string.pref_update_covers_summary)
        preferenceUpdateCovers.setOnPreferenceClickListener {
            // show dialog
            YesNoDialog(this).show(context = preferenceManager.context, type = Keys.DIALOG_UPDATE_COVERS, message = R.string.dialog_yes_no_message_update_covers, yesButton = R.string.dialog_yes_no_positive_button_update_covers)
            return@setOnPreferenceClickListener true
        }

        // set up "Delete All" preference
        preferenceDeleteAll = Preference(activity as Context)
        preferenceDeleteAll.title = getString(R.string.pref_delete_all_title)
        preferenceDeleteAll.setIcon(R.drawable.ic_delete_24dp)
        preferenceDeleteAll.summary = "${getString(R.string.pref_delete_all_summary)} ${getAvailableSpace()}"
        preferenceDeleteAll.setOnPreferenceClickListener {
            // stop playback using intent (we have no media controller reference here)
            val intent = Intent(activity, PlayerService::class.java)
            intent.action = Keys.ACTION_STOP
            (preferenceManager.context).startService(intent)
            // show dialog
            YesNoDialog(this).show(context = preferenceManager.context, type = Keys.DIALOG_DELETE_DOWNLOADS, message = R.string.dialog_yes_no_message_delete_downloads, yesButton = R.string.dialog_yes_no_positive_button_delete_downloads)
            return@setOnPreferenceClickListener true
        }

        // set up "Search Provider" preference
        preferenceSearchProviderSelection = ListPreference(context)
        preferenceSearchProviderSelection.title = getString(R.string.pref_search_provider_selection_title)
        preferenceSearchProviderSelection.setIcon(R.drawable.ic_search_24dp)
        preferenceSearchProviderSelection.key = Keys.PREF_PODCAST_SEARCH_PROVIDER_SELECTION
        preferenceSearchProviderSelection.summary = "${getString(R.string.pref_search_provider_selection_summary)} ${PreferencesHelper.getCurrentPodcastSearchProvider(preferenceManager.context)}"
        preferenceSearchProviderSelection.entries = arrayOf(getString(R.string.pref_search_provider_selection_gpodder), getString(R.string.pref_search_provider_selection_podcastindex))
        preferenceSearchProviderSelection.entryValues = arrayOf(Keys.PODCAST_SEARCH_PROVIDER_GPODDER, Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX)
        preferenceSearchProviderSelection.setDefaultValue(Keys.PODCAST_SEARCH_PROVIDER_PODCASTINDEX)
        preferenceSearchProviderSelection.setOnPreferenceChangeListener { preference, newValue ->
            if (preference is ListPreference) {
                val index: Int = preference.entryValues.indexOf(newValue)
                preferenceSearchProviderSelection.summary = "${getString(R.string.pref_search_provider_selection_summary)} ${preference.entries[index]}"
                return@setOnPreferenceChangeListener true
            } else {
                return@setOnPreferenceChangeListener false
            }
        }

        // set up "App Version" preference
        preferenceAppVersion = Preference(activity as Context)
        preferenceAppVersion.title = getString(R.string.pref_app_version_title)
        preferenceAppVersion.setIcon(R.drawable.ic_info_24dp)
        preferenceAppVersion.summary = "${getString(R.string.pref_app_version_summary)} ${BuildConfig.VERSION_NAME} (${getString(R.string.app_version_name)})"
        preferenceAppVersion.setOnPreferenceClickListener {
            // copy to clipboard
            val clip: ClipData = ClipData.newPlainText("simple text", preferenceAppVersion.summary)
            val cm: ClipboardManager = preferenceManager.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            Toast.makeText(activity as Context, R.string.toast_message_copied_to_clipboard, Toast.LENGTH_LONG).show()
            return@setOnPreferenceClickListener true
        }

        // set up "Report Issue" preference
        preferenceReportIssue = Preference(activity as Context)
        preferenceReportIssue.title = getString(R.string.pref_report_issue_title)
        preferenceReportIssue.setIcon(R.drawable.ic_bug_report_24dp)
        preferenceReportIssue.summary = getString(R.string.pref_report_issue_summary)
        preferenceReportIssue.setOnPreferenceClickListener {
            // open web browser
            val intent = Intent().apply {
                action = Intent.ACTION_VIEW
                data = "https://github.com/y20k/escapepod/issues".toUri()
            }
            startActivity(intent)
            return@setOnPreferenceClickListener true
        }


        // set preference categories
        val preferenceCategoryGeneral: PreferenceCategory = PreferenceCategory(context)
        preferenceCategoryGeneral.title = getString(R.string.pref_general_title)
        preferenceCategoryGeneral.contains(preferenceThemeSelection)
        preferenceCategoryGeneral.contains(preferenceBackgroundRefresh)
        preferenceCategoryGeneral.contains(preferenceEpisodeDownloadOverMobile)

        val preferenceCategoryMaintenance: PreferenceCategory = PreferenceCategory(context)
        preferenceCategoryMaintenance.title = getString(R.string.pref_maintenance_title)
        preferenceCategoryMaintenance.contains(preferenceOpmlExport)
        preferenceCategoryMaintenance.contains(preferenceOpmlImport)
        preferenceCategoryMaintenance.contains(preferenceUpdateCovers)
        preferenceCategoryMaintenance.contains(preferenceDeleteAll)

        val preferenceCategoryAdvanced: PreferenceCategory = PreferenceCategory(context)
        preferenceCategoryAdvanced.title = getString(R.string.pref_advanced_title)
        preferenceCategoryAdvanced.contains(preferenceSearchProviderSelection)

        val preferenceCategoryAbout: PreferenceCategory = PreferenceCategory(context)
        preferenceCategoryAbout.title = getString(R.string.pref_about_title)
        preferenceCategoryAbout.contains(preferenceAppVersion)
        preferenceCategoryAbout.contains(preferenceReportIssue)


        // setup preference screen
        screen.addPreference(preferenceCategoryGeneral)
        screen.addPreference(preferenceThemeSelection)
        screen.addPreference(preferenceBackgroundRefresh)
        screen.addPreference(preferenceEpisodeDownloadOverMobile)
        screen.addPreference(preferenceCategoryMaintenance)
        screen.addPreference(preferenceOpmlExport)
        screen.addPreference(preferenceOpmlImport)
        screen.addPreference(preferenceUpdateCovers)
        screen.addPreference(preferenceDeleteAll)
        screen.addPreference(preferenceCategoryAdvanced)
        screen.addPreference(preferenceSearchProviderSelection)
        screen.addPreference(preferenceCategoryAbout)
        screen.addPreference(preferenceAppVersion)
        screen.addPreference(preferenceReportIssue)
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
                        deleteAllEpisodes()
                    }
                }
            }

            Keys.DIALOG_UPDATE_COVERS -> {
                when (dialogResult) {
                    // user tapped: refresh podcast covers
                    true -> {
                        DownloadHelper.updateCovers(activity as Context)
                    }
                }
            }

        }

    }

    /* Register the ActivityResultLauncher for saving OPML */
    private val requestSaveOpmlLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestSaveOpmlResult)

    /* save OPML file to result file location */
    private fun requestSaveOpmlResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK && result.data != null) {
            val sourceUri: Uri = OpmlHelper.getOpmlUri(activity as Activity)
            val targetUri: Uri? = result.data?.data
            if (targetUri != null) {
                // copy file async (= fire & forget - no return value needed)
                CoroutineScope(IO).launch { FileHelper.saveCopyOfFileSuspended(activity as Context, sourceUri, targetUri) }
                Toast.makeText(activity as Context, R.string.toast_message_save_opml, Toast.LENGTH_LONG).show()
            }
        }
    }

    /* Register the ActivityResultLauncher for opening OPML */
    private val requestOpenOpmlLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestOpenOpmlResult)

    /* open OPML file selected in file picker and import */
    private fun requestOpenOpmlResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK && result.data != null) {
            val targetUri: Uri? = result.data?.data
            if (targetUri != null) {
                // open and import OPML in player fragment
                val bundle: Bundle = bundleOf(
                    Keys.ARG_OPEN_OPML to "$targetUri"
                )
                this.findNavController().navigate(R.id.podcast_player_destination, bundle)
            }
        }
    }


    /* Deletes all episode audio files - deep clean */
    private fun deleteAllEpisodes() {
        // delete audio files for all episode
        CollectionHelper.deleteAllAudioFiles(activity as Context)
        // reset all local audio references in database
        CoroutineScope(IO).launch {
            val collectionDatabase = CollectionDatabase.getInstance(activity as Context)
            collectionDatabase.episodeDao().resetLocalAudioReferencesForAllEpisodes()
        }
        // reset current and up next media id
        PreferencesHelper.saveCurrentMediaId()
        PreferencesHelper.saveUpNextMediaId()
        PreferencesHelper.savePlayerPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        // update summary
        preferenceDeleteAll.summary = "${getString(R.string.pref_delete_all_summary)} ${getAvailableSpace()}"
        Toast.makeText(activity as Context, R.string.toast_message_deleting_downloads, Toast.LENGTH_LONG).show()
    }


    /* Opens up a file picker to select the save location */
    private fun openSaveOpmlDialog() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Keys.MIME_TYPE_XML
            putExtra(Intent.EXTRA_TITLE, Keys.COLLECTION_OPML_FILE)
        }
        // file gets saved in the ActivityResult
        try {
            requestSaveOpmlLauncher.launch(intent)
        } catch (exception: Exception) {
            LogHelper.e(TAG, "Unable to save OPML.\n$exception")
            Toast.makeText(activity as Context, R.string.toast_message_install_file_helper, Toast.LENGTH_LONG).show()
        }
    }


    /* Opens up a file picker to select an OPML file for import */
    private fun openImportOpmlDialog() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = Keys.MIME_TYPE_XML
            putExtra(Intent.EXTRA_TITLE, Keys.COLLECTION_OPML_FILE)
        }
        // file gets saved in the ActivityResult
        try {
            requestOpenOpmlLauncher.launch(intent)
        } catch (exception: Exception) {
            LogHelper.e(TAG, "Unable to open file picker for OPML.\n$exception")
            // Toast.makeText(activity as Context, R.string.toast_message_install_file_helper, Toast.LENGTH_LONG).show()
        }
    }


    /* Creates readable string containing available and used storage space */
    private fun getAvailableSpace(): String {
        val externalFilesDir = preferenceManager.context.getExternalFilesDir("")
        val audioFolder = preferenceManager.context.getExternalFilesDir(Keys.FOLDER_AUDIO)
        return "${getString(R.string.pref_delete_all_storage_space_available)}: ${FileHelper.getAvailableFreeSpaceForFolder(externalFilesDir)} | ${getString(R.string.pref_delete_all_storage_space_used)}: ${FileHelper.getFolderSize(audioFolder)}" }

}
