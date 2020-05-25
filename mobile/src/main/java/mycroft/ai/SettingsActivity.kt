/*
 *  Copyright (c) 2017. Mycroft AI, Inc.
 *
 *  This file is part of Mycroft-Android a client for Mycroft Core.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package mycroft.ai

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.preference.RingtonePreference
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.preference.*
import mycroft.ai.Constants.MycroftMobileConstants.VERSION_CODE_PREFERENCE_KEY
import mycroft.ai.Constants.MycroftMobileConstants.VERSION_NAME_PREFERENCE_KEY


/**
 * A Activity that presents a set of application settings.
 */

class SettingsActivity : AppCompatActivity(), PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val backstackTag = "root_fragment"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportFragmentManager.popBackStack(backstackTag, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        if (supportFragmentManager.findFragmentById(android.R.id.content) == null) {
            supportFragmentManager.beginTransaction()
                    .add(android.R.id.content, ListPreferenceFragment())
                    .addToBackStack(backstackTag)
                    .commit()
        }
    }

    override fun onPreferenceStartFragment(caller: PreferenceFragmentCompat, pref: Preference): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                pref.fragment)
        fragment.arguments = args
        fragment.setTargetFragment(caller, 0)

        supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        return true
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */

    class ListPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.pref_headers, rootKey)
            setHasOptionsMenu(true)
        }
    }

    class GeneralPreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_general)
            setHasOptionsMenu(true)

            bindPreferenceSummaryToValue(findPreference("ip"), 2)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
                val overlayPreference: Preference? = findPreference("overlaySwitch")
                overlayPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                    if (newValue.toString().toBoolean()) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${requireContext().packageName}"))
                        startActivityForResult(intent, DRAW_OVERLAYS_PERMISSION_REQUEST_CODE)
                    }
                    true
                }
            }
        }


        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            val overlayPreference: SwitchPreference? = findPreference("overlaySwitch")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
                overlayPreference?.isChecked = false
            }
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == android.R.id.home) {
                activity?.supportFragmentManager?.popBackStack()
                return true
            }
            return super.onOptionsItemSelected(item)
        }
    }

    class NotificationPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_notification)
            setHasOptionsMenu(true)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == android.R.id.home) {
                activity?.supportFragmentManager?.popBackStack()
                return true
            }

            return super.onOptionsItemSelected(item)
        }
/*
        override fun onPreferenceTreeClick(preference: Preference): Boolean {
            return if (preference.key == KEY_RINGTONE_PREFERENCE) {
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                val existingValue: String = getRingtonePreferenceValue() // TODO
                if (existingValue != null) {
                    if (existingValue.length == 0) {
                        // Select "Silent"
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, null as Uri?)
                    } else {
                        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue))
                    }
                } else {
                    // No ringtone has been selected, set to the default
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
                }
                startActivityForResult(intent, REQUEST_CODE_ALERT_RINGTONE)
                true
            } else {
                super.onPreferenceTreeClick(preference)
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_CODE_ALERT_RINGTONE && data != null) {
                val ringtone = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                if (ringtone != null) {
                    setRingtonPreferenceValue(ringtone.toString()) // TODO
                } else {
                    // "Silent" was selected
                    setRingtonPreferenceValue("") // TODO
                }
            } else {
                super.onActivityResult(requestCode, resultCode, data)
            }
        }*/
    }

    class DataSyncPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_data_sync)
            setHasOptionsMenu(true)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == android.R.id.home) {
                activity?.supportFragmentManager?.popBackStack()
                return true
            }

            return super.onOptionsItemSelected(item)
        }
    }

    class AboutPreferenceFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.pref_about)
            setHasOptionsMenu(true)

            bindPreferenceSummaryToValue(findPreference(VERSION_NAME_PREFERENCE_KEY), 2)
            bindPreferenceSummaryToValue(findPreference(VERSION_CODE_PREFERENCE_KEY), 1)

            val licensePreference: Preference? = findPreference("license")

            licensePreference?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.gnu.org/licenses/gpl-3.0.en.html"))
                startActivity(intent)

                true
            }

            bindPreferenceSummaryToValue(licensePreference)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == android.R.id.home) {
                activity?.supportFragmentManager?.popBackStack()
                return true
            }

            return super.onOptionsItemSelected(item)
        }
    }

    companion object {

        private const val DRAW_OVERLAYS_PERMISSION_REQUEST_CODE = 666

        /**
         * A preference value change listener that updates the preference's summary
         * to reflect its new value.
         */
        private val sBindPreferenceSummaryToValueListener = Preference.OnPreferenceChangeListener { preference, value ->
            val stringValue = value.toString()

            if (preference is ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                val index = preference.findIndexOfValue(stringValue)

                // Set the summary to reflect the new value.
                preference.setSummary(
                        if (index >= 0)
                            preference.entries[index]
                        else
                            null)

            } else if (preference is RingtonePreference) {
                // For ringtone preferences, look up the correct display value
                // using RingtoneManager.
                if (TextUtils.isEmpty(stringValue)) {
                    // Empty values correspond to 'silent' (no ringtone).
                    preference.setSummary(R.string.pref_ringtone_silent)

                } else {
                    val ringtone = RingtoneManager.getRingtone(
                            preference.getContext(), Uri.parse(stringValue))

                    if (ringtone == null) {
                        // Clear the summary if there was a lookup error.
                        preference.setSummary(null)
                    } else {
                        // Set the summary to reflect the new ringtone display
                        // name.
                        val name = ringtone.getTitle(preference.getContext())
                        preference.setSummary(name)
                    }
                }
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.summary = stringValue
            }
            true
        }

        /**
         * Binds a preference's summary to its value. More specifically, when the
         * preference's value is changed, its summary (line of text below the
         * preference title) is updated to reflect the value. The summary is also
         * immediately updated upon calling this method. The exact display format is
         * dependent on the type of preference.
         *
         * @see .sBindPreferenceSummaryToValueListener
         */
        private fun bindPreferenceSummaryToValue(preference: Preference?, type: Int = 2) {
            // Set the listener to watch for value changes.
            preference?.onPreferenceChangeListener = sBindPreferenceSummaryToValueListener

            // Trigger the listener immediately with the preference's
            // current value.
            val preferences = PreferenceManager.getDefaultSharedPreferences(preference?.context)

            var stringValue: String? = ""
            when (type) {
                1 ->
                    //integer
                    stringValue = preferences.getInt(preference?.key, 0).toString()
                2 ->
                    //string
                    stringValue = preferences.getString(preference?.key, "")
                3 ->
                    //boolean
                    stringValue = java.lang.Boolean.toString(preferences.getBoolean(preference?.key, false))
            }

            sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, stringValue)
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount == 1) finish()
        else super.onBackPressed()
    }
}
