package ca.rttv.chatcalc.engine.astvisitors

import ca.rttv.chatcalc.engine.*
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.types.AnyConstraint
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.interleaveWith
import me.ancientri.rimelib.util.text.TextBuilder
import me.ancientri.rimelib.util.text.text
import net.minecraft.screen.ScreenTexts.SPACE
import net.minecraft.text.Text

/**
 * An implementation of [ExpressionVisitor] and [StatementVisitor] that pretty-prints AST nodes to colored [Text].
 */
object PrettyPrinter : ExpressionVisitor<Text>, StatementVisitor<Text, NoContext> {
	val COMMA = ", ".text(ColorPalette.SUBTEXT2)

	fun print(node: Expression): Text = node.accept(this)
	fun print(node: Statement): Text = node.accept(this, NoContext)

	//region Helper extensions
	context(builder: TextBuilder)
	private fun String.op() {
		builder.append(SPACE)
		builder.append(this.text(ColorPalette.SUBTEXT2))
		builder.append(SPACE)
	}

	context(builder: TextBuilder)
	private fun String.keyword() {
		builder.append(this.text(ColorPalette.ACCENT))
		builder.append(SPACE)
	}

	context(builder: TextBuilder)
	private fun String.ident() {
		builder.append(this.text(ColorPalette.TEXT))
	}

	context(builder: TextBuilder)
	private fun Token.fmt() {
		this.lexeme.ident()
	}
	//endregion

	//region StatementVisitor methods
	override fun visitVariableAssignment(statement: VariableAssignment, context: NoContext): Text = text {
		statement.name.fmt()
		"=".op()
		+statement.value.accept(this@PrettyPrinter)
	}

	override fun visitQuery(statement: Query, context: NoContext): Text = statement.expression.accept(this)

	override fun visitUndefine(statement: Undefine, context: NoContext): Text = text {
		"Undefine".keyword()
		statement.name.fmt()
	}

	override fun visitFunctionDefinition(statement: FunctionDefinition, context: NoContext): Text = text {
		statement.name.fmt()
		"(" colored ColorPalette.SUBTEXT2
		statement.parameters.asSequence()
			.map {
				text {
					it.name colored ColorPalette.TEXT
					if (it.constraint != AnyConstraint) { // Unannotated parameters are implicitly ANY, and ANY constraints are implicitly unannotated.
						": " colored ColorPalette.SUBTEXT2
						+it.constraint.toPrettyText()
					}
				}
			}
			.interleaveWith(COMMA)
			.forEach(::append)
		")" colored ColorPalette.SUBTEXT2
		if (statement.returnType != AnyConstraint) {
			": ".colored(ColorPalette.SUBTEXT2)
			+statement.returnType.toPrettyText()
		}
		"=".op()
		+statement.body.accept(this@PrettyPrinter)
	}

	override fun visitExpressionStatement(statement: ExpressionStatement, context: NoContext): Text = statement.expression.accept(this)
	//endregion

	//region ExpressionVisitor methods
	override fun visitBinary(expr: Binary): Text = text {
		+expr.left.accept(this@PrettyPrinter)
		when (expr.operator.lexeme) {
			"*" -> "×".op()
			">=" -> "≥".op()
			"<=" -> "≤".op()
			"!=" -> "≠".op()
			"->" -> "→".op()
			else -> expr.operator.lexeme.op()
		}
		+expr.right.accept(this@PrettyPrinter)
	}

	override fun visitUnary(expr: Unary): Text = text {
		expr.operator.lexeme colored ColorPalette.SUBTEXT2
		+expr.right.accept(this@PrettyPrinter)
	}

	override fun visitPostfix(expr: Postfix): Text = text {
		+expr.operand.accept(this@PrettyPrinter)
		expr.operator.lexeme colored ColorPalette.SUBTEXT2
	}

	override fun visitLiteral(expr: Literal): Text = text {
		expr.value.toString() colored ColorPalette.PEACH
	}

	override fun visitGrouping(expr: Grouping): Text = text {
		"(" colored ColorPalette.SUBTEXT2
		+expr.expression.accept(this@PrettyPrinter)
		")" colored ColorPalette.SUBTEXT2
	}

	override fun visitAbsoluteValue(expr: AbsoluteValue): Text = text {
		"|" colored ColorPalette.SUBTEXT2
		+expr.expression.accept(this@PrettyPrinter)
		"|" colored ColorPalette.SUBTEXT2
	}

	override fun visitVector(expr: Vector): Text = text {
		"[" colored ColorPalette.SUBTEXT2
		expr.elements.asSequence()
			.map { it.accept(this@PrettyPrinter) }
			.interleaveWith(COMMA)
			.forEach(::append)
		"]" colored ColorPalette.SUBTEXT2
	}

	override fun visitVariable(expr: Variable): Text = text {
		expr.name.fmt()
	}

	override fun visitFunctionCall(expr: FunctionCall): Text = text {
		+expr.callee.accept(this@PrettyPrinter)
		"(" colored ColorPalette.SUBTEXT2
		expr.arguments.asSequence()
			.map { it.accept(this@PrettyPrinter) }
			.interleaveWith(COMMA)
			.forEach(::append)
		")" colored ColorPalette.SUBTEXT2
	}

	override fun visitIndexAccess(expr: IndexAccess): Text = text {
		+expr.operand.accept(this@PrettyPrinter)
		"[" colored ColorPalette.SUBTEXT2
		expr.indices.asSequence()
			.map { it.accept(this@PrettyPrinter) }
			.interleaveWith(COMMA)
			.forEach(::append)
		"]" colored ColorPalette.SUBTEXT2
	}

	override fun visitConditional(expr: Conditional): Text = text {
		"if".keyword()
		+expr.condition.accept(this@PrettyPrinter)
		" then".keyword()
		+expr.ifTrue.accept(this@PrettyPrinter)
		" else".keyword()
		+expr.ifFalse.accept(this@PrettyPrinter)
	}

	override fun visitLambda(expr: Lambda): Text = text {
		when (expr.parameters.size) {
			0 -> "()".text(ColorPalette.SUBTEXT2)
			1 -> {
				val first = expr.parameters.first()
				first.name colored ColorPalette.TEXT
				if (first.constraint != AnyConstraint) { // Unannotated parameters are implicitly ANY, and ANY constraints are implicitly unannotated.
					": " colored ColorPalette.SUBTEXT2
					+first.constraint.toPrettyText()
				}
			}

			else -> {
				"(" colored ColorPalette.SUBTEXT2
				expr.parameters.asSequence()
					.map {
						text {
							it.name colored ColorPalette.TEXT
							if (it.constraint != AnyConstraint) { // Unannotated parameters are implicitly ANY, and ANY constraints are implicitly unannotated.
								": " colored ColorPalette.SUBTEXT2
								+it.constraint.toPrettyText()
							}
						}
					}
					.interleaveWith(COMMA)
					.forEach(::append)
				")" colored ColorPalette.SUBTEXT2
			}
		}
		"→".op()
		+expr.body.accept(this@PrettyPrinter)
	}
	//endregion
}