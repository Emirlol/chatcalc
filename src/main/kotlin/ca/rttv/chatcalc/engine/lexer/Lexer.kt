package ca.rttv.chatcalc.engine.lexer

import ca.rttv.chatcalc.engine.LexerError
import me.ancientri.rimelib.util.color.ColorPalette
import net.minecraft.screen.ScreenTexts

class Lexer(source: String) {
	private val source = fixParenthesis(source)
	private val tokens = mutableListOf<Token>()
	private var start = 0
	private var current = 0

	/**
	 * Scans the entire input string and returns a list of tokens and the pre-processed source string.
	 */
	fun scanTokens(): Pair<List<Token>, String> {
		while (!isAtEnd()) {
			start = current
			scanToken()
		}
		tokens += Token(TokenType.EOF, "", range = source.length until source.length)
		return tokens to source
	}

	fun isAtEnd() = current >= source.length

	private fun scanToken() {
		when (val c = advance()) {
			' ', '\r', '\t', '\n' -> {
				// Ignore whitespace
			}

			'(' -> addToken(TokenType.LPAREN)
			')' -> addToken(TokenType.RPAREN)
			'[' -> addToken(TokenType.LBRACKET)
			']' -> addToken(TokenType.RBRACKET)
			',' -> addToken(TokenType.COMMA)
			';' -> addToken(TokenType.SEMICOLON)
			'+' -> addToken(TokenType.PLUS)
			'-' -> addToken(if (match('>')) TokenType.ARROW else TokenType.MINUS)
			'→' -> addToken(TokenType.ARROW) // Add the unicode arrow too, because why not
			'^' -> addToken(TokenType.HAT)
			'×', '*' -> addToken(TokenType.STAR) // The same goes for the unicode multiplication sign, I like it more than asterisk
			'/' -> addToken(TokenType.SLASH)
			'%' -> addToken(TokenType.PERCENT)
			'&' -> addToken(if (match('&')) TokenType.AMPERSAND_AMPERSAND else TokenType.AMPERSAND)
			'|' -> addToken(if (match('|')) TokenType.PIPE_PIPE else TokenType.PIPE)
			'!' -> addToken(if (match('=')) TokenType.BANG_EQUAL else TokenType.BANG)
			'=' -> addToken(if (match('=')) TokenType.EQUAL_EQUAL else TokenType.EQUAL)
			'≠' -> addToken(TokenType.BANG_EQUAL)
			'<' -> addToken(if (match('=')) TokenType.LESS_EQUAL else TokenType.LESS)
			'>' -> addToken(if (match('=')) TokenType.GREATER_EQUAL else TokenType.GREATER)
			'≤' -> addToken(TokenType.LESS_EQUAL)
			'≥' -> addToken(TokenType.GREATER_EQUAL)
			'~' -> addToken(TokenType.TILDE)
			':' -> addToken(TokenType.COLON)
			'.' -> {
				if (match('*') || match('×')) addToken(TokenType.DOT_STAR)
				else if (match('/')) addToken(TokenType.DOT_SLASH)
				else if (match('^')) addToken(TokenType.DOT_HAT)
				else if (peek().isDigit()) number() // Allows omitting 0 for decimal numbers like .5
				else throw error("Unexpected character '$c'")
			}

			'0' -> {
				when {
					match('x', 'X') -> hexNumber()
					match('b', 'B') -> binaryNumber()
					match('o', 'O') -> octalNumber()
					else -> {
						// It was just the number 0, we need to back up one char to use the standard number() logic
						current--
						number()
					}
				}
			}

			else -> when {
				c.isDigit() -> number()
				c.isLetter() || c == '_' -> identifier()

				else -> throw error("Unexpected character '$c'")
			}
		}
	}

	// The final message will be something like: "Unexpected character 'x' at position 5: 3+5*x<--[HERE]"
	private fun error(message: String): LexerError = LexerError {
		"$message at position $current:" colored ColorPalette.ERROR
		+ScreenTexts.SPACE
		source.take(current) colored ColorPalette.TEXT
		"<--[HERE]" colored ColorPalette.ERROR
	}

	private fun hexNumber() {
		while (peek().isDigit() || peek() in 'a'..'f' || peek() in 'A'..'F') advance()
		val value = try {
			source.substring(start + 2, current).toBigInteger(16) // We can't convert to Long since a Long has a lower max value than Double, which is our final goal
		} catch (e: NumberFormatException) {
			throw error("Invalid hexadecimal number")
		}
		addToken(TokenType.NUMBER, value.toDouble())
	}

	private fun binaryNumber() {
		while (peek() == '0' || peek() == '1') advance()
		val value = try {
			source.substring(start + 2, current).toBigInteger(2)
		} catch (e: NumberFormatException) {
			throw error("Invalid binary number")
		}
		addToken(TokenType.NUMBER, value.toDouble())
	}

	private fun octalNumber() {
		while (peek().isDigit() && peek() in '0'..'7') advance()
		val value = try {
			source.substring(start + 2, current).toBigInteger(8)
		} catch (e: NumberFormatException) {
			throw error("Invalid octal number")
		}
		addToken(TokenType.NUMBER, value.toDouble())
	}

	private fun number() {
		while (peek().isDigit()) advance()
		if (peek() == '.' && peekNext().isDigit()) {
			advance()
			while (peek().isDigit()) advance()
		}
		// Scientific notation
		if (peek() == 'e' || peek() == 'E') {
			advance()
			if (peek() == '+' || peek() == '-') advance()
			if (!peek().isDigit()) throw error("Invalid scientific notation")
			while (peek().isDigit()) advance()
		}
		val value = try {
			source.substring(start, current).toDouble()
		} catch (e: NumberFormatException) {
			throw error("Invalid number format")
		}
		addToken(TokenType.NUMBER, value)
	}

	private fun identifier() {
		// May contain digits after the first character, so it's possible do stuff like f1() f2() f3() instead of being forced to f() g() h()
		while (peek().isLetterOrDigit() || peek() == '_') advance()
		val text = source.substring(start, current)
		val type = KEYWORDS[text] ?: TokenType.IDENTIFIER
		addToken(type, text)
	}

	private fun advance(): Char = source[current++]

	private fun addToken(type: TokenType, literal: Any? = null) {
		val text = source.substring(start, current)
		val range = if (start < current) start until current else start..current // For EOF
		tokens += Token(type, text, literal, range)
	}

	/**
	 * Peeks the next char. If it matches any of the given chars advances the current position and returns true.
	 */
	private fun match(vararg expected: Char): Boolean {
		if (isAtEnd()) return false
		for (char in expected) {
			if (peek() == char) {
				advance()
				return true
			}
		}
		return false
	}

	private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]

	private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]

	companion object {
		val KEYWORDS = mapOf(
			"if" to TokenType.IF,
			"true" to TokenType.TRUE,
			"false" to TokenType.FALSE
		)

		fun fixParenthesis(input: String): String {
			var open = 0
			var missingOpen = 0
			for (char in input) {
				if (char == '(') open++
				else if (char == ')') {
					if (open > 0) open--
					else missingOpen++
				}
			}

			val sb = StringBuilder(input.length + missingOpen + open)
			repeat(missingOpen) { sb.append('(') }
			sb.append(input)
			repeat(open) { sb.append(')') }
			return sb.toString()
		}
	}
}