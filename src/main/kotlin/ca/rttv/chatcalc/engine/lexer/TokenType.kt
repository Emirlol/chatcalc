package ca.rttv.chatcalc.engine.lexer

import net.minecraft.util.StringIdentifiable
import net.minecraft.util.StringIdentifiable.EnumCodec

enum class TokenType: StringIdentifiable {
	// Logic/comparison operators
	AMPERSAND, AMPERSAND_AMPERSAND,
	PIPE, PIPE_PIPE,
	EQUAL_EQUAL, BANG_EQUAL,
	TILDE,
	LESS, LESS_EQUAL, GREATER, GREATER_EQUAL,

	COLON,

	ARROW,

	// Numeric Operators
	PLUS, MINUS, STAR, SLASH, HAT, PERCENT, BANG,

	// Parentheses
	LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE,

	// Delimiters
	COMMA, SEMICOLON, EQUAL,

	// Multi-character tokens
	DOT_STAR, DOT_SLASH, DOT_HAT,

	// Literals
	NUMBER, IDENTIFIER,

	// Keywords
	IF, TRUE, FALSE,

	EOF;

	override fun asString() = name

	companion object {
		val CODEC: EnumCodec<TokenType> = StringIdentifiable.createCodec(TokenType::values)
	}
}