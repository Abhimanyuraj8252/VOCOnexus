package com.voconexus.app.core.utils

object FastTextHelpers {
    fun fastWordCount(text: String): Int {
        if (text.isBlank()) return 0
        var count = 0
        var isWord = false
        for (i in text.indices) {
            val ch = text[i]
            if (ch.isWhitespace()) {
                isWord = false
            } else if (!isWord) {
                count++
                isWord = true
            }
        }
        return count
    }
}
