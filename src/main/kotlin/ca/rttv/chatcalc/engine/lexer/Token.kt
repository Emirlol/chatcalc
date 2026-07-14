package ca.rttv.chatcalc.engine.lexer

/**
 * A token produced by the lexer.
 * @property type The type of the token.
 * @property lexeme The lexeme of the token.
 * @property literal The literal value of the token, if any. This is only used for literal numbers ([Double]) and identifiers ([String]).
 * @property range The range of the token in the input string.
 */
data class Token(
	val type: TokenType,
	val lexeme: String,
	val literal: Any? = null,
	val range: IntRange = IntRange.EMPTY
)
