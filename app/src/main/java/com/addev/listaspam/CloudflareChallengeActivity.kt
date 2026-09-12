package com.addev.listaspam

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.addev.listaspam.util.ApiUtils
import com.ead.lib.cloudflare_bypass.BypassClient

class CloudflareChallengeActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var challengeCompleted = false
    private var userId: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userId = intent.getStringExtra(EXTRA_USER_ID)
        if (userId.isNullOrBlank()) {
            finish()
            return
        }

        CookieManager.getInstance().setAcceptCookie(true)
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CHROME_USER_AGENT
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : BypassClient() {
                override fun onPageStartedPassed(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStartedPassed(view, url, favicon)
                }

                override fun onPageFinishedByPassed(view: WebView?, url: String?) {
                    super.onPageFinishedByPassed(view, url)
                    checkAndCompleteChallenge()
                }

                override fun onReceivedError(
                    view: android.webkit.WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    checkAndCompleteChallenge()
                }
            }
        }
        setContentView(webView)

        webView.loadUrl(ApiUtils.UNKNOWN_PHONE_API_URL)
    }

    private fun checkAndCompleteChallenge() {
        val currentUserId = userId.orEmpty()
        if (challengeCompleted) return

        val cookie = CookieManager.getInstance()
            .getCookie(ApiUtils.UNKNOWN_PHONE_API_URL).orEmpty()
        if (cookie.contains("cf_clearance=")) {
            completeChallengeIfVerified(currentUserId)
        }
    }

    private fun completeChallengeIfVerified(userId: String) {
        if (challengeCompleted) return

        val cookie = CookieManager.getInstance()
            .getCookie(ApiUtils.UNKNOWN_PHONE_API_URL).orEmpty()
        if (!cookie.contains("cf_clearance=")) return

        challengeCompleted = true
        setResult(
            Activity.RESULT_OK,
            intent.putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_COOKIE, cookie)
                .putExtra(EXTRA_USER_AGENT, webView.settings.userAgentString)
        )
        finish()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_COOKIE = "cloudflare_cookie"
        const val EXTRA_USER_AGENT = "cloudflare_user_agent"
        private const val CHROME_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
