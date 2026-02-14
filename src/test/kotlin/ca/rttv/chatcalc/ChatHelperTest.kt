package ca.rttv.chatcalc

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class ChatHelperTest {
	@Test
	fun `getSpaceDelimitedSection returns word under cursor`() {
		val input = "hello world again"
		val section = ChatHelper.getSpaceDelimitedSection(input, cursor = 8)
		section.text shouldBe "world"
	}

	@Test
	fun `getSection prefers braces around cursor`() {
		val input = "say { 1+2 } now"
		val section = ChatHelper.getSection(input, cursor = 7)
		section.text shouldBe "1+2"
	}

	@Test
	fun `getSection falls back to whitespace when no braces`() {
		val input = "abc def ghi"
		val section = ChatHelper.getSection(input, cursor = 5)
		section.text shouldBe "def"
	}

	@Test
	fun `getSection handles cursor at end with open brace`() {
		val input = "prefix {2+2"
		val section = ChatHelper.getSection(input, cursor = input.length)
		section.text shouldBe "2+2"
	}

	@Test
	fun `replaceSection swaps selected section`() {
		val input = "say {1+2}"
		val section = ChatHelper.getSection(input, cursor = 6)
		var output: String? = null
		val replaced = ChatHelper.replaceSection(section, "3") { output = it }

		replaced shouldBe true
		output shouldBe "say {3}"
	}

	@Test
	fun `appendAfterSection inserts text after selected section`() {
		val input = "say {1+2}"
		val section = ChatHelper.getSection(input, cursor = 6)
		var output: String? = null
		val appended = ChatHelper.appendAfterSection(section, "=3") { output = it }

		appended shouldBe true
		output shouldBe "say {1+2=3}"
	}

	@Test
	fun `replaceSection enforces chat length limit`() {
		val input = "x"
		val section = Section(0..0, input)
		var called = false
		val ok = ChatHelper.replaceSection(section, "a".repeat(257)) { called = true }
		ok shouldBe false
		called shouldBe false
	}
}
