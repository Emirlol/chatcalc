package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.lexer.Lexer
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.parser.Parser
import ca.rttv.chatcalc.engine.types.FunctionParameter
import ca.rttv.chatcalc.engine.types.TypeConstraint
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult

object NoContext

interface StatementVisitor<R, C> {
	fun visitExpressionStatement(statement: ExpressionStatement, context: C): R
	fun visitFunctionDefinition(statement: FunctionDefinition, context: C): R
	fun visitUndefine(statement: Undefine, context: C): R
	fun visitQuery(statement: Query, context: C): R
	fun visitVariableAssignment(statement: VariableAssignment, context: C): R
}

sealed interface Statement {
	fun <R, C> accept(visitor: StatementVisitor<R, C>, context: C): R
}

// region  AST Node classes
data class ExpressionStatement(val expression: Expression) : Statement {
	override fun <R, C> accept(visitor: StatementVisitor<R, C>, context: C) = visitor.visitExpressionStatement(this, context)
}

data class FunctionDefinition(
	val name: Token,
	val parameters: List<FunctionParameter>,
	val returnType: TypeConstraint,
	val body: Expression,
	val astSource: String
) : Statement {
	override fun <R, C> accept(visitor: StatementVisitor<R, C>, context: C) = visitor.visitFunctionDefinition(this, context)

	companion object {
		// Save the source and reparse when decoding. This is simpler than trying to serialize the entire AST and more future-proof.
		val CODEC: Codec<FunctionDefinition> = Codec.STRING.comapFlatMap(
			{
				val (lexed, source) = try {
					Lexer(it).scanTokens()
				} catch (e: Exception) {
					return@comapFlatMap DataResult.error { e.message ?: "Unknown error" }
				}
				val parsed = try {
					Parser(lexed, source).parse()
				} catch (e: Exception) {
					return@comapFlatMap DataResult.error { e.message ?: "Unknown error" }
				}
				if (parsed.size != 1) return@comapFlatMap DataResult.error { "Expected exactly one statement." }
				when (val function = parsed.first()) {
					is FunctionDefinition -> DataResult.success(function)
					else -> return@comapFlatMap DataResult.error { "Expected function definition." }
				}
			},
			FunctionDefinition::astSource
		)
	}
}

data class Undefine(val name: Token) : Statement {
	override fun <R, C> accept(visitor: StatementVisitor<R, C>, context: C) = visitor.visitUndefine(this, context)
}

data class Query(val expression: Expression) : Statement {
	override fun <R, C> accept(visitor: StatementVisitor<R, C>, context: C) = visitor.visitQuery(this, context)
}

data class VariableAssignment(val name: Token, val value: Expression) : Statement {
	override fun <R, C> accept(visitor: StatementVisitor<R, C>, context: C) = visitor.visitVariableAssignment(this, context)
}
//endregion


