package com.addev.listaspam

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.addev.listaspam.util.ApiUtils

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
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    onWebPageLoaded(url)
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    completeChallengeIfVerified(userId.orEmpty())
                }
            }
        }
        setContentView(webView)

        val formData = "os_version=${android.os.Build.VERSION.SDK_INT}" +
            "&user_id=$userId&_action=_get_new_api_key&device=Android"
        webView.postUrl(ApiUtils.UNKNOWN_PHONE_API_URL, formData.toByteArray(Charsets.UTF_8))
    }

    private fun onWebPageLoaded(url: String) {
        val currentUserId = userId.orEmpty()
        if (challengeCompleted) return

        val cookie = CookieManager.getInstance().getCookie(ApiUtils.UNKNOWN_PHONE_API_URL).orEmpty()
        if (cookie.contains("cf_clearance=")) {
            val formData = "os_version=${android.os.Build.VERSION.SDK_INT}" +
                "&user_id=$currentUserId&_action=_get_new_api_key&device=Android"
            webView.postUrl(ApiUtils.UNKNOWN_PHONE_API_URL, formData.toByteArray(Charsets.UTF_8))
            return
        }

        val currentCookie = CookieManager.getInstance().getCookie(url).orEmpty()
        if (currentCookie.contains("cf_clearance=")) {
            completeChallengeIfVerified(currentUserId)
            return
        }

        if (url.contains("challenge") || url.contains("captcha")) {
            webView.evaluateJavascript(
                """
                (function() {
                    var btn = document.querySelector('input[type="submit"], button[type="submit"], .challenge-submit, #challenge-submit');
                    if (btn) btn.click();
                    return 'clicked';
                })();
                """.trimIndent()
            ) {}
        }
    }

    private fun completeChallengeIfVerified(userId: String) {
        if (challengeCompleted) return

        val cookie = CookieManager.getInstance().getCookie(ApiUtils.UNKNOWN_PHONE_API_URL).orEmpty()
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