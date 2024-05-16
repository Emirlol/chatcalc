package ca.rttv.chatcalc

import net.minecraft.client.gui.widget.TextFieldWidget

object ChatHelper {
    fun getSection(input: String, cursor: Int): String {
        return input.substring(getStartOfSection(input, cursor), getEndOfSection(input, cursor))
    }

    fun replaceSection(field: TextFieldWidget, replacement: String): Boolean {
        val input = field.text
        val cursor = field.cursor
        val start = getStartOfSection(input, cursor)
        val end = getEndOfSection(input, cursor)
        val output = input.substring(0, start) + replacement + input.substring(end)
        if (output.length > 256 || input.substring(start, end) == replacement) {
            return false
        }
        field.text = output
        return true
    }

    fun addSectionAfterIndex(field: TextFieldWidget, word: String): Boolean {
        val input = field.text
        val index = getEndOfSection(input, field.cursor)
        val output = input.substring(0, index) + word + input.substring(index)
        if (output.length > 256) {
            return false
        }
        field.text = output
        return true
    }

    fun getStartOfSection(input: String, cursor: Int): Int {
        if (cursor == 0) {
            return 0
        }
        if (input[cursor - 1] == ' ') {
            return cursor
        }
        for (i in cursor - 1 downTo 1) {
            if (input[i - 1] == ' ') {
                return i
            }
        }
        return 0
    }

    fun getEndOfSection(input: String, cursor: Int): Int {
        if (cursor == input.length - 1) {
            return cursor
        }
        for (i in cursor until input.length) {
            if (input[i] == ' ') {
                return i
            }
        }
        return input.length
    }
}
