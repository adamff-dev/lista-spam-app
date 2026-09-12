package com.addev.listaspam.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.addev.listaspam.db.AppDatabase
import com.addev.listaspam.db.DangerousPhone
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory


/**
 * Utility object for interacting with the UnknownPhone API to check if a phone number is marked as spam.
 */
object ApiUtils {
    private const val TAG = "UnknownPhoneApi"
    internal const val UNKNOWN_PHONE_API_URL = "https://secure.unknownphone.com/api/v4/"
    private const val UNKNOWN_PHONE_API_KEY_FALLBACK = "d7e07fec659645b12df76c94e378d47a"
    private const val UNKNOWN_PHONE_APP_VERSION = "3.4.3"
    private const val UNKNOWN_PHONE_APP_BUILD = "607"
    private const val UNKNOWN_PHONE_USER_AGENT = "okhttp/3.14.9"

    private const val TELLOWS_API_URL = "www.tellows.de"
    private const val TELLOWS_API_KEY = "koE5hjkOwbHnmcADqZuqqq2"

    private const val TRUECALLER_API_URL_EU = "search5-eu.truecaller.com"
    private const val TRUECALLER_API_URL_NONEU = "search5-noneu.truecaller.com"
    private const val TRUECALLER_API_KEY = "a1i1V--ua298eldF0hb0rL520GjDz7bzVAdt63J2nzZBnWlEKNCJUeln_7kWj4Ir"

    private const val TRUE_CALLER_REPORT_API_URL_EU = "https://filter-store4-eu.truecaller.com/v4/filters?encoding=json"
    private const val TRUE_CALLER_REPORT_API_URL_NONEU = "https://filter-store4-noneu.truecaller.com/v4/filters?encoding=json"

