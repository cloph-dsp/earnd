package com.earnd.app.i18n

import com.earnd.app.R
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lets JVM tests read English text: parses the xml files in res/values and installs resolvers on [Str].
 * Call TestStr.install() in tests that assert on localized text (it also pins the default locale to English).
 */
object TestStr {
    private val strings = HashMap<String, String>()
    private val plurals = HashMap<String, Map<String, String>>()
    private val idToName: Map<Int, String> by lazy {
        R.string::class.java.fields.associate { it.getInt(null) to it.name } +
            R.plurals::class.java.fields.associate { it.getInt(null) to it.name }
    }

    private fun unescape(s: String): String =
        s.trim().removeSurrounding("\"").replace("\\'", "'").replace("\\\"", "\"").replace("\n", "\n").replace("\\@", "@")

    private fun load() {
        if (strings.isNotEmpty()) return
        val dir = listOf(File("src/main/res/values"), File("app/src/main/res/values")).first { it.isDirectory }
        dir.listFiles { f -> f.extension == "xml" }!!.forEach { f ->
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
            val root = doc.documentElement
            for (i in 0 until root.childNodes.length) {
                val n = root.childNodes.item(i) as? Element ?: continue
                when (n.tagName) {
                    "string" -> strings[n.getAttribute("name")] = unescape(n.textContent)
                    "plurals" -> plurals[n.getAttribute("name")] = (0 until n.childNodes.length)
                        .mapNotNull { n.childNodes.item(it) as? Element }
                        .associate { it.getAttribute("quantity") to unescape(it.textContent) }
                }
            }
        }
    }

    fun install() {
        Locale.setDefault(Locale.ENGLISH)
        load()
        Str.testResolver = { id, args -> String.format(Locale.ENGLISH, strings.getValue(idToName.getValue(id)), *args) }
        Str.testPluralResolver = { id, n, args ->
            val forms = plurals.getValue(idToName.getValue(id))
            String.format(Locale.ENGLISH, forms[if (n == 1) "one" else "other"] ?: forms.getValue("other"), n, *args)
        }
    }
}
