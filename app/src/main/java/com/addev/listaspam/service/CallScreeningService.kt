package com.addev.listaspam.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.addev.listaspam.util.SpamUtils
import com.addev.listaspam.util.shouldAnswerAndHangup
import com.addev.listaspam.util.shouldMuteInsteadOfBlocking

/**
 * Call screening service to identify and block spam calls.
 */
class CallScreeningService : CallScreeningService() {

    private val spamUtils = SpamUtils()

    /**
     * Called when an incoming call is being screened.
     * @param details Details of the incoming call.
     */
    override fun onScreenCall(details: Call.Details) {
        // Only handle incoming calls
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) return

        spamUtils.checkSpamNumber(this, null, details) { isSpam ->
            if (isSpam) {
                endCall(details)
            } else {
                // Allow the call to proceed normally
                respondToCall(
                    details, CallResponse.Builder()
                        .setDisallowCall(false)
                        .build()
                )
            }
        }
    }

    /**
     * Ends the call by either muting or blocking it based on user preferences.
     * @param details Details of the call to be ended.
     */
    private fun endCall(details: Call.Details) {
        val context = this
        val telecomManager = getSystemService(TELECOM_SERVICE) as TelecomManager

        if (shouldAnswerAndHangup(context)) {
            // Silence the call first so the user is not interrupted
            respondToCall(
                details, CallResponse.Builder()
                    .setDisallowCall(false)
                    .setSilenceCall(true)
                    .setSkipCallLog(false)
                    .setSkipNotification(true)
                    .build()
            )
            // Immediately answer and hang up
            try {
                telecomManager.acceptRingingCall()
                telecomManager.endCall()
            } catch (e: SecurityException) {
                // Fallback to normal block if permissions fail
                respondToCall(
                    details, CallResponse.Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                        .build()
                )
            }
            return
        }

        val shouldMute = shouldMuteInsteadOfBlocking(this)
        if (shouldMute) {
            respondToCall(
                details, CallResponse.Builder()
                    .setSilenceCall(true)
                    .build()
            )
        } else {
            respondToCall(
                details, CallResponse.Builder()
                    .setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true)
                    .build()
            )
        }
    }
}
