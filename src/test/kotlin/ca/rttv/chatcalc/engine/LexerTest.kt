package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.lexer.TokenType
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
internal class LexerTest {
	@TestFactory
	fun parentheses() = mathTests {
		"(1+2" shouldFixTo "(1+2)"
		"(3+(3+5" shouldFixTo "(3+(3+5))"
		")1+2(" shouldFixTo "()1+2()"
		"((1+2)" shouldFixTo "((1+2))"
		"4+5)" shouldFixTo "(4+5)"
		"((1+3)+((1+2)" shouldFixTo "((1+3)+((1+2)))"
		"f(5+2" shouldFixTo "f(5+2)"
	}

	@TestFactory
	fun lexer() = mathTests {
		"1+2" shouldLexTo listOf(TokenType.NUMBER, TokenType.PLUS, TokenType.NUMBER)
		"1+2*3" shouldLexTo listOf(TokenType.NUMBER, TokenType.PLUS, TokenType.NUMBER, TokenType.STAR, TokenType.NUMBER)
		"1+2*3/4" shouldLexTo listOf(TokenType.NUMBER, TokenType.PLUS, TokenType.NUMBER, TokenType.STAR, TokenType.NUMBER, TokenType.SLASH, TokenType.NUMBER)
		"f(g=5".shouldFail()
		"f(g)=5" shouldLexTo listOf(
			TokenType.IDENTIFIER,
			TokenType.LPAREN,
			TokenType.IDENTIFIER,
			TokenType.RPAREN,
			TokenType.EQUAL,
			TokenType.NUMBER,
		)
		"g(a,b)=a*b" shouldLexTo listOf(
			TokenType.IDENTIFIER,
			TokenType.LPAREN,
			TokenType.IDENTIFIER,
			TokenType.COMMA,
			TokenType.IDENTIFIER,
			TokenType.RPAREN,
			TokenType.EQUAL,
			TokenType.IDENTIFIER,
			TokenType.STAR,
			TokenType.IDENTIFIER,
		)
	}
}