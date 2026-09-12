package com.addev.listaspam

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.addev.listaspam.preferences.BaseListManagerDialogFragment
import com.addev.listaspam.preferences.BaseListManagerPreference
import com.addev.listaspam.util.BLOCK_NUMBERS_KEY
import com.addev.listaspam.util.SPAM_PREFS
import com.addev.listaspam.util.WHITELIST_NUMBERS_KEY
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class SettingsActivity : AppCompatActivity() {
    private val retiredUnknownPhoneApiPreferenceKeys = setOf(
        "pref_filter_lista_spam",
        "pref_language",
        "pref_named_call_log",
        "pref_system_info_spam_total",
        "pref_unknown_phone_api_key"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateSettingsContainer()

        val exportButton: Button = findViewById(R.id.btn_export)
        val importButton: Button = findViewById(R.id.btn_import)

        exportButton.setOnClickListener {
            val timestamp = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
            exportFileLauncher.launch("spam_export_${timestamp}.json")
        }

        importButton.setOnClickListener {
            importFileLauncher.launch(arrayOf("application/json"))
        }
    }

    /**
     * Updates the settings container by replacing its content with a new instance of SettingsFragment.
     * This function is typically called when the activity is created or when settings need to be refreshed,
     * for example, after importing new settings.
     */
    private fun updateSettingsContainer() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
    }

    /**
     * A [PreferenceFragmentCompat] that displays the application's settings.
     * It loads preferences from an XML resource file.
     */
    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            if (preference is BaseListManagerPreference) {
                val fragment = BaseListManagerDialogFragment.newInstance(preference.key)
                fragment.setTargetFragment(this, 0)
                fragment.show(parentFragmentManager, "list_manager_dialog")
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }
    }

    /**
     * ActivityResultLauncher for creating a document to export SharedPreferences.
     *
     * This launcher is used to initiate the system's file picker to select a location
     * and name for the JSON file where the SharedPreferences data will be exported.
     *
     * Upon successful selection of a URI by the user, the [exportAllSharedPreferences]
     * method is called with the chosen URI. A Toast message is displayed to indicate
     * the success or failure of the export operation.
     */
    private val exportFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
            uri?.let {
                if (exportAllSharedPreferences(it)) {
                    Toast.makeText(
                        this,
                        this.getString(R.string.preferences_export_success),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } else {
                    Toast.makeText(
                        this,
                        this.getString(R.string.preferences_export_error),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }

    /**
     * ActivityResultLauncher for handling the result of opening a document to import SharedPreferences.
     * It expects a URI as a result. If a URI is received, it attempts to import
     * SharedPreferences from the selected file.
     * Displays a success or error Toast message based on the outcome of the import operation.
     * After a successful import, it updates the settings container.
     */
    private val importFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let {
                if (importAllSharedPreferences(it)) {
                    updateSettingsContainer()
                    Toast.makeText(
                        this,
                        this.getString(R.string.preferences_import_success),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                } else {
                    Toast.makeText(
                        this,
                        this.getString(R.string.preferences_import_error),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }

    /**
     * Exports all SharedPreferences to a JSON file.
     *
     * This function iterates through all SharedPreferences files in the application's
     * `shared_prefs` directory. For each file, it reads all key-value pairs
     * and constructs a JSON object representing the preferences. These individual
     * JSON objects are then combined into a single parent JSON object, where each
     * key is the name of the SharedPreferences file (without the .xml extension)
     * and the value is the corresponding preferences JSON object.
     *
     * The resulting parent JSON object is then written to the `OutputStream`
     * associated with the provided `Uri`.
     *
     * Special handling is implemented for `Set<String>` values, which are
     * converted to JSONArrays. Other primitive types (Boolean, Int, Long, Float, String)
     * are directly put into the JSON object.
     *
     * @param uri The Uri of the file where the SharedPreferences data will be exported.
     *            This Uri should be obtained from a file picker (e.g., using
     *            `ActivityResultContracts.CreateDocument`).
     * @return `true` if the export was successful, `false` otherwise (e.g., if an
     *         IOException or JSONException occurs).
     */
    private fun exportAllSharedPreferences(uri: Uri): Boolean {
        return try {
            val prefsDir = File(applicationInfo.dataDir, "shared_prefs")
            val files = prefsDir.listFiles()
            val jsonObject = JSONObject()

            files?.forEach { file ->
                val prefName = file.nameWithoutExtension
                val sharedPreferences = getSharedPreferences(prefName, Context.MODE_PRIVATE)
                val allEntries: Map<String, *> = sharedPreferences.all

                val booleans = JSONObject()
                val strings = JSONObject()
                val lists = JSONObject()
                for ((key, value) in allEntries) {
                    when {
                        value is Boolean -> booleans.put(key, value)
                        value is Set<*> -> lists.put(key, JSONArray(value))
                        value is String && value.startsWith("[") -> {
                            try {
                                lists.put(key, JSONArray(value))
                            } catch (_: Exception) {
                                strings.put(key, value)
                            }
                        }
                        else -> strings.put(key, value)
                    }
                }
                val prefJsonObject = JSONObject()
                for (k in booleans.keys()) prefJsonObject.put(k, booleans.get(k))
                for (k in strings.keys()) prefJsonObject.put(k, strings.get(k))
                for (k in lists.keys()) prefJsonObject.put(k, lists.get(k))

                jsonObject.put(prefName, prefJsonObject)
            }

            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jsonObject.toString().toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Imports all SharedPreferences from a JSON file specified by the given URI.
     *
     * This function reads a JSON file, validates its structure, and then iterates through
     * each SharedPreferences file represented in the JSON. For each preference file,
     * it reads the key-value pairs and writes them to the corresponding SharedPreferences
     * on the device.
     *
     * Supported data types for import are: `String`, `Int`, `Long`, `Float`, `Boolean`,
     * and `Set<String>` (represented as a JSONArray of strings in the JSON).
     *
     * @param uri The URI of the JSON file to import SharedPreferences from.
     * @return `true` if the import was successful, `false` otherwise (e.g., if the file is invalid,
     *         an I/O error occurs, or a JSON parsing error occurs).
     */
    private fun importAllSharedPreferences(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(jsonString)

                // Validar el archivo JSON antes de importar
                if (!isValidSharedPreferencesJson(jsonObject)) {
                    Toast.makeText(
                        this,
                        this.getString(R.string.invalid_json_file),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }

                for (prefName in jsonObject.keys()) {
                    val sharedPreferences = getSharedPreferences(prefName, Context.MODE_PRIVATE)
                    sharedPreferences.edit {
                        val prefJsonObject = jsonObject.getJSONObject(prefName)
                        for (key in prefJsonObject.keys()) {
                            if (key in retiredUnknownPhoneApiPreferenceKeys) continue
                            val targetKey = if (key == "pref_filter_lista_spam_scraper") {
                                "pref_listaspam_scraper"
                            } else {
                                key
                            }
                            when (val value = prefJsonObject.get(key)) {
                                is JSONArray -> {
                                    if (prefName == SPAM_PREFS) {
                                        val set = (0 until value.length())
                                            .mapNotNull { index ->
                                                value.optString(index).takeIf { it.isNotBlank() && it != "null" }
                                            }
                                            .toSet()
                                        putStringSet(targetKey, set)
                                    } else {
                                        putString(targetKey, value.toString())
                                    }
                                }
                                is Int -> putInt(targetKey, value)
                                is Long -> putLong(targetKey, value)
                                is Float -> putFloat(targetKey, value)
                                is Boolean -> putBoolean(targetKey, value)
                                is String -> putString(targetKey, value)
                                JSONObject.NULL -> { /* omitir claves con valor null */ }
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Validates if the given JSONObject represents a valid SharedPreferences structure.
     *
     * This function checks if the JSON object conforms to the expected format of
     * SharedPreferences data. It iterates through each preference file (represented by
     * a key in the root JSONObject) and then iterates through each key-value pair
     * within that preference file.
     *
     * The validation ensures:
     * - Each preference file is a JSONObject.
     * - Values within each preference file are of supported types:
     *   - Int
     *   - Long
     *   - Float
     *   - Boolean
     *   - String
     *   - JSONArray (where all elements are Strings, representing a Set<String>)
     *
     * If any part of the JSON structure does not conform to these rules, or if a
     * JSONException occurs during parsing, the function returns false.
     *
     * @param jsonObject The JSONObject to validate.
     * @return True if the JSONObject is a valid representation of SharedPreferences, false otherwise.
     */
    private fun isValidSharedPreferencesJson(jsonObject: JSONObject): Boolean {
        return try {
            for (prefName in jsonObject.keys()) {
                val prefJsonObject = jsonObject.getJSONObject(prefName)

                // Verificar cada valor en las SharedPreferences
                for (key in prefJsonObject.keys()) {
                    val value = prefJsonObject.get(key)
                    when (value) {
                        is JSONArray -> {
                            for (i in 0 until value.length()) {
                                if (value.get(i) !is String && value.get(i) != JSONObject.NULL) {
                                    return false
                                }
                            }
                        }

                        is Int, is Long, is Float, is Boolean, is String -> {
                            // Tipos válidos, no hacer nada
                        }

                        JSONObject.NULL -> {
                            // Valor nulo: se ignora al importar
                        }

                        else -> {
                            return false
                        }
                    }
                }
            }
            true
        } catch (e: JSONException) {
            e.printStackTrace()
            false
        }
    }
}