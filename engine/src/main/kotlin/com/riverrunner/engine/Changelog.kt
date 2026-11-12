package com.riverrunner.engine

/**
 * Reads CHANGELOG.md. Format:
 *
 *     ## 0.4 · 2026-11-11
 *     ### 新增 | Added
 *     - 中文说明 | English text
 *
 * Text before the first `## ` heading is ignored. A line without ` | ` is used for both languages.
 */
object Changelog {
    data class Item(val zh: String, val en: String) {
        fun text(chinese: Boolean) = if (chinese) zh else en
    }

    data class Section(val title: Item, val items: List<Item>)

    data class Release(val version: String, val date: String, val sections: List<Section>)

    private val HEADING = Regex("""^##\s+([0-9][0-9A-Za-z.\-]*)\s*(?:·\s*(.*))?$""")

    private fun split(s: String): Item {
        val i = s.indexOf(" | ")
        return if (i < 0) Item(s.trim(), s.trim()) else Item(s.substring(0, i).trim(), s.substring(i + 3).trim())
    }

    fun parse(text: String): List<Release> {
        val releases = ArrayList<Release>()
        var version: String? = null
        var date = ""
        val sections = ArrayList<Section>()
        var title: Item? = null
        val items = ArrayList<Item>()

        fun closeSection() {
            if (items.isNotEmpty()) sections += Section(title ?: Item("", ""), items.toList())
            items.clear()
            title = null
        }
        fun closeRelease() {
            closeSection()
            version?.let { releases += Release(it, date, sections.toList()) }
            sections.clear()
        }

        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val h = HEADING.find(line)
            when {
                h != null -> {
                    closeRelease()
                    version = h.groupValues[1]
                    date = h.groupValues[2].trim()
                }
                version == null -> {}
                line.startsWith("### ") -> {
                    closeSection()
                    title = split(line.removePrefix("### "))
                }
                line.startsWith("- ") -> items += split(line.removePrefix("- "))
            }
        }
        closeRelease()
        return releases
    }
}
