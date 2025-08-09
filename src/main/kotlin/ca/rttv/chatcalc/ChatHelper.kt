package ca.rttv.chatcalc

import debugSend
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.player
import me.ancientri.rimelib.util.text.text
import java.util.function.Consumer

object ChatHelper {
	fun getSection(input: String, cursor: Int) = input.substring(getStartOfSection(input, cursor), getEndOfSection(input, cursor))

	fun replaceSection(input: String, cursor: Int, replacement: String, setMethod: Consumer<String>): Boolean {
		//region debug stuff
		player?.debugSend("--- Replacing ---".text(ColorPalette.ACCENT))
		player?.debugSend {
			"Input: " colored ColorPalette.TEXT
			input colored ColorPalette.ACCENT
		}
		player?.debugSend {
			"Cursor: " colored ColorPalette.TEXT
			cursor.toString() colored ColorPalette.ACCENT
		}
		player?.debugSend {
			"Replacement: " colored ColorPalette.TEXT
			replacement colored ColorPalette.ACCENT
		} //endregion
		val start = getStartOfSection(input, cursor)
		//region debug stuff
		player?.debugSend {
			"Start: " colored ColorPalette.TEXT
			start.toString() colored ColorPalette.ACCENT
		} //endregion
		val end = getEndOfSection(input, cursor)
		//region debug stuff
		player?.debugSend {
			"End: " colored ColorPalette.TEXT
			end.toString() colored ColorPalette.ACCENT
		} //endregion
		val output = input.take(start) + replacement + input.substring(end)
		//region debug stuff
		player?.debugSend {
			"Output: " colored ColorPalette.TEXT
			output colored ColorPalette.ACCENT
		}
		//endregion
		if (output.length > 256 || input.substring(start, end) == replacement) {
			return false
		}
		setMethod.accept(output)
		return true
	}

	fun addSectionAfterIndex(input: String, cursor: Int, word: String, setMethod: Consumer<String>): Boolean {
		val index = getEndOfSection(input, cursor)
		val output = input.take(index) + word + input.substring(index)
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
