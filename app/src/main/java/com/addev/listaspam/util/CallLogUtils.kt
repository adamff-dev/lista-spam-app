// CallLogUtils.kt
package com.addev.listaspam.util

import android.content.Context
import android.database.Cursor
import android.provider.CallLog
import java.util.*

data class CallLogEntry(
    val number: String,
    val type: Int,
    val date: Date,
    val duration: Long
)

fun getCallLogs(context: Context): List<CallLogEntry> {
    val callLogs = mutableListOf<CallLogEntry>()

    val selection = "${CallLog.Calls.TYPE} IN (${CallLog.Calls.INCOMING_TYPE}, ${CallLog.Calls.OUTGOING_TYPE}, ${CallLog.Calls.REJECTED_TYPE}, ${CallLog.Calls.BLOCKED_TYPE}, ${CallLog.Calls.MISSED_TYPE})"

    val cursor: Cursor? = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        null,
        selection,
        null,
        CallLog.Calls.DATE + " DESC"
    )

    cursor?.use {
        val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
        val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
        val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
        val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

        while (it.moveToNext()) {
            val number = it.getString(numberIndex)
            val type = it.getInt(typeIndex)
            val date = Date(it.getLong(dateIndex))
            val duration = it.getLong(durationIndex)
            callLogs.add(CallLogEntry(number, type, date, duration))
        }
    }

    return callLogs
}
