package com.fastdm.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists the download list as a small JSON file in app-private storage. */
class DownloadStore(context: Context) {
    private val file = File(context.filesDir, "downloads.json")

    fun load(): List<DownloadItem> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(items: List<DownloadItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun toJson(i: DownloadItem) = JSONObject().apply {
        put("id", i.id)
        put("url", i.url)
        put("fileName", i.fileName)
        put("mime", i.mime)
        put("total", i.totalBytes)
        put("status", i.status.name)
        put("resumable", i.resumable)
        put("created", i.createdAt)
        put("error", i.error ?: JSONObject.NULL)
        put("savedUri", i.savedUri ?: JSONObject.NULL)
        put("maxSegments", i.maxSegments)
        put("ua", i.userAgent)
        put("referer", i.referer)
        put("cookie", i.cookie)
        put("segments", JSONArray().apply {
            i.segments.forEach { s ->
                put(JSONObject().put("s", s.start).put("e", s.end).put("d", s.downloaded))
            }
        })
    }

    private fun fromJson(o: JSONObject): DownloadItem {
        val segs = o.optJSONArray("segments") ?: JSONArray()
        return DownloadItem(
            id = o.getString("id"),
            url = o.getString("url"),
            fileName = o.optString("fileName"),
            mime = o.optString("mime"),
            totalBytes = o.optLong("total", -1),
            status = runCatching { Status.valueOf(o.getString("status")) }.getOrDefault(Status.PAUSED),
            segments = (0 until segs.length()).map {
                val s = segs.getJSONObject(it)
                Segment(s.getLong("s"), s.getLong("e"), s.getLong("d"))
            },
            resumable = o.optBoolean("resumable"),
            createdAt = o.optLong("created"),
            error = if (o.isNull("error")) null else o.optString("error"),
            savedUri = if (o.isNull("savedUri")) null else o.optString("savedUri"),
            maxSegments = o.optInt("maxSegments", 8),
            userAgent = o.optString("ua", DEFAULT_UA),
            referer = o.optString("referer"),
            cookie = o.optString("cookie")
        )
    }
}
