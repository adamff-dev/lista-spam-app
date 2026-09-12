package com.addev.listaspam.util

import android.app.AlertDialog
import android.content.Context
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.addev.listaspam.R
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportDialogManager(private val context: Context) {

    fun show(number: String) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_report, null)
        val dialog = createDialog(dialogView)
        setupDialogView(dialogView, number)
        setupDialogButtons(dialog, dialogView, number)
        dialog.show()
    }

    private fun createDialog(dialogView: View): AlertDialog {
        return AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.report_title))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.accept), null)
            .setNegativeButton(context.getString(R.string.cancel), null)
            .create()
    }

    private fun setupDialogView(dialogView: View, number: String) {
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)
        val spamRadio = dialogView.findViewById<RadioButton>(R.id.radioSpam)
        val noSpamRadio = dialogView.findViewById<RadioButton>(R.id.radioNoSpam)
        val checkboxTellows = dialogView.findViewById<CheckBox>(R.id.checkboxTellows)
        val checkboxTruecaller = dialogView.findViewById<CheckBox>(R.id.checkboxTruecaller)

        messageEditText.hint = context.getString(R.string.report_hint)
        spamRadio.text = context.getString(R.string.report_spam)
        noSpamRadio.text = context.getString(R.string.report_not_spam)

        checkboxTellows.text = buildProviderText(context, "Tellows", getCountryDisplayName())
        val prefix = extractPrefixFromNumber(number)
        checkboxTruecaller.text = context.getString(
            R.string.truecaller_prefix_repository,
            "+$prefix"
        )

    }

    private fun extractPrefixFromNumber(number: String): String {
        val phoneUtil = PhoneNumberUtil.getInstance()
        return try {
            val parsedNumber: Phonenumber.PhoneNumber = phoneUtil.parse(number, null)
            val countryCode = parsedNumber.countryCode
            countryCode.toString()
        } catch (e: Exception) {
            getDevicePrefix().toString()
        }
    }

    private fun setupDialogButtons(dialog: AlertDialog, dialogView: View, number: String) {
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.setOnClickListener {
                if (!validateInput(dialogView)) return@setOnClickListener

                val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
                progressBar.visibility = View.VISIBLE
                button.isEnabled = false

                submitReport(dialog, dialogView, number, progressBar, button)
            }
        }
    }

    private fun validateInput(dialogView: View): Boolean {
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)
        val spamRadio = dialogView.findViewById<RadioButton>(R.id.radioSpam)
        val noSpamRadio = dialogView.findViewById<RadioButton>(R.id.radioNoSpam)
        val checkboxTellows = dialogView.findViewById<CheckBox>(R.id.checkboxTellows)
        val checkboxTruecaller = dialogView.findViewById<CheckBox>(R.id.checkboxTruecaller)

        val message = messageEditText.text.toString().trim()
        val wordCount = message.split("\\s+".toRegex()).size
        val charCount = message.replace("\\s".toRegex(), "").length

        if (wordCount < 2 || charCount < 10) {
            messageEditText.error = context.getString(R.string.report_validation_message)
            return false
        }

        if (!spamRadio.isChecked && !noSpamRadio.isChecked) {
            Toast.makeText(
                context,
                context.getString(R.string.report_radio_validation),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        if (!checkboxTellows.isChecked && !checkboxTruecaller.isChecked) {
            Toast.makeText(
                context,
                context.getString(R.string.select_at_least_one_provider),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }

        return true
    }

    private fun submitReport(
        dialog: AlertDialog,
        dialogView: View,
        number: String,
        progressBar: ProgressBar,
        button: Button
    ) {
        val messageEditText = dialogView.findViewById<EditText>(R.id.messageEditText)
        val spamRadio = dialogView.findViewById<RadioButton>(R.id.radioSpam)
        val checkboxTellows = dialogView.findViewById<CheckBox>(R.id.checkboxTellows)
        val checkboxTruecaller = dialogView.findViewById<CheckBox>(R.id.checkboxTruecaller)

        val message = messageEditText.text.toString().trim()
        val isSpam = spamRadio.isChecked

        CoroutineScope(Dispatchers.IO).launch {
            val reportedTo = mutableListOf<String>()

            if (checkboxTellows.isChecked) {
                getTellowsApiCountry(context)?.let { country ->
                    if (ApiUtils.reportToTellows(number, message, isSpam, country)) {
                        reportedTo.add("Tellows")
                    }
                }
            }

            if (checkboxTruecaller.isChecked) {
                // Add country code prefix if missing
                val prefix = getDevicePrefix()
                val formattedPhone = if (!number.startsWith("+")) {
                    "$prefix$number"
                } else {
                    number
                }

                if (ApiUtils.reportToTruecaller(formattedPhone, message, isSpam)) {
                    reportedTo.add("TrueCaller")
                }
            }

            val reportMessage = buildReportMessage(reportedTo)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                button.isEnabled = true
                Toast.makeText(context, reportMessage, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
    }

    private fun getDevicePrefix(): Int {
        val telephonyManager = ContextCompat.getSystemService(context, TelephonyManager::class.java)
        val countryIso = telephonyManager?.networkCountryIso?.uppercase()
        val phoneUtil = PhoneNumberUtil.getInstance()
        return phoneUtil.getCountryCodeForRegion(countryIso)
    }

    private fun buildReportMessage(reportedTo: List<String>): String {
        return if (reportedTo.isNotEmpty()) {
            context.getString(R.string.report_success_prefix) + " " +
                    reportedTo.joinToString(", ")
        } else {
            context.getString(R.string.report_failure)
        }
    }

    private fun getCountryDisplayName(): String {
        val country = getTellowsApiCountry(context)?.lowercase()
        val countryValues = context.resources.getStringArray(R.array.entryvalues_region_preference)
        val countryNames = context.resources.getStringArray(R.array.entries_region_preference)
        return getDisplayName(country, countryValues, countryNames)
            ?: context.getString(R.string.unknown_value)
    }


    private fun getDisplayName(
        value: String?,
        values: Array<String>,
        names: Array<String>
    ): String? {
        return value?.let {
            val index = values.indexOf(it)
            if (index in names.indices) names[index] else it
        }
    }

    private fun buildProviderText(
        context: Context,
        providerName: String,
        displayName: String
    ): String {
        return context.getString(R.string.provider_repository_format, providerName, displayName)
    }
}
