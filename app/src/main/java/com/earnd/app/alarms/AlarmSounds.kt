package com.earnd.app.alarms

import android.content.Context
import android.media.RingtoneManager
import java.io.File

/** One selectable alarm sound. [key] is "system:<title>" or "custom:<filename>"; [type] alarm/notification/ringtone/custom. */
data class SoundOption(val key: String, val title: String, val uri: String, val type: String)

/** Sound keys: "default", "system:<title>", "custom:<filename>". Pure parsing/validation plus device listing. */
object AlarmSounds {
    const val DEFAULT = "default"
    const val SUBDIR = "sounds"
    val EXTENSIONS = setOf("mp3", "ogg", "wav", "m4a")

    sealed interface Key {
        object Default : Key
        data class System(val title: String) : Key
        data class Custom(val file: String) : Key
        /** A raw uri that must match a listed sound. */
        data class Raw(val uri: String) : Key
    }

    fun parseKey(raw: String): Key? {
        val v = raw.trim()
        return when {
            v.isEmpty() || v.equals(DEFAULT, true) || v.equals("none", true) -> Key.Default
            v.startsWith("system:") -> v.removePrefix("system:").takeIf { it.isNotBlank() }?.let(Key::System)
            v.startsWith("custom:") -> v.removePrefix("custom:").takeIf { isSafeFileName(it) }?.let(Key::Custom)
            "://" in v -> Key.Raw(v)
            else -> null
        }
    }

    /** Plain file name with an allowed audio extension; no path separators or dot-dot. */
    fun isSafeFileName(name: String): Boolean =
        name.isNotEmpty() && name.length <= 100 && name.none { it == '/' || it == '\\' || it == '\u0000' } &&
            !name.startsWith(".") && name.substringAfterLast('.', "").lowercase() in EXTENSIONS

    /**
     * Resolves a user-supplied sound to the uri to store (null = default). Throws [IllegalArgumentException]
     * when the key is malformed or not in [options].
     */
    fun resolve(raw: String, options: List<SoundOption>): String? {
        val key = parseKey(raw) ?: throw IllegalArgumentException(
            "sound must be default, system:<title>, custom:<file> or a listed uri (see sounds.list)")
        if (key == Key.Default) return null
        return when (key) {
            Key.Default -> null
            is Key.System -> options.firstOrNull { it.key.startsWith("system:") && it.key.equals("system:" + key.title, true) }?.uri
            is Key.Custom -> options.firstOrNull { it.key == "custom:" + key.file }?.uri
            is Key.Raw -> options.firstOrNull { it.uri == key.uri }?.uri
        } ?: throw IllegalArgumentException("unknown sound: $raw (see sounds.list)")
    }

    /** Display name for a stored uri, or "Default". */
    fun nameOf(uri: String?, options: List<SoundOption>): String =
        if (uri == null) "Default" else options.firstOrNull { it.uri == uri }?.title ?: "Custom"

    /** A stored `file://` uri or absolute path as a File, else null. */
    fun filePath(uri: String): File? = when {
        uri.startsWith("file://") -> File(java.net.URI(uri.replace(" ", "%20")).path)
        uri.startsWith("/") -> File(uri)
        else -> null
    }

    fun customDir(context: Context): File? = context.externalMediaDirs.firstOrNull()?.let { File(it, SUBDIR) }

    fun list(context: Context): List<SoundOption> {
        val out = mutableListOf<SoundOption>()
        val types = listOf(RingtoneManager.TYPE_ALARM to "alarm", RingtoneManager.TYPE_NOTIFICATION to "notification",
            RingtoneManager.TYPE_RINGTONE to "ringtone")
        for ((t, name) in types) runCatching {
            val rm = RingtoneManager(context).apply { setType(t) }
            rm.cursor.use { c ->
                while (c.moveToNext()) {
                    val title = c.getString(RingtoneManager.TITLE_COLUMN_INDEX) ?: continue
                    val uri = rm.getRingtoneUri(c.position)?.toString() ?: continue
                    val key = "system:$title"
                    if (out.none { it.key == key }) out += SoundOption(key, title, uri, name)
                }
            }
        }
        customDir(context)?.listFiles()?.filter { it.isFile && isSafeFileName(it.name) }?.sortedBy { it.name.lowercase() }
            ?.forEach { out += SoundOption("custom:${it.name}", it.name, "file://" + it.absolutePath, "custom") }
        return out
    }
}
