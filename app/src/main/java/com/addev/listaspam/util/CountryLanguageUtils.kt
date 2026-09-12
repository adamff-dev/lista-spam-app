package com.addev.listaspam.util

import android.content.Context
import java.util.Locale
import android.telephony.TelephonyManager
import com.addev.listaspam.R

object CountryLanguageUtils {
    fun getSimCountry(context: Context): String {
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simCountry = telephonyManager?.simCountryIso?.takeIf { it.isNotEmpty() }
        if (!simCountry.isNullOrEmpty()) return simCountry

        val networkCountry = telephonyManager?.networkCountryIso?.takeIf { it.isNotEmpty() }
        if (!networkCountry.isNullOrEmpty()) return networkCountry

        val localeCountry = Locale.getDefault().country.takeIf { it.isNotEmpty() }
        return localeCountry ?: "us"
    }

    fun setTellowsCountry(context: Context) {
        if (getTellowsApiCountry(context) != null) return

        val simCountry = getSimCountry(context)?.lowercase()
        val systemCountry = Locale.getDefault().country.lowercase()
        val supportedCountries =
            context.resources.getStringArray(R.array.entryvalues_region_preference).toSet()

        val finalCountry = when {
            simCountry != null && supportedCountries.contains(simCountry) -> simCountry
            supportedCountries.contains(systemCountry) -> systemCountry
            else -> "us"
        }
        setTellowsApiCountry(context, finalCountry)
    }

    fun setListaSpamScraperCountry(context: Context) {
        if (hasListaSpamScraperCountry(context)) return

        val simCountry = getSimCountry(context).uppercase()
        val systemCountry = Locale.getDefault().country.uppercase()
        val supportedCountries =
            context.resources.getStringArray(R.array.listaspam_scraper_country_values).toSet()

        val finalCountry = when {
            supportedCountries.contains(simCountry) -> simCountry
            supportedCountries.contains(systemCountry) -> systemCountry
            else -> "OTHER"
        }
        setListaSpamScraperCountry(context, finalCountry)
    }

    fun setTruecallerCountry(context: Context) {
        if (getTruecallerApiCountry(context) != null) return

        val simCountry = getSimCountry(context)?.uppercase()
        val systemCountry = Locale.getDefault().country.uppercase()
        val supportedCountries =
            context.resources.getStringArray(R.array.truecaller_region_code).toSet()

        val finalCountry = when {
            simCountry != null && supportedCountries.contains(simCountry) -> simCountry
            supportedCountries.contains(systemCountry) -> systemCountry
            else -> "US"
        }
        setTruecallerApiCountry(context, finalCountry)
    }
}
