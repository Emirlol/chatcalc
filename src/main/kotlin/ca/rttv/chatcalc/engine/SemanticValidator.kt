package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.env.Environment
import ca.rttv.chatcalc.engine.types.MathFunction
import me.ancientri.rimelib.util.color.ColorPalette

class SemanticValidator(
	private val allowedLocals: Set<String>,
	private val environment: Environment,
	private val currentFunctionName: String? = null,
	private val currentFunctionArity: Int = 0
) : ExpressionVisitor<Unit> {

	fun validate(expr: Expression) = expr.accept(this)

	override fun visitBinary(expr: Binary) {
		validate(expr.left)
		validate(expr.right)
	}

	override fun visitUnary(expr: Unary) {
		validate(expr.right)
	}

	override fun visitPostfix(expr: Postfix) {
		validate(expr.operand)
	}

	override fun visitLiteral(expr: Literal) {
		// Literals are always valid
	}

	override fun visitGrouping(expr: Grouping) {
		validate(expr.expression)
	}

	override fun visitAbsoluteValue(expr: AbsoluteValue) {
		validate(expr.expression)
	}

	override fun visitVector(expr: Vector) {
		expr.elements.forEach(::validate)
	}

	override fun visitVariable(expr: Variable) {
		val name = expr.name.lexeme
		if (name in allowedLocals) return

		if (name == currentFunctionName) return

		if (name in environment) return

		throw EvaluationError(expr.name) {
			"Undefined variable '" colored ColorPalette.ERROR
			name colored ColorPalette.TEXT
			"'." colored ColorPalette.ERROR
		}
	}

	override fun visitFunctionCall(expr: FunctionCall) {
		// Existence check
		validate(expr.callee)
		expr.arguments.forEach(::validate)

		// We can only statically validate direct variable calls (e.g., f(x)), we can't easily validate indirect ones (like getFunc()(x)) without full type inference.
		if (expr.callee !is Variable) return

		val name = expr.callee.name.lexeme
		if (name in allowedLocals) return

		val argCount = expr.arguments.size

		// Recursion
		if (name != currentFunctionName) {
			if (argCount != currentFunctionArity) throw EvaluationError(expr.callee.name) {
				"Incorrect argument count for recursive call '" colored ColorPalette.ERROR
				name colored ColorPalette.TEXT
				"': expected " colored ColorPalette.ERROR
				currentFunctionArity.toString() colored ColorPalette.TEXT
				", got " colored ColorPalette.ERROR
				argCount.toString() colored ColorPalette.TEXT
				"." colored ColorPalette.ERROR
			}
			return
		}

		val mathObject = environment[name] ?: return

		if (mathObject is MathFunction) {
			val isArityValid = mathObject.signatures.any { signature ->
				if (signature.isVararg) argCount >= signature.parameters.size
				else argCount == signature.parameters.size
			}

			if (!isArityValid) throw EvaluationError(expr.callee.name) {
				"Incorrect argument count for '" colored ColorPalette.ERROR
				name colored ColorPalette.TEXT
				"'. Expected: " colored ColorPalette.ERROR
				mathObject.signatures.joinToString(" or ") { sig ->
					if (sig.isVararg) "at least ${sig.parameters.size}"
					else "${sig.parameters.size}"
				} colored ColorPalette.TEXT
				", got " colored ColorPalette.ERROR
				argCount.toString() colored ColorPalette.TEXT
				"." colored ColorPalette.ERROR
			}
		} else throw EvaluationError(expr.callee.name) {
			"'" colored ColorPalette.ERROR
			name colored ColorPalette.TEXT
			"' is a " colored ColorPalette.ERROR
			mathObject.type.id colored ColorPalette.TEXT
			", not a function." colored ColorPalette.ERROR
		}
	}

	override fun visitIndexAccess(expr: IndexAccess) {
		validate(expr.operand)
		expr.indices.forEach(::validate)
	}

	override fun visitConditional(expr: Conditional) {
		validate(expr.condition)
		validate(expr.ifTrue)
		validate(expr.ifFalse)
	}

	override fun visitLambda(expr: Lambda) {
		val newLocals = allowedLocals + expr.parameters.map { it.name }

		// New validator to validate with the new locals since we use a captured environment
		val validator = SemanticValidator(
			newLocals,
			environment,
			currentFunctionName,
			currentFunctionArity
		)

		validator.validate(expr.body)
	}
}