package dev.voqal.assistant.processing

import com.intellij.openapi.util.TextRange
import io.vertx.core.json.JsonObject
import java.util.regex.Pattern

object TextSearcher {

    fun checkForVuiInteraction(type: String, responseCode: String): Boolean {
        try {
            val json = JsonObject(responseCode)
            if (json.getBoolean(type, false)) {
                return true
            }
        } catch (_: Exception) {
        }
        try {
            val extractedCode = CodeExtractor.extractCodeBlock(responseCode)
            val json = JsonObject(extractedCode)
            if (json.getBoolean(type, false)) {
                return true
            }
        } catch (_: Exception) {
        }

        //try stripping leading and trailing backticks
        if (responseCode.startsWith("`") && responseCode.endsWith("`")) {
            try {
                val trimmed = responseCode.trim('`')
                val json = JsonObject(trimmed)
                if (json.getBoolean(type, false)) {
                    return true
                }
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun findText(needle: String, haystack: String, startIndex: Int = 0, lookForward: Boolean = true): TextRange? {
        if (lookForward) {
            val index = haystack.indexOf(needle, startIndex)
            if (index != -1) {
                return TextRange(index, index + needle.length)
            }

            //try ignoring all spacing differences
            val needlePattern = needle
                .split("\\s+".toRegex())
                .joinToString("\\s+") { Regex.escape(it) }
            val pattern = Pattern.compile(needlePattern)
            val matcher = pattern.matcher(haystack.substring(startIndex))
            if (matcher.find()) {
                //this is a bit greedy so make sure the start with the same amount of newlines
                val textRange = TextRange(matcher.start() + startIndex, matcher.end() + startIndex)
                val text = haystack.substring(textRange.startOffset, textRange.endOffset)
                val startNewlines = text.takeWhile { it == '\n' }.length
                val needleNewlines = needle.takeWhile { it == '\n' }.length
                //remove leading newlines if not the same
                if (startNewlines != needleNewlines) {
                    val newStart = text.dropWhile { it == '\n' }
                    return TextRange(textRange.startOffset + (text.length - newStart.length), textRange.endOffset)
                }
                return textRange
            }
        } else {
            val reverseHaystack = haystack.reversed()
            val reverseNeedle = needle.reversed()
            val adjustedStartIndex = haystack.length - startIndex - 1

            val index = reverseHaystack.indexOf(reverseNeedle, adjustedStartIndex)
            if (index != -1) {
                return TextRange(haystack.length - index - reverseNeedle.length, haystack.length - index)
            }

            //try ignoring all spacing differences
            val needlePattern = reverseNeedle
                .split("\\s+".toRegex())
                .joinToString("\\s+") { Regex.escape(it) }
            val pattern = Pattern.compile(needlePattern)
            val matcher = pattern.matcher(reverseHaystack.substring(adjustedStartIndex))
            if (matcher.find()) {
                //this is a bit greedy so make sure the start with the same amount of newlines
                val reverseStart = matcher.start() + adjustedStartIndex
                val reverseEnd = matcher.end() + adjustedStartIndex
                val startOffset = haystack.length - reverseEnd
                val endOffset = haystack.length - reverseStart
                val textRange = TextRange(startOffset, endOffset)
                val text = haystack.substring(textRange.startOffset, textRange.endOffset)
                val startNewlines = text.takeWhile { it == '\n' }.length
                val needleNewlines = needle.takeWhile { it == '\n' }.length
                //remove leading newlines if not the same
                if (startNewlines != needleNewlines) {
                    val newStart = text.dropWhile { it == '\n' }
                    return TextRange(textRange.startOffset + (text.length - newStart.length), textRange.endOffset)
                }
                return textRange
            }
        }

        return null
    }
}
