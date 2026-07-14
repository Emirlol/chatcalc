package ca.rttv.chatcalc

import ca.rttv.chatcalc.ChatHelper.getSpaceDelimitedSection
import java.util.function.Consumer

/**
 * Represents a section of text in a chat message.
 * @property range The range of indices in the message that make up this section.
 * @property input The entire message.
 * @property text The text of this section.
 */
interface Section {
	val range: IntRange
	val input: String
	val text: String
		get() = input.substring(range)

	data class Braced(override val range: IntRange, override val input: String) : Section
	data class SpaceDelimited(override val range: IntRange, override val input: String) : Section
}

object ChatHelper {
	/**
	 * Tries to find a `{ }` pair around the given index.
	 * If there are no pairs, falls back to [getSpaceDelimitedSection].
	 */
	fun getSection(input: String, cursor: Int): Section {
		// Edge-cases
		if (cursor == 0) {
			val rightIndex = input.indexOf('}', cursor)
			if (rightIndex != -1) return Section.Braced(getTrimmedRange(input, 0, rightIndex), input)
		} else if (cursor == input.length) {
			val leftIndex = input.lastIndexOf('{', cursor - 1)
			if (leftIndex != -1) return Section.Braced(getTrimmedRange(input, (leftIndex + 1), input.length), input)
		}

		var start = -1
		var end = -1
		var searchLeft = cursor
		var searchRight = cursor

		do {
			val leftIndex = input.lastIndexOf('{', searchLeft - 1)
			if (leftIndex == -1) break // Can't be a pair if there's no left brace
			val rightIndex = input.indexOf('}', searchRight)
			if (rightIndex == -1) break
			start = leftIndex + 1
			end = rightIndex
			searchLeft = leftIndex
			searchRight = rightIndex + 1
		} while (true)

		return if (start == -1) getSpaceDelimitedSection(input, cursor) // No braces found, fall back to whitespace method
		else Section.Braced(getTrimmedRange(input, start, end), input) // Braces found, return the section within
	}

	fun getSpaceDelimitedSection(input: String, cursor: Int): Section {
		val start = getStartOfSection(input, cursor)
		val end = getEndOfSection(input, cursor)
		return Section.SpaceDelimited(start until end, input)
	}

	fun replaceSection(section: Section, replacement: String, setMethod: Consumer<String>): Boolean {
		val output = section.input.take(section.range.first) + replacement + section.input.substring(section.range.last + 1)
		if (output.length > 256 || section.input == replacement) return false
		setMethod.accept(output)
		return true
	}

	fun appendAfterSection(section: Section, word: String, setMethod: Consumer<String>): Boolean {
		val output = section.input.take(section.range.last + 1) + word + section.input.substring(section.range.last + 1)
		if (output.length > 256) return false
		setMethod.accept(output)
		return true
	}

	/**
	 * @return The index + 1 of the first whitespace character before the given index, or 0 if there are no more whitespaces.
	 */
	private fun getStartOfSection(input: String, cursor: Int): Int {
		if (cursor == 0) return 0
		return input.lastIndexOf(' ', cursor - 1) + 1 // This also handles the -1 return value by making it 0.
	}

	/**
	 * @return The index of the first whitespace character after the given index, or the end of the string if there are no more whitespaces.
	 */
	private fun getEndOfSection(input: String, cursor: Int): Int {
		if (cursor == input.length) return input.length
		val index = input.indexOf(' ', cursor)
		return if (index == -1) input.length else index
	}

	/**
	 * Trims whitespace from the given range within the input string.
	 * @return A new [IntRange] with whitespace trimmed from both ends.
	 */
	private fun getTrimmedRange(input: String, start: Int, end: Int): IntRange {
		var newStart = start
		var newEnd = end
		while (newStart < newEnd && input[newStart].isWhitespace()) {
			newStart++
		}
		while (newEnd > newStart && input[newEnd - 1].isWhitespace()) {
			newEnd--
		}
		return newStart..<newEnd
	}
}
