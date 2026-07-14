package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.lexer.Lexer
import ca.rttv.chatcalc.engine.lexer.TokenType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
internal class LexerTest {
	@Test
	fun `fixParenthesis balances inputs`() {
		Lexer.fixParenthesis("(1+2") shouldBe "(1+2)"
		Lexer.fixParenthesis("(3+(3+5") shouldBe "(3+(3+5))"
		Lexer.fixParenthesis(")1+2(") shouldBe "()1+2()"
		Lexer.fixParenthesis("((1+2)") shouldBe "((1+2))"
		Lexer.fixParenthesis("4+5)") shouldBe "(4+5)"
	}

	@Test
	fun `lexes arithmetic and punctuation tokens`() {
		lex("1+2*3/4%5^2!").map { it.type } shouldBe listOf(
			TokenType.NUMBER,
			TokenType.PLUS,
			TokenType.NUMBER,
			TokenType.STAR,
			TokenType.NUMBER,
			TokenType.SLASH,
			TokenType.NUMBER,
			TokenType.PERCENT,
			TokenType.NUMBER,
			TokenType.HAT,
			TokenType.NUMBER,
			TokenType.BANG,
			TokenType.EOF,
		)
	}

	@Test
	fun `lexes keywords unicode operators and compound operators`() {
		lex("if(true≠false&&1≤2||3≥2) x→x.×2 ./ 4 .^ 2").map { it.type } shouldBe listOf(
			TokenType.IF,
			TokenType.LPAREN,
			TokenType.TRUE,
			TokenType.BANG_EQUAL,
			TokenType.FALSE,
			TokenType.AMPERSAND_AMPERSAND,
			TokenType.NUMBER,
			TokenType.LESS_EQUAL,
			TokenType.NUMBER,
			TokenType.PIPE_PIPE,
			TokenType.NUMBER,
			TokenType.GREATER_EQUAL,
			TokenType.NUMBER,
			TokenType.RPAREN,
			TokenType.IDENTIFIER,
			TokenType.ARROW,
			TokenType.IDENTIFIER,
			TokenType.DOT_STAR,
			TokenType.NUMBER,
			TokenType.DOT_SLASH,
			TokenType.NUMBER,
			TokenType.DOT_HAT,
			TokenType.NUMBER,
			TokenType.EOF,
		)
	}

	@Test
	fun `lexes numeric formats with literals`() {
		val tokens = lex("0x10 0b101 0o17 .5 2.5e2")
		tokens.dropLast(1).map { it.type } shouldBe List(5) { TokenType.NUMBER }
		tokens.dropLast(1).map { it.literal as Double } shouldBe listOf(16.0, 5.0, 15.0, 0.5, 250.0)
	}

	@Test
	fun `lexes identifiers and separators`() {
		lex("f(a,b):scalar= a*b").map { it.type } shouldBe listOf(
			TokenType.IDENTIFIER,
			TokenType.LPAREN,
			TokenType.IDENTIFIER,
			TokenType.COMMA,
			TokenType.IDENTIFIER,
			TokenType.RPAREN,
			TokenType.COLON,
			TokenType.IDENTIFIER,
			TokenType.EQUAL,
			TokenType.IDENTIFIER,
			TokenType.STAR,
			TokenType.IDENTIFIER,
			TokenType.EOF,
		)
	}

	@Test
	fun `throws on invalid characters and invalid scientific notation`() {
		shouldThrow<LexerError> { lex("1@2") }
		shouldThrow<LexerError> { lex("1e+") }
	}
}
