package com.addev.listaspam.adapter

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.addev.listaspam.R
import com.addev.listaspam.util.CallLogEntry
import com.addev.listaspam.util.ReportDialogManager
import com.addev.listaspam.util.addNumberToWhitelist
import com.addev.listaspam.util.removeSpamNumber
import com.addev.listaspam.util.removeWhitelistNumber
import com.addev.listaspam.util.saveSpamNumber
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import androidx.core.net.toUri

class CallLogAdapter(
    private val context: Context,
    var callLogs: List<CallLogEntry>,
    var blockedNumbers: Set<String>,
    var whitelistNumbers: Set<String>
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    interface OnItemChangedListener {
        fun onItemChanged(number: String)
    }

    companion object {
        const val GOOGLE_URL_TEMPLATE = "https://www.google.com/search?q=%s"
        const val LISTA_SPAM_URL_TEMPLATE = "https://www.listaspam.com/busca.php?Telefono=%s"
        const val UNKNOWN_PHONE_URL_TEMPLATE = "https://www.unknownphone.com/phone/%s"
        const val WHATSAPP_URL_TEMPLATE = "https://wa.me/%s"
    }
    
    private val formatter: DateTimeFormatter = getSystemLocalizedFormatter()

    private var onItemChangedListener: OnItemChangedListener? = null

    private fun getSystemLocalizedFormatter(): DateTimeFormatter {
        val locale = Locale.getDefault()
        return DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val callLog = callLogs[position]
        holder.bind(
            callLog,
            blockedNumbers.contains(callLog.number),
            whitelistNumbers.contains(callLog.number)
        )
    }

    override fun getItemCount(): Int = callLogs.size

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numberTextView: TextView = itemView.findViewById(R.id.numberTextView)
        private val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
        private val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        private val actionTextView: TextView = itemView.findViewById(R.id.actionTextView)
        private val overflowMenuButton = itemView.findViewById<ImageButton>(R.id.overflowMenuButton)

        fun bind(callLog: CallLogEntry, isBlocked: Boolean, isWhitelisted: Boolean = false) {
            val number = callLog.number ?: "Unknown number"
            val contactName = getContactName(context, number)
            val textToShow = if (isBlocked) {
                val displayText = when {
                    contactName != null -> contactName
                    number.isNotBlank() -> number
                    else -> context.getString(R.string.unknown_value)
                }
                context.getString(R.string.blocked_text_format, displayText)
            } else if (isWhitelisted) {
                context.getString(R.string.whitelisted_text_format, contactName ?: number)
            } else {
                contactName ?: number
            }
            numberTextView.text = textToShow
            dateTextView.text = formatter.format(
                Instant.ofEpochMilli(callLog.date.time).atZone(ZoneId.systemDefault())
            )
            durationTextView.text = context.getString(R.string.duration_label, callLog.duration)

            val action = when (callLog.type) {
                CallLog.Calls.INCOMING_TYPE -> context.getString(R.string.call_incoming)
                CallLog.Calls.OUTGOING_TYPE -> context.getString(R.string.call_outgoing)
                CallLog.Calls.MISSED_TYPE -> context.getString(R.string.call_missed)
                CallLog.Calls.REJECTED_TYPE -> context.getString(R.string.call_rejected)
                CallLog.Calls.BLOCKED_TYPE -> context.getString(R.string.call_blocked)
                else -> context.getString(R.string.call_unknown)
            }

            actionTextView.text = action

            if (callLog.type == CallLog.Calls.BLOCKED_TYPE) {
                actionTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_red_light
                    )
                )
            } else {
                actionTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.darker_gray
                    )
                )
            }

            when {
                isBlocked -> numberTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_red_light
                    )
                )

                isWhitelisted -> numberTextView.setTextColor(
                    ContextCompat.getColor(
                        context,
                        android.R.color.holo_blue_dark
                    )
                )

                else -> {
                    numberTextView.setTextColor(ContextCompat.getColor(context, R.color.textColor))
                }
            }

            if (number.isBlank()) {
                overflowMenuButton.visibility = View.GONE
                return
            }

            overflowMenuButton.visibility = View.VISIBLE
            overflowMenuButton.setOnClickListener {
                val popupMenu = PopupMenu(
                    itemView.context,
                    overflowMenuButton,
                    Gravity.NO_GRAVITY,
                    android.R.attr.popupMenuStyle,
                    R.style.PopupMenuStyle
                )
                popupMenu.inflate(R.menu.item_actions)

                setDynamicTitles(popupMenu, isBlocked, isWhitelisted)

                popupMenu.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        R.id.search_action -> {
                            searchAction(number)
                            true
                        }

                        R.id.open_report_alert -> {
                            openReportAlert(number)
                            true
                        }

                        R.id.open_in_lista_spam_action -> {
                            openInListaSpam(number)
                            true
                        }

                        R.id.open_in_unknown_phone_action -> {
                            openInUnknownPhone(number)
                            true
                        }

                        R.id.open_in_whatsapp_action -> {
                            openInWhatsApp(number)
                            true
                        }

                        R.id.add_to_contacts_action -> {
                            addToContacts(number)
                            true
                        }

                        R.id.whitelist_action -> {
                            if (isWhitelisted) {
                                removeWhitelistNumber(context, number)
                            } else {
                                addNumberToWhitelist(context, number)
                            }
                            onItemChangedListener?.onItemChanged(number)
                            true
                        }

                        R.id.block_action -> {
                            if (isBlocked) {
                                removeSpamNumber(context, number)
                            } else {
                                saveSpamNumber(context, number)
                            }
                            onItemChangedListener?.onItemChanged(number)
                            true
                        }

                        else -> false
                    }
                }
                popupMenu.show()
            }

            // Copy number to clipboard
            itemView.setOnLongClickListener {
                clipboardAction(number)
                true
            }
        }

        private fun setDynamicTitles(
            popupMenu: PopupMenu,
            isBlocked: Boolean,
            isWhitelisted: Boolean
        ) {
            val blockMenuItem = popupMenu.menu.findItem(R.id.block_action)
            val whitelistMenuItem = popupMenu.menu.findItem(R.id.whitelist_action)
            if (isBlocked) {
                blockMenuItem.setTitle(R.string.unblock)
            } else {
                blockMenuItem.setTitle(R.string.block)
            }

            if (isWhitelisted) {
                whitelistMenuItem.setTitle(R.string.remove_from_whitelist)
            } else {
                whitelistMenuItem.setTitle(R.string.add_to_whitelist)
            }
        }
    }

    private fun getContactName(context: Context, phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun clipboardAction(number: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("phone number", number)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(
            context,
            context.getString(R.string.number_copied_to_clipboard),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openInListaSpam(number: String) {
        val url = String.format(LISTA_SPAM_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    private fun openInUnknownPhone(number: String) {
        val url = String.format(UNKNOWN_PHONE_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    private fun openInWhatsApp(number: String) {
        // Remove any non-digit characters except +
        val cleanNumber = number.replace(Regex("[^+\\d]"), "")
        val url = String.format(WHATSAPP_URL_TEMPLATE, cleanNumber)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    }

    private fun addToContacts(number: String) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, number)
        }
        context.startActivity(intent)
    }

    private fun searchAction(number: String) {
        val url = String.format(GOOGLE_URL_TEMPLATE, number)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    fun setOnItemChangedListener(listener: OnItemChangedListener) {
        this.onItemChangedListener = listener
    }

    private fun openReportAlert(number: String) {
        val reportDialogManager = ReportDialogManager(context)
        reportDialogManager.show(number)
    }
}