    private val EU_COUNTRIES = setOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
        "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE"
    )

    private val client = OkHttpClient()

    private data class PhoneDirectory(
        val searchUrl: (String) -> String,
        val isSpam: (String) -> Boolean
    )

    private val phoneDirectories = mapOf(
        "ES" to listaSpamDirectory("www.listaspam.com"),
        "MX" to listaSpamDirectory("mx.listaspam.com"),
        "AR" to listaSpamDirectory("ar.listaspam.com"),
        "CO" to listaSpamDirectory("co.listaspam.com"),
        "CL" to listaSpamDirectory("cl.listaspam.com"),
        "PE" to listaSpamDirectory("pe.listaspam.com"),
        "VE" to listaSpamDirectory("ve.listaspam.com"),
        "EC" to listaSpamDirectory("ec.listaspam.com"),
        "UY" to listaSpamDirectory("uy.listaspam.com"),
        "FR" to localizedDirectory("www.telefono-numero.fr", ::hasFrenchSpamRating),
        "IT" to localizedDirectory("www.chi-chiama.it", ::hasItalianSpamRating),
        "DE" to PhoneDirectory(
            searchUrl = { number -> "https://www.anrufer-bewertung.de/${number.replace("+", "%2B")}" },
            isSpam = ::hasGermanSpamRating
        ),
        "PT" to localizedDirectory("www.quemmelineu.com", ::hasPortugueseSpamRating),
        "AU" to unknownPhoneDirectory(),
        "CA" to unknownPhoneDirectory(),
        "GB" to unknownPhoneDirectory(),
        "US" to unknownPhoneDirectory(),
        "OTHER" to unknownPhoneDirectory()
    )

    private fun listaSpamDirectory(host: String) = PhoneDirectory(
        searchUrl = { number -> phoneSearchUrl(host, "busca.php", "Telefono", number) },
        isSpam = ::hasListaSpamSpamRating
    )

    private fun localizedDirectory(host: String, isSpam: (String) -> Boolean) = PhoneDirectory(
        searchUrl = { number -> phoneSearchUrl(host, "search.php", "num", number) },
        isSpam = isSpam
    )

    private fun unknownPhoneDirectory() = PhoneDirectory(
        searchUrl = { number -> phoneSearchUrl("www.unknownphone.com", "search.php", "num", number) },
        isSpam = ::hasUnknownPhoneSpamRating
    )

    private fun phoneSearchUrl(host: String, path: String, parameter: String, number: String): String =
        HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegment(path)
            .addQueryParameter(parameter, number)
            .build()
            .toString()

    private fun hasListaSpamSpamRating(html: String): Boolean =
        Jsoup.parse(html)
            .select(".rate-and-owner .phone_rating:not(.result-4):not(.result-5)")
            .isNotEmpty()

    private fun hasUnknownPhoneSpamRating(html: String): Boolean =
        Regex("Rating:\\s*.*\\b(Bad|Dangerous)\\b", RegexOption.IGNORE_CASE)
            .containsMatchIn(Jsoup.parse(html).text())

    private fun hasFrenchSpamRating(html: String): Boolean =
        hasNegativeRating(html, "Mauvais", "Dangereux", "Arnaque", "Indesirable")

    private fun hasItalianSpamRating(html: String): Boolean =
        hasNegativeRating(html, "Cattivo", "Pericoloso", "Truffa", "Indesiderato")

    private fun hasPortugueseSpamRating(html: String): Boolean =
        hasNegativeRating(html, "Mau", "Perigoso", "Fraude", "Indesejado")

    private fun hasGermanSpamRating(html: String): Boolean =
        hasNegativeRating(html, "Unseriose Nummer", "Belastigung", "Spam")

    private fun hasNegativeRating(html: String, vararg negativeRatings: String): Boolean =
        Regex(
            "(?:Rating|Bewertung|Valutazione|Classificazione|Avaliacao)\\s*:\\s*.*\\b(${negativeRatings.joinToString("|")})\\b",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(Jsoup.parse(html).text())

    fun checkListaSpamScraper(number: String, country: String): Boolean {
        val directory = phoneDirectories[country.uppercase()] ?: return false
        val request = Request.Builder()
            .url(directory.searchUrl(number))
            .header("User-Agent", UNKNOWN_PHONE_USER_AGENT)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful && directory.isSpam(response.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun getApiKey(context: Context): String {
        fetchAndStoreApiKey(context)
        return getUnknownPhoneApiKey(context) ?: UNKNOWN_PHONE_API_KEY_FALLBACK
    }

    /**
     * Fetches and stores a new API key from the UnknownPhone server.
     * 
     * This method automatically calls launchOnboardingRequests() after obtaining the API key.
     * The three onboarding requests (updateDangerousPhonesList, fetchNameCallLog, and fetchSystemInfo)
     * must be executed to prevent the API key from being flagged as invalid by the UnknownPhone server.
     * These requests are handled internally by this method, so no additional calls are needed.
     *
     * @param context The application context.
     */
    fun fetchAndStoreApiKey(context: Context) {
        if (getUnknownPhoneApiKey(context) != null) return
        fetchApiKey(
            context,
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID),
            null,
            UNKNOWN_PHONE_USER_AGENT
        )
        launchOnboardingRequests(context)
    }

    private fun launchOnboardingRequests(context: Context) {
        val lang = getListaSpamApiLang(context) ?: Locale.getDefault().country.uppercase()
        val apiKey = getUnknownPhoneApiKey(context) ?: return
        Thread { updateDangerousPhonesList(context, apiKey, lang, "onboarding") }.start()
        Thread { fetchNameCallLog(context, apiKey, lang) }.start()
        Thread { fetchSystemInfo(context, apiKey, lang) }.start()
    }

    fun refreshDangerousPhonesList(context: Context) {
        val lang = getListaSpamApiLang(context) ?: Locale.getDefault().country.uppercase()
        val apiKey = getUnknownPhoneApiKey(context) ?: return
        Thread { updateDangerousPhonesList(context, apiKey, lang, "manual") }.start()
    }

    /**
     * Fetches and updates the list of dangerous phone numbers from the UnknownPhone API.
     * 
     * This method retrieves a list of known spam and dangerous phone numbers based on the user's
     * language and region settings, and stores them in the local database for offline consultation.
     * This is part of the onboarding process and should be called via launchOnboardingRequests().
     *
     * @param context The application context.
     * @param apiKey The API key for authentication with the UnknownPhone service.
     * @param lang The language code for the request (e.g., "ES" for Spanish).
     * @param syncTrigger The trigger for this sync operation (e.g., "onboarding", "manual").
     */
    fun updateDangerousPhonesList(context: Context, apiKey: String, lang: String, syncTrigger: String) {
        val body = FormBody.Builder()
            .add("_action", "_get_dangerous_phones_list")
            .add("api_key", apiKey)
            .add("region", Locale.getDefault().country.uppercase())
            .add("lang", lang)
            .add("user_type", "free")
            .add("device", "Android")
            .add("country_code", Locale.getDefault().country.uppercase())
            .add("os_version", Build.VERSION.SDK_INT.toString())
            .add("sync_trigger", syncTrigger)
            .build()

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .addHeader("User-Agent", "okhttp/3.14.9")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val arr = JSONArray(response.body?.string() ?: return)
                val phones = mutableListOf<DangerousPhone>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val phone = obj.optString("phone").takeIf { it.isNotBlank() } ?: continue
                    phones.add(DangerousPhone(phone))
                }
                val dao = AppDatabase.getInstance(context).dangerousPhoneDao()
                dao.deleteAll()
                if (phones.isNotEmpty()) dao.insertAll(phones)
            }
        } catch (_: Exception) {}
    }

    /**
     * Sends the device's call log to the UnknownPhone API for processing.
     * 
     * This method retrieves up to 30 phone numbers from the device's call history and sends them
     * to the UnknownPhone server for analysis and enrichment. This is part of the onboarding process
     * and helps the server maintain information about the user's contacts and calling patterns.
     * This request is essential for proper API key validation.
     *
     * @param context The application context.
     * @param apiKey The API key for authentication with the UnknownPhone service.
     * @param lang The language code for the request (e.g., "ES" for Spanish).
     */
    private fun fetchNameCallLog(context: Context, apiKey: String, lang: String) {
        val numbers = try {
            getCallLogs(context)
                .map { it.number }
                .filter { it.isNotBlank() }
                .distinct()
                .take(30)
        } catch (_: Exception) { return }

        if (numbers.isEmpty()) return

        val bodyBuilder = FormBody.Builder()
            .add("_action", "_name_call_log")
            .add("api_key", apiKey)
            .add("lang", lang)
            .add("app_version", UNKNOWN_PHONE_APP_VERSION)
            .add("app_build", UNKNOWN_PHONE_APP_BUILD)
            .add("os_version", Build.VERSION.SDK_INT.toString())
        numbers.forEachIndexed { i, num -> bodyBuilder.add("phone_numbers[$i]", num) }

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .addHeader("User-Agent", "okhttp/3.14.9")
            .post(bodyBuilder.build())
            .build()

        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    /**
     * Sends system information to the UnknownPhone API.
     * 
     * This method gathers and transmits device and system information to the UnknownPhone server
     * for analytics and validation purposes. This is part of the onboarding process and is required
     * to prevent the API key from being flagged as invalid by the server.
     *
     * @param context The application context.
     * @param apiKey The API key for authentication with the UnknownPhone service.
     * @param lang The language code for the request (e.g., "ES" for Spanish).
     */
    private fun fetchSystemInfo(context: Context, apiKey: String, lang: String) {
        val body = FormBody.Builder()
            .add("_action", "_get_system_info")
            .add("api_key", apiKey)
            .add("lang", lang)
            .build()

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .addHeader("User-Agent", "okhttp/3.14.9")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { }
        } catch (_: Exception) {}
    }

    enum class ApiKeyRenewalResult {
        SUCCESS,
        CLOUDFLARE_CHALLENGE,
        FAILURE
    }

    fun newApiKeyUserId(): String = UUID.randomUUID().toString()

    fun renewApiKey(context: Context): Boolean =
        renewApiKey(context, newApiKeyUserId()) == ApiKeyRenewalResult.SUCCESS

    fun renewApiKey(
        context: Context,
        userId: String,
        cloudflareCookie: String? = null,
        userAgent: String = UNKNOWN_PHONE_USER_AGENT
    ): ApiKeyRenewalResult {
        val result = fetchApiKey(context, userId, cloudflareCookie, userAgent)
        if (result == ApiKeyRenewalResult.SUCCESS) {
            launchOnboardingRequests(context)
        }
        return result
    }

    private fun fetchApiKey(
        context: Context,
        userId: String,
        cloudflareCookie: String?,
        userAgent: String
    ): ApiKeyRenewalResult {
        val fetchClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val body = FormBody.Builder()
            .add("os_version", Build.VERSION.SDK_INT.toString())
            .add("user_id", userId)
            .add("_action", "_get_new_api_key")
            .add("device", "Android")
            .build()

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .post(body)
            .header("User-Agent", userAgent)
            .apply {
                if (!cloudflareCookie.isNullOrBlank()) {
                    header("Cookie", cloudflareCookie)
                }
            }
            .build()

        return try {
            fetchClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                val contentType = response.header("Content-Type").orEmpty()
                val server = response.header("Server").orEmpty()
                val cfRay = response.header("CF-RAY")
                val isCloudflareChallenge =
                    response.code == 403 && (
                        cfRay != null ||
                        server.contains("cloudflare", ignoreCase = true) ||
                        responseBody.contains("error code:", ignoreCase = true) ||
                        responseBody.contains("cloudflare", ignoreCase = true)
                    )
                Log.i(
                    TAG,
                    "API key renewal: status=${response.code}, contentType=$contentType, " +
                        "server=$server, cfRay=$cfRay, cloudflareChallenge=$isCloudflareChallenge, " +
                        "bodyLength=${responseBody.length}, body=[${responseBody.take(1000)}]"
                )
                if (isCloudflareChallenge) {
                    return ApiKeyRenewalResult.CLOUDFLARE_CHALLENGE
                }
                if (!response.isSuccessful) return ApiKeyRenewalResult.FAILURE
                val json = JSONObject(responseBody)
                val apiKey = json.optString("api_key").takeIf { it.isNotBlank() }
                    ?: return ApiKeyRenewalResult.FAILURE
                setUnknownPhoneApiKey(context, apiKey)
                ApiKeyRenewalResult.SUCCESS
            }
        } catch (exception: Exception) {
            Log.w(TAG, "API key renewal request failed", exception)
            ApiKeyRenewalResult.FAILURE
        }
    }

    fun isCloudflareChallengeResponse(response: okhttp3.Response): Boolean {
        val server = response.header("Server").orEmpty()
        val cfRay = response.header("CF-RAY")
        return response.code == 403 && (
            cfRay != null ||
            server.contains("cloudflare", ignoreCase = true)
        )
    }

    /**
     * Sends a POST request to the UnknownPhone API to retrieve information about the given phone number.
     *
     * The method constructs a form-encoded request with necessary parameters, sends it using OkHttp,
     * and interprets the response to determine if the phone number is likely to be spam.
     *
     * @param number The phone number to check, in international format.
     * @return `true` if the number has an average rating lower than 3 (i.e., bad or dangerous), otherwise `false`.
     */
    fun checkListaSpamApi(context: Context, number: String, lang: String): Boolean {
        val formBody = FormBody.Builder()
            .add("user_type", "free")
            .add("api_key", getApiKey(context))
            .add("phone", number)
            .add("_action", "_get_info_for_phone")
            .add("lang", lang)
            .add("app_version", UNKNOWN_PHONE_APP_VERSION)
            .add("app_build", UNKNOWN_PHONE_APP_BUILD)
            .add("os_version", Build.VERSION.SDK_INT.toString())
            .build()

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .post(formBody)
            .header("Connection", "Keep-Alive")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Host", "secure.unknownphone.com")
            .header("User-Agent", "okhttp/3.14.9")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val bodyString = response.body?.string() ?: return false

            val avgRating =
                JSONObject(bodyString).optString("avg_ratings").toFloatOrNull() ?: return false

            // Average ratings:
            // 5 - safe
            // 4 - good
            // 3 - neutral
            // 2 - bad
            // 1 - dangerous
            avgRating < 3
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sends a POST report to the UnknownPhone API for a given phone number, marking it with a comment and metadata.
     *
     * This method constructs and sends a form-encoded POST request that includes the phone number, a comment,
     * call type, language, and optional metadata. It's used to report marketing or spam calls to the platform.
     *
     * @param phone The phone number being reported.
     * @param comment The user comment about the phone number.
     * @param lang The language code (e.g., "ES" for Spanish).
     * @return `true` if the report was submitted successfully; `false` otherwise.
     */
    fun reportToUnknownPhone(
        context: Context,
        phone: String,
        comment: String,
        isSpam: Boolean,
        lang: String,
    ): Boolean {
        val optRating = if (isSpam) "1" else "5"

        val formBuilder = FormBody.Builder()
            .add("api_key", getApiKey(context))
            .add("phone", phone)
            .add("_action", "_submit_comment")
            .add("comment", comment)
            .add("lang", lang)
            .add("_opt_rating", optRating)
            .add("_opt_type_call", "_not_specified")

        val request = Request.Builder()
            .url(UNKNOWN_PHONE_API_URL)
            .post(formBuilder.build())
            .header("Connection", "Keep-Alive")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Host", "secure.unknownphone.com")
            .header("User-Agent", "okhttp/3.14.9")
            .build()

        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    fun checkTruecallerSpamApi(number: String, countryCode: String): Boolean {
        val deviceCountryCode = Locale.getDefault().country

        val host = if (EU_COUNTRIES.contains(deviceCountryCode.uppercase())) {
            TRUECALLER_API_URL_EU
        } else {
            TRUECALLER_API_URL_NONEU
        }

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(host)
            .addPathSegments("v2/search")
            .addQueryParameter("q", number)
            .addQueryParameter("countryCode", countryCode)
            .addQueryParameter("type", "4")
            .addQueryParameter("locAddr", "")
            .addQueryParameter("placement", "SEARCHRESULTS,HISTORY,DETAILS")
            .addQueryParameter("adId", "")
            .addQueryParameter("encoding", "json")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Connection", "Keep-Alive")
            .header("User-Agent", "Truecaller/9.00.3 (Android;10)")
            .header("Authorization", "Bearer $TRUECALLER_API_KEY")
            .header("Host", "search5-eu.truecaller.com")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val bodyString = response.body?.string() ?: return false
            val json = JSONObject(bodyString)
            val dataArray = json.optJSONArray("data") ?: return false
            if (dataArray.length() == 0) return false

            val firstEntry = dataArray.getJSONObject(0)
            val spamInfo = firstEntry.optJSONObject("spamInfo")
            val spamType = spamInfo?.optString("spamType")
            val spamScore = spamInfo?.optInt("spamScore", 0) ?: 0 // Reports quantity

            !spamType.isNullOrBlank() && spamScore > 1
        } catch (e: Exception) {
            false
        }
    }

    fun reportToTruecaller(
        phone: String,
        comment: String,
        isSpam: Boolean
    ): Boolean {
        val deviceCountryCode = Locale.getDefault().country

        val host = if (EU_COUNTRIES.contains(deviceCountryCode.uppercase())) {
            TRUE_CALLER_REPORT_API_URL_EU
        } else {
            TRUE_CALLER_REPORT_API_URL_NONEU
        }


        val requestBodyJson = JSONArray().put(
            JSONObject().apply {
                put("value", phone)
                put("label", comment.take(10))
                put("comment", comment)
                put("rule", if (isSpam) "BLACKLIST" else "WHITELIST")
                put("type", "OTHER")
                put("source", "detailView")
            }
        ).toString()

        val requestBody = requestBodyJson.toRequestBody("application/json; charset=UTF-8".toMediaType())

        val request = Request.Builder()
            .url(host)
            .put(requestBody)
            .header("Authorization", "Bearer $TRUECALLER_API_KEY")
            .header("Content-Type", "application/json; charset=UTF-8")
            .header("Connection", "Keep-Alive")
            .header("Host", "filter-store4-eu.truecaller.com")
            .header("User-Agent", "Truecaller/9.00.3 (Android;10)")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return false
            val json = JSONObject(responseBody)
            json.has("data")
        } catch (e: Exception) {
            false
        }
    }

    fun checkTellowsSpamApi(number: String, country: String): Boolean {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(TELLOWS_API_URL)
            .addPathSegments("basic/num/$number")
            .addQueryParameter("xml", "1")
            .addQueryParameter("partner", "androidapp")
            .addQueryParameter("apikey", TELLOWS_API_KEY)
            .addQueryParameter("overridecountryfilter", "1")
            .addQueryParameter("country", country)
            .addQueryParameter("showcomments", "50")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Connection", "Keep-Alive")
            .header("Host", TELLOWS_API_URL)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 6.0; I14 Pro Max Build/MRA58K)")
            .build()

        return try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return false

            val bodyString = response.body?.string() ?: return false

            val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(bodyString.byteInputStream())

            val scoreNode = xml.getElementsByTagName("score").item(0)
            val score = scoreNode?.textContent?.toIntOrNull() ?: return false

            // Tellows scores: 1 (safe) to 9 (very dangerous)
            score >= 7
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sends a report to Tellows about a phone number, submitting a comment and associated metadata.
     *
     * This method builds a POST request to the Tellows API with form data including the phone number,
     * comment, complaint type, user type, and score.
     *
     * @param phone The phone number to report (without country code prefix, if already localized).
     * @param comment A description of the issue or behavior associated with the number.
     * @param isSpam
     * @param lang Language and country code, e.g. "es".
     * @return `true` if the report was accepted; `false` otherwise.
     */
    fun reportToTellows(
        phone: String,
        comment: String,
        isSpam: Boolean,
        lang: String = "es",
    ): Boolean {
        val userScore = if (isSpam) 9 else 1
        val complainTypeId = if (isSpam) 5 else 2 // 5 is aggressive advertising and 2 is reliable number

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(TELLOWS_API_URL)
            .addPathSegments("basic/num/$phone")
            .addQueryParameter("xml", "1")
            .addQueryParameter("partner", "androidapp")
            .addQueryParameter("apikey", TELLOWS_API_KEY)
            .addQueryParameter("createcomment", "1")
            .addQueryParameter("country", lang)
            .addQueryParameter("lang", lang)
            .addQueryParameter("user_auth", "")
            .addQueryParameter("user_email", "")
            .build()

        val formBody = FormBody.Builder()
            .add("caller", phone)
            .add("comment", comment)
            .add("complain_type_id", complainTypeId.toString())
            .add("user", "Android")
            .add("userscore", userScore.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .header("Accept-Encoding", "gzip")
            .header("Connection", "Keep-Alive")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Host", TELLOWS_API_URL)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 6.0; I14 Pro Max Build/MRA58K)")
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return false

            // Check for success in JSON response
            val json = JSONObject(responseBody)
            json.optBoolean("success", false)
        } catch (e: Exception) {
            false
        }
    }

}