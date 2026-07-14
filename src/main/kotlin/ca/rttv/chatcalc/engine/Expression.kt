package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.types.FunctionParameter
import me.ancientri.rimelib.util.client

sealed interface Expression {
	fun <R> accept(visitor: ExpressionVisitor<R>): R
}

interface ExpressionVisitor<R> {
	fun visitBinary(expr: Binary): R
	fun visitUnary(expr: Unary): R
	fun visitPostfix(expr: Postfix): R
	fun visitLiteral(expr: Literal): R
	fun visitGrouping(expr: Grouping): R
	fun visitAbsoluteValue(expr: AbsoluteValue): R
	fun visitVector(expr: Vector): R
	fun visitVariable(expr: Variable): R
	fun visitFunctionCall(expr: FunctionCall): R
	fun visitIndexAccess(expr: IndexAccess): R
	fun visitConditional(expr: Conditional): R
	fun visitLambda(expr: Lambda): R
}

// region  AST Node classes
data class Binary(val left: Expression, val operator: Token, val right: Expression) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitBinary(this)
}

data class Unary(val operator: Token, val right: Expression) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitUnary(this)
}

data class Postfix(val operand: Expression, val operator: Token) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitPostfix(this)
}

data class Literal(val value: Any?) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitLiteral(this)
}

data class Grouping(val expression: Expression) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitGrouping(this)
}

data class Vector(val elements: List<Expression>) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitVector(this)
}

data class Variable(val name: Token) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitVariable(this)
}

data class FunctionCall(val callee: Expression, val arguments: List<Expression>) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitFunctionCall(this)
}

data class IndexAccess(val operand: Expression, val indices: List<Expression>) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitIndexAccess(this)
}

data class AbsoluteValue(val expression: Expression) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitAbsoluteValue(this)
}

data class Conditional(
	val condition: Expression,
	val ifTrue: Expression,
	val ifFalse: Expression
) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitConditional(this)
}

data class Lambda(val parameters: List<FunctionParameter>, val body: Expression) : Expression {
	override fun <R> accept(visitor: ExpressionVisitor<R>) = visitor.visitLambda(this)
}

data class Slice(
    val start: Expression?,
    val end: Expression?,
    val step: Expression?
) : Expression {
    override fun <R> accept(visitor: ExpressionVisitor<R>): R {
		// This will be evaluated in IndexAccess
        throw UnsupportedOperationException("Slice cannot be evaluated directly")
    }
}
// endregion

