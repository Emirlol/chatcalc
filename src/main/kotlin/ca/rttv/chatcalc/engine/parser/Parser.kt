package ca.rttv.chatcalc.engine.parser

import ca.rttv.chatcalc.engine.*
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.lexer.TokenType
import ca.rttv.chatcalc.engine.types.*
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.text.TextBuilder
import net.minecraft.screen.ScreenTexts

/**
 * Parser based on lang.bnf in the same directory.
 */
class Parser(private val tokens: List<Token>, private val source: String) {
	private var current = 0
	private var insideAbsoluteExpressionDepth = 0

	fun parse(): List<Statement> {
		val statements = mutableListOf<Statement>()
		while (!isAtEnd()) statements += statement()
		return statements
	}

	//region Language rules.
	// All functions in this region correspond to a single rule in lang.bnf.

	private fun statement(): Statement {
		when (peek().type) {
			TokenType.IDENTIFIER if lookAhead(1).type == TokenType.EQUAL -> {
				if (lookAhead(2).type == TokenType.EOF) {
					val name = advance() // Identifier
					advance() // Equal
					return Undefine(name)
				}
				return variableAssignment()
			}

			TokenType.IDENTIFIER if isFunctionDefinition() -> return functionDefinition()

			else -> {
				val expr = expression()
				if (match(TokenType.EQUAL)) {
					if (!isAtEnd()) {
						throw error(previous()!!) {
							"Unexpected token after '" colored ColorPalette.ERROR
							"=" colored ColorPalette.TEXT
							"'" colored ColorPalette.ERROR
						}
					}
					return Query(expr)
				}
				return expressionStatement(expr)
			}
		}
	}

	private fun functionDefinition(): FunctionDefinition {
		val name = consume(TokenType.IDENTIFIER) { "Expected function name" colored ColorPalette.ERROR }
		consume(TokenType.LPAREN) {
			"Expected '" colored ColorPalette.ERROR
			"(" colored ColorPalette.TEXT
			"' after the function name" colored ColorPalette.ERROR
		}

		val parameters: List<FunctionParameter>

		if (check(TokenType.RPAREN)) {
			consume(TokenType.RPAREN) {
				"Expected arguments or '" colored ColorPalette.ERROR
				")" colored ColorPalette.TEXT
				"' after '" colored ColorPalette.ERROR
				"(" colored ColorPalette.TEXT
				"'" colored ColorPalette.ERROR
			}
			parameters = emptyList()
		} else {
			parameters = parseParameters()

			consume(TokenType.RPAREN) {
				"Expected closing '" colored ColorPalette.ERROR
				")" colored ColorPalette.TEXT
				"' after parameters" colored ColorPalette.ERROR
			}
		}

		val returnType = parseTypeAnnotation()
		consume(TokenType.EQUAL) {
			"Expected '" colored ColorPalette.ERROR
			"=" colored ColorPalette.TEXT
			"' after function parameters" colored ColorPalette.ERROR
		}
		val body = expression()
		val endToken = previous()!!
		val range = name.range.first..endToken.range.last
		return FunctionDefinition(name, parameters, returnType, body, source.substring(range))
	}

	private fun parseParameters(): List<FunctionParameter> {
		val parameters = mutableListOf<FunctionParameter>()
		do {
			val name = consume(TokenType.IDENTIFIER) { "Expected parameter name" colored ColorPalette.ERROR }
			parameters += FunctionParameter(name.lexeme, parseTypeAnnotation())
		} while (match(TokenType.COMMA))
		return parameters
	}

