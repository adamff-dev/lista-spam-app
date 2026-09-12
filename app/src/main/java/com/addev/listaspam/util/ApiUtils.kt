package com.addev.listaspam.util

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
import javax.xml.parsers.DocumentBuilderFactory


object ApiUtils {
    private const val SCRAPER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.6533.103 Mobile Safari/537.36"

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
            .header("User-Agent", SCRAPER_USER_AGENT)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful && directory.isSpam(response.body?.string().orEmpty())
            }
        } catch (_: Exception) {
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