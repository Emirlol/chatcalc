package ca.rttv.chatcalc

import java.util.function.Consumer

object ChatHelper {
    fun getSection(input: String, cursor: Int) = input.substring(getStartOfSection(input, cursor), getEndOfSection(input, cursor))

    fun replaceSection(input: String, cursor: Int, replacement: String, setMethod: Consumer<String>): Boolean {
        val start = getStartOfSection(input, cursor)
        val end = getEndOfSection(input, cursor)
        val output = input.substring(0, start) + replacement + input.substring(end)
        if (output.length > 256 || input.substring(start, end) == replacement) {
            return false
        }
        setMethod.accept(output)
        return true
    }

    fun addSectionAfterIndex(input: String, cursor: Int, word: String, setMethod: Consumer<String>): Boolean {
        val index = getEndOfSection(input, cursor)
        val output = input.substring(0, index) + word + input.substring(index)
        if (output.length > 256) {
            return false
        }
        setMethod.accept(output)
        return true
    }

    fun getStartOfSection(input: String, cursor: Int): Int {
        if (cursor == 0 || input[cursor - 1] == ' ') return cursor
        for (i in cursor - 1 downTo 1) {
            if (input[i - 1] == ' ') {
                return i
            }
        }
        return 0
    }

    fun getEndOfSection(input: String, cursor: Int): Int {
        if (cursor == input.length) return cursor
        for (i in cursor until input.length) {
            if (input[i] == ' ') {
                return i
            }
        }
        return input.length
    }
}
