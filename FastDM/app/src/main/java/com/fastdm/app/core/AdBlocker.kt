package com.fastdm.app.core

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Host-based ad / tracker blocker used by the built-in browser. */
object AdBlocker {
    private val DEFAULT: Set<String> = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "pagead2.googlesyndication.com", "googletagservices.com", "googletagmanager.com",
        "google-analytics.com", "analytics.google.com", "adsense.google.com", "2mdn.net",
        "adnxs.com", "adsrvr.org", "adform.net", "advertising.com", "criteo.com", "criteo.net",
        "taboola.com", "outbrain.com", "revcontent.com", "mgid.com", "zedo.com", "casalemedia.com",
        "pubmatic.com", "openx.net", "rubiconproject.com", "smartadserver.com", "contextweb.com",
        "moatads.com", "scorecardresearch.com", "quantserve.com", "chartbeat.com", "hotjar.com",
        "mixpanel.com", "segment.io", "amplitude.com", "branch.io", "appsflyer.com", "adjust.com",
        "kochava.com", "flurry.com", "mopub.com", "applovin.com", "unity3d.com", "unityads.unity3d.com",
        "vungle.com", "chartboost.com", "inmobi.com", "startapp.com", "admob.com", "adcolony.com",
        "ironsrc.com", "supersonicads.com", "fyber.com", "tapjoy.com", "popads.net", "popcash.net",
        "propellerads.com", "adsterra.com", "exoclick.com", "juicyads.com", "trafficjunky.com",
        "clickadu.com", "hilltopads.net", "adcash.com", "bidvertiser.com", "yllix.com",
        "mc.yandex.ru", "an.yandex.ru", "ads.yahoo.com", "advertising.yahoo.com", "ads.twitter.com",
        "ads-api.twitter.com", "analytics.twitter.com", "ads.linkedin.com", "px.ads.linkedin.com",
        "facebook.net", "connect.facebook.net", "pixel.facebook.com", "ads.tiktok.com", "analytics.tiktok.com",
        "amazon-adsystem.com", "adsymptotic.com", "bluekai.com", "demdex.net", "everesttech.net",
        "krxd.net", "rlcdn.com", "turn.com", "tapad.com", "adroll.com", "mathtag.com", "yieldmo.com",
        "sharethrough.com", "teads.tv", "tremorhub.com", "spotxchange.com", "lijit.com", "33across.com",
        "sovrn.com", "media.net", "bidswitch.net", "gumgum.com", "adition.com", "nativeads.com",
        "ad.gt", "adskeeper.com", "lentainform.com", "ero-advertising.com", "a-ads.com"
    )

    @Volatile
    private var hosts: Set<String> = DEFAULT

    @Volatile
    var count: Int = DEFAULT.size
        private set

    private val patterns = listOf("/pagead/", "/adserver/")

    fun isBlocked(url: String): Boolean {
        val host = Uri.parse(url).host?.lowercase() ?: return false
        var h = host
        val set = hosts
        while (true) {
            if (h in set) return true
            val i = h.indexOf('.')
            if (i < 0) break
            h = h.substring(i + 1)
        }
        return patterns.any { url.contains(it) }
    }

    fun load(dir: File) {
        val f = File(dir, "ad_hosts.txt")
        if (!f.exists()) return
        try {
            val set = HashSet<String>(DEFAULT)
            f.forEachLine { line -> if (line.isNotBlank()) set.add(line.trim()) }
            hosts = set
            count = set.size
        } catch (_: Exception) {
        }
    }

    suspend fun updateFromRemote(client: OkHttpClient, dir: File): Int = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
            .build()
        val set = HashSet<String>(DEFAULT)
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val src = resp.body!!.source()
            while (true) {
                val line = src.readUtf8Line() ?: break
                val t = line.substringBefore('#').trim()
                if (t.isEmpty()) continue
                val parts = t.split(Regex("\\s+"))
                if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                    val d = parts[1].lowercase()
                    if (d != "localhost" && d.contains('.')) set.add(d)
                }
            }
        }
        File(dir, "ad_hosts.txt").writeText(set.joinToString("\n"))
        hosts = set
        count = set.size
        set.size
    }
}