	private fun parseTypeAnnotation(): TypeConstraint {
		if (!match(TokenType.COLON)) return AnyConstraint // Type annotations are optional

		val constraints = mutableListOf<TypeConstraint>()
		do {
			val typeToken = consume(TokenType.IDENTIFIER) { "Expected type name" colored ColorPalette.ERROR }
			constraints += TypeRegistry[typeToken.lexeme] ?: throw error(typeToken) {
				"Unknown type constraint '" colored ColorPalette.ERROR
				typeToken.lexeme colored ColorPalette.TEXT
				"'" colored ColorPalette.ERROR
			}
		} while (match(TokenType.PIPE))

		if (constraints.size == 1) return constraints.first()
		if (AnyConstraint in constraints) return AnyConstraint

		val optimized = mutableListOf<TypeConstraint>()
		for (constraint in constraints) {
			if (optimized.any { it.isSupersetOf(constraint) }) continue
			optimized.removeAll { constraint.isSupersetOf(it) }
			optimized += constraint // isSupersetOf returns true for equal types, so this constraint gets removed as well.
		}

		if (optimized.size == 1) return optimized.first()
		return UnionTypeConstraint(optimized)
	}

	private fun variableAssignment(): VariableAssignment {
		val name = consume(TokenType.IDENTIFIER) { "Expected variable name" colored ColorPalette.ERROR }
		consume(TokenType.EQUAL) {
			"Expected '" colored ColorPalette.ERROR
			"=" colored ColorPalette.TEXT
			"' after the variable name" colored ColorPalette.ERROR
		}
		val initializer = expression()
		return VariableAssignment(name, initializer)
	}

	private fun expressionStatement(expr: Expression = expression()) = ExpressionStatement(expr)

	private fun expression(): Expression = logicalOr()

