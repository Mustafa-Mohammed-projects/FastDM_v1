package com.fastdm.app.core

import android.net.Uri
import android.webkit.MimeTypeMap
import java.util.Locale

const val DEFAULT_UA =
    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
const val SEARCH_URL = "https://duckduckgo.com/?q="

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024
        i++
    }
    return if (i == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", v, units[i])
}

fun formatSpeed(bytesPerSecond: Long): String = formatBytes(bytesPerSecond) + "/s"

fun formatEta(seconds: Long): String {
    if (seconds < 0) return "--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun extractUrl(text: String): String? = Regex("https?://[^\\s]+").find(text)?.value

fun normalizeInput(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("about:") || t.startsWith("file://")) return t
    val looksLikeUrl = !t.contains(' ') && t.contains('.')
    return if (looksLikeUrl) "https://$t" else SEARCH_URL + Uri.encode(t)
}

fun displayUrl(url: String): String {
    if (url.isBlank()) return ""
    val host = Uri.parse(url).host?.removePrefix("www.")
    return host ?: url
}

fun fileNameFromDisposition(cd: String?): String? {
    if (cd.isNullOrBlank()) return null
    Regex("filename\\*\\s*=\\s*[^']*''([^;]+)", RegexOption.IGNORE_CASE).find(cd)?.let {
        return Uri.decode(it.groupValues[1].trim().trim('"'))
    }
    Regex("filename\\s*=\\s*\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(cd)?.let {
        return it.groupValues[1].trim()
    }
    return null
}

fun nameFromUrl(url: String): String {
    val seg = Uri.parse(url).lastPathSegment
    return if (seg.isNullOrBlank()) "download" else Uri.decode(seg)
}

fun sanitizeFileName(name: String): String {
    val s = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().trim('.')
    return s.ifBlank { "download" }.take(150)
}

fun ensureExtension(name: String, mime: String): String {
    if (name.contains('.') && !name.endsWith('.')) return name
    val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
    return if (ext != null) "$name.$ext" else name
}

enum class FileKind { VIDEO, AUDIO, IMAGE, ARCHIVE, DOCUMENT, APK, OTHER }

fun fileKind(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
    "mp4", "mkv", "webm", "avi", "mov", "3gp", "flv", "m4v", "ts" -> FileKind.VIDEO
    "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus" -> FileKind.AUDIO
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "heic" -> FileKind.IMAGE
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> FileKind.ARCHIVE
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "epub" -> FileKind.DOCUMENT
    "apk", "xapk", "apks" -> FileKind.APK
    else -> FileKind.OTHER
}