	private fun logicalOr(): Expression {
		var expr = logicalAnd()
		while (match(TokenType.PIPE_PIPE)) {
			val operator = previous()!! // We just matched, this can't be null.
			val right = logicalAnd()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun logicalAnd(): Expression {
		var expr = equality()
		while (match(TokenType.AMPERSAND_AMPERSAND)) {
			val operator = previous()!! // We just matched, this can't be null.
			val right = equality()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun equality(): Expression {
		var expr = comparison()
		while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
			val op = previous()!! // We just matched, this can't be null.
			val right = comparison()
			expr = Binary(expr, op, right)
		}
		return expr
	}

	private fun comparison(): Expression {
		var expr = addition()
		// Comparison is non-associative, so we use 'if' instead of 'while' here
		if (match(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL)) {
			val operator = previous()!! // We just matched, this can't be null.
			val right = addition()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun addition(): Expression {
		var expr = multiplication()
		while (match(TokenType.PLUS, TokenType.MINUS)) {
			val operator = previous()!! // We just matched, this can't be null.
			val right = multiplication()
			expr = Binary(expr, operator, right)
		}
		return expr
	}

	private fun multiplication(): Expression {
		var expr = unary()
		while (true) {
			if (peek().type == TokenType.PIPE && insideAbsoluteExpressionDepth > 0) break

			if (match(TokenType.STAR, TokenType.SLASH, TokenType.DOT_STAR, TokenType.DOT_SLASH, TokenType.PERCENT)) {
				val operator = previous()!! // We just matched, this can't be null.
				val right = unary()
				expr = Binary(expr, operator, right)
			} else if (isImplicitMultiplicationStart(peek().type)) {
				// Synthetic * to represent implicit multiplication
				val operator = Token(TokenType.STAR, "*", range = peek().range)
				val right = unary()
				expr = Binary(expr, operator, right)
			} else break
		}
		return expr
	}

	private fun unary(): Expression {
		if (match(TokenType.MINUS, TokenType.PLUS, TokenType.TILDE)) {
			val operator = previous()!! // We just matched, this can't be null.
			val right = unary()
			return Unary(operator, right)
		}
		return exponentiation()
	}

	private fun exponentiation(): Expression {
		val expr = postfix()
		if (match(TokenType.HAT, TokenType.DOT_HAT)) {
			val operator = previous()!! // We just matched, this can't be null.
			val right = unary() // The right side must recurse to unary operators to allow 2^-3.
			return Binary(expr, operator, right)
		}
		return expr
	}

	private fun postfix(): Expression {
		var expr = access()
		while (match(TokenType.BANG)) {
			val operator = previous()!! // We just matched, this can't be null.
			expr = Postfix(expr, operator)
		}
		return expr
	}

	private fun access(): Expression {
		var expr = primary()
		while (true) {
			when (expr) {
				!is Literal if match(TokenType.LPAREN) -> {
					val arguments = arguments()
					consume(TokenType.RPAREN) {
						"Expected '" colored ColorPalette.ERROR
						")" colored ColorPalette.TEXT
						"' after arguments" colored ColorPalette.ERROR
					}
					expr = FunctionCall(expr, arguments)
				}

				!is Literal if match(TokenType.LBRACKET) -> {
					val indices = parseIndices()
					consume(TokenType.RBRACKET) {
						"Expected '" colored ColorPalette.ERROR
						"]" colored ColorPalette.TEXT
						"' after indices" colored ColorPalette.ERROR
					}
					expr = IndexAccess(expr, indices)
				}

				else -> break
			}
		}
		return expr
	}

	private fun parseIndices(): List<Expression> {
		val indices = mutableListOf<Expression>()
		if (!check(TokenType.RBRACKET)) {
			do {
				if (check(TokenType.COLON)) {
					indices += parseSlice(null)
				} else {
					val start = expression()
					indices += if (check(TokenType.COLON)) parseSlice(start) else start
				}
			} while (match(TokenType.COMMA))
		}
		return indices
	}

	private fun parseSlice(start: Expression?): Slice {
		consume(TokenType.COLON) {
			"Expected '" colored ColorPalette.ERROR
			":" colored ColorPalette.TEXT
			"' in slice." colored ColorPalette.ERROR
		}

		val end = if (check(TokenType.COMMA) || check(TokenType.RBRACKET) || check(TokenType.COLON)) {
			null // Empty end -> [1:]
		} else expression()

		var step: Expression? = null
		if (match(TokenType.COLON)) {
			step = if (check(TokenType.COMMA) || check(TokenType.RBRACKET)) null // Empty step -> [1::]
			else expression()
		}

		return Slice(start, end, step)
	}

	private fun primary(): Expression = when {
		isLambda() -> lambda() // Early check so that it's not consumed as an identifier or grouping
		match(TokenType.NUMBER) -> Literal(previous()!!.literal!!)
		match(TokenType.FALSE) -> Literal(false)
		match(TokenType.TRUE) -> Literal(true)
		match(TokenType.IDENTIFIER) -> Variable(previous()!!)
		match(TokenType.LPAREN) -> grouping()
		match(TokenType.PIPE) -> absoluteValue()
		match(TokenType.LBRACKET) -> vectorLiteral()
		match(TokenType.IF) -> conditional()
		else -> throw error {
			"Expected expression" colored ColorPalette.ERROR
		}
	}

	private fun lambda(): Expression {
		val parameters: List<FunctionParameter>
		if (match(TokenType.IDENTIFIER)) {
			// Single parameter without parentheses
			val identifier = previous()!!
			parameters = listOf(FunctionParameter(identifier.lexeme, parseTypeAnnotation()))
		} else {
			consume(TokenType.LPAREN) {
				"Expected '" colored ColorPalette.ERROR
				"(" colored ColorPalette.TEXT
				"' at the start of lambda parameters" colored ColorPalette.ERROR
			}
			parameters = if (check(TokenType.RPAREN)) emptyList() else parseParameters()
			consume(TokenType.RPAREN) {
				"Expected closing '" colored ColorPalette.ERROR
				")" colored ColorPalette.TEXT
				"' after lambda parameters" colored ColorPalette.ERROR
			}
		}
		consume(TokenType.ARROW) {
			"Expected '" colored ColorPalette.ERROR
			"->" colored ColorPalette.TEXT
			"' after lambda parameters" colored ColorPalette.ERROR
		}
		val body = expression()
		return Lambda(parameters, body)
	}

	private fun conditional(): Expression {
		// Keyword already consumed
		consume(TokenType.LPAREN) {
			"Expected '" colored ColorPalette.ERROR
			"(" colored ColorPalette.TEXT
			"' after '" colored ColorPalette.ERROR
			"if" colored ColorPalette.TEXT
			"'" colored ColorPalette.ERROR
		}
		val condition = expression()
		consume(TokenType.COMMA) {
			"Expected '" colored ColorPalette.ERROR
			"," colored ColorPalette.TEXT
			"' after if condition" colored ColorPalette.ERROR
		}
		val ifTrue = expression()
		consume(TokenType.COMMA) {
			"Expected '" colored ColorPalette.ERROR
			"," colored ColorPalette.TEXT
			"' after if-true branch" colored ColorPalette.ERROR
		}
		val ifFalse = expression()
		consume(TokenType.RPAREN) {
			"Expected closing '" colored ColorPalette.ERROR
			")" colored ColorPalette.TEXT
			"' after if expression" colored ColorPalette.ERROR
		}
		return Conditional(condition, ifTrue, ifFalse)
	}

	private fun grouping(): Expression {
		val expr = expression()
		consume(TokenType.RPAREN) {
			"Expected '" colored ColorPalette.ERROR
			")" colored ColorPalette.TEXT
			"' after expression" colored ColorPalette.ERROR
		}
		return Grouping(expr)
	}

	private fun absoluteValue(): Expression {
		insideAbsoluteExpressionDepth++
		val expr = try {
			expression()
		} finally {
			insideAbsoluteExpressionDepth--
		}
		consume(TokenType.PIPE) {
			"Expect closing '" colored ColorPalette.ERROR
			"|" colored ColorPalette.TEXT
			"' after absolute value operator" colored ColorPalette.ERROR
		}
		return AbsoluteValue(expr)
	}

	private fun vectorLiteral(): Expression {
		val elements = mutableListOf<Expression>()
		if (!check(TokenType.RBRACKET)) {
			do {
				elements += expression()
			} while (match(TokenType.COMMA))
		}
		consume(TokenType.RBRACKET) {
			"Expected closing '" colored ColorPalette.ERROR
			"]" colored ColorPalette.TEXT
			"' after vector literal" colored ColorPalette.ERROR
		}
		return Vector(elements)
	}

	private fun arguments(): List<Expression> {
		val args = mutableListOf<Expression>()
		if (!check(TokenType.RPAREN) && !check(TokenType.RBRACKET)) {
			do {
				args += expression()
			} while (match(TokenType.COMMA))
		}
		return args
	}
	//endregion

	//region Utility and error handling

	/**
	 * Checks if the current token is of the given type(s) and consumes it if so.
	 */
	private fun match(vararg types: TokenType): Boolean {
		for (type in types) {
			if (check(type)) {
				advance()
				return true
			}
		}
		return false
	}

	/**
	 *  Checks if the current token is of the given type without consuming it.
	 */
	private fun check(type: TokenType): Boolean {
		if (isAtEnd()) return false
		return peek().type == type
	}

	/**
	 * @return `true` if all tokens have been consumed, `false` otherwise.
	 */
	private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

	/**
	 * @return the current token without consuming it.
	 */
	private fun peek(): Token = tokens[current]

	/**
	 * @return the most recently consumed token, or `null` if none has been consumed yet.
	 */
	private fun previous(): Token? = tokens.getOrNull(current - 1)

	/**
	 * @return a token `k` positions ahead of the current position.
	 */
	private fun lookAhead(k: Int): Token {
		val index = current + k
		if (index >= tokens.size) return tokens.last() // EOF
		return tokens[index]
	}

	/**
	 *  Consumes the current token and returns it.
	 */
	private fun advance(): Token {
		if (!isAtEnd()) current++
		return previous()!!
	}

	/**
	 * Checks if the current token is of the given type and consumes it if so.
	 * @throws ParserError if the current token is not of the given type.
	 */
	private fun consume(type: TokenType, builder: TextBuilder.(Token) -> Unit): Token {
		if (check(type)) return advance()
		throw this.error(builder = builder)
	}

	// The error example given isn't a valid syntax or error, it's just for illustration purposes
	/**
	 * Throws a [ParserError] with the current token given by [peek] with the current position & text until that position included as a suffix.
	 * The final message will be something like: `Expected '(' after identifier at position 5: 3+5*x<--[HERE]`
	 *
	 * For better results, make sure the error message ends without any punctuation or whitespace.
	 */
	private fun error(token: Token = peek(), builder: TextBuilder.(Token) -> Unit) = ParserError(token) {
		builder(it)
		" at position " colored ColorPalette.ERROR
		if (token.range.first == token.range.last) token.range.first.toString() colored ColorPalette.TEXT
		else {
			token.range.first.toString() colored ColorPalette.TEXT
			".." colored ColorPalette.SUBTEXT2
			token.range.last.toString() colored ColorPalette.TEXT
		}
		": " colored ColorPalette.ERROR
		+ScreenTexts.SPACE
		// Range is inclusive end, `take` needs exclusive end so + 1
		source.take(token.range.last + 1) colored ColorPalette.TEXT
		"<--[HERE]" colored ColorPalette.ERROR
	}

	private fun isImplicitMultiplicationStart(type: TokenType): Boolean = when (type) {
		TokenType.IDENTIFIER,
		TokenType.NUMBER,
		TokenType.LPAREN,
		TokenType.LBRACKET,
		TokenType.PIPE,
		TokenType.IF -> true

		else -> false
	}

	/**
	 * Scans ahead to see if the current position is the start of a function definition.
	 * This is quite a loose check of the syntax, it doesn't check for illegal function signatures.
	 *
	 * All it does is validate the start `ident(` and end `)=`, and check if the tokens inside the paren are valid tokens that could be used for function definition.
	 *
	 * Making it strict causes the parsing errors to be thrown from functionCall instead, which makes less sense. For example, `f(x:)=x` might show `) expected after args: f(x<--[HERE]` instead of `Expected type name after ':' at position 4: f(x:<--[HERE]`.
	 */
	private fun isFunctionDefinition(): Boolean {
		if (!check(TokenType.IDENTIFIER)) return false

		var offset = 1 // Skip the function name identifier
		if (lookAhead(offset++).type != TokenType.LPAREN) return false // '('
		var insideTypeAnnotation = false // We have to track this to avoid allowing | in illegal places
		while (true) {
			val type = lookAhead(offset++).type
			if (type == TokenType.RPAREN) break
			if (type == TokenType.EOF) return false // Unclosed parens
			when (type) {
				TokenType.IDENTIFIER -> {}
				TokenType.COLON -> insideTypeAnnotation = true
				TokenType.COMMA -> insideTypeAnnotation = false
				TokenType.PIPE -> if (!insideTypeAnnotation) return false
				else -> return false
			}
		}

		// Optional return type annotation
		if (lookAhead(offset).type == TokenType.COLON) {
			offset++ // Skip ':'
			while (lookAhead(offset).type == TokenType.IDENTIFIER || lookAhead(offset).type == TokenType.PIPE) {
				offset++
			}
		}

		return lookAhead(offset).type == TokenType.EQUAL
	}

	/**
	 * Scans ahead to see if the current position is the start of a lambda expression.
	 */
	private fun isLambda(): Boolean {
		if (check(TokenType.IDENTIFIER)) return lookAhead(1).type == TokenType.ARROW // Allow no parentheses for single parameter functions such as `x->x+1`

		if (!check(TokenType.LPAREN)) return false

		var offset = 1 // Skip '('
		if (lookAhead(offset).type == TokenType.RPAREN) return lookAhead(offset + 1).type == TokenType.ARROW // Empty lambda

		while (true) {
			if (lookAhead(offset++).type != TokenType.IDENTIFIER) return false

			if (lookAhead(offset).type == TokenType.COLON) {
				offset++
				if (lookAhead(offset++).type != TokenType.IDENTIFIER) return false
				while (lookAhead(offset).type == TokenType.PIPE && lookAhead(offset + 1).type == TokenType.IDENTIFIER) {
					offset += 2 // Skip | and ID
				}
			}

			// Next must be ',' or ')'
			val next = lookAhead(offset++).type

			if (next == TokenType.RPAREN) return lookAhead(offset).type == TokenType.ARROW
			if (next != TokenType.COMMA) return false
		}
	}
	//endregion

}