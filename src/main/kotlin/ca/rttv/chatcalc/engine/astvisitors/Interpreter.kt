@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.engine.astvisitors

import ca.rttv.chatcalc.engine.*
import ca.rttv.chatcalc.engine.astvisitors.Interpreter.Context
import ca.rttv.chatcalc.engine.env.EnvDelta
import ca.rttv.chatcalc.engine.env.MapEnvironment
import ca.rttv.chatcalc.engine.env.MutableEnvironment
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.lexer.TokenType
import ca.rttv.chatcalc.engine.types.*
import com.danrusu.pods4k.immutableArrays.ImmutableArray
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction
import it.unimi.dsi.fastutil.doubles.DoubleBinaryOperator
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.interleaveWith
import me.ancientri.rimelib.util.text.text
import net.minecraft.screen.ScreenTexts
import java.util.function.Supplier

class Interpreter(rootEnvSupplier: Supplier<MutableEnvironment>) : StatementVisitor<MathObject?, Context>, ExpressionVisitor<MathObject> {
	class Context {
		private val envDeltas = ImmutableArray.Builder<EnvDelta>()

		operator fun plusAssign(delta: EnvDelta) {
			envDeltas += delta
		}

		fun build(): ImmutableArray<EnvDelta> = envDeltas.build()
	}

	/**
	 * Persistent root environment containing the user's definitions.
	 */
	private val rootEnvironment = rootEnvSupplier.get()

	/**
	 * Dynamic environment that is used for function scopes.
	 */
	private var currentEnvironment: MutableEnvironment? = null

	private var recursionDepth = 0

	fun interpret(statements: List<Statement>): EngineResult {
		require(statements.isNotEmpty()) { "No statements to interpret." }
		val context = Context()

		currentEnvironment = rootEnvironment
		try {
			for (i in statements.indices) {
				val statement = statements[i]
				if (i != statements.lastIndex) statement.accept(this, context) // Execute for side effects. This is probably not the best way to go about this, but we're not explicitly supporting multiple statements during execution anyway.

				return when (statement) { // Return a result for the very last statement
					is Query -> {
						val result = evaluate(statement.expression)
						EngineResult.Append(result, context.build())
					}

					is FunctionDefinition -> {
						statement.accept(this, context)
						EngineResult.Clear(context.build()) {
							"(" colored ColorPalette.SUBTEXT2
							"Define " colored ColorPalette.TEXT
							+PrettyPrinter.print(statement)
							")" colored ColorPalette.SUBTEXT2
						}
					}

					is Undefine -> {
						val key = statement.name.lexeme

						val existing = rootEnvironment[key] ?: throw EvaluationError(statement.name) {
							"Cannot undefine '" colored ColorPalette.ERROR
							key colored ColorPalette.TEXT
							"' because it is not defined." colored ColorPalette.ERROR
						}

						statement.accept(this, context)

						EngineResult.Clear(context.build()) {
							"(" colored ColorPalette.SUBTEXT2
							"Undefine " colored ColorPalette.TEXT
							statement.name.lexeme colored ColorPalette.TEXT
							if (existing is UserDefinedFunction) +existing.signatures.first().toPrettyText()
							")" colored ColorPalette.SUBTEXT2
						}
					}

					is VariableAssignment -> {
						val result = requireNotNull(statement.accept(this, context)) { "This should've returned a preview object" }
						EngineResult.Clear(context.build()) {
							"(" colored ColorPalette.SUBTEXT2
							"Assign " colored ColorPalette.TEXT
							statement.name.lexeme colored ColorPalette.ACCENT
							" = " colored ColorPalette.TEXT
							+result.toPrettyText()
							")" colored ColorPalette.SUBTEXT2
						}
					}

					else -> {
						val result = statement.accept(this, context)
						if (result == null) EngineResult.Clear(context.build()) { "No result." colored ColorPalette.SUBTEXT2 }
						else EngineResult.Value(result, context.build())
					}
				}
			}
			return EngineResult.Error(EvaluationError(Token(TokenType.EOF, "")) { "Unexpected end of file." colored ColorPalette.ERROR }) // Should be unreachable, but just in case.
		} catch (error: EngineError) {
			return EngineResult.Error(error)
		} catch (exception: Exception) {
			throw exception // Any non-engine-error is pretty much a critical failure and should be thrown so that it's noticed.
		} finally {
			currentEnvironment = null
		}
	}

	private fun evaluate(expression: Expression) = expression.accept(this)

	fun createLocalEnvironment() = MapEnvironment(parent = rootEnvironment)

	fun executeInScope(expression: Expression, env: MutableEnvironment): MathObject {
		if (recursionDepth >= MAX_RECURSION_DEPTH) throw EvaluationError(Token(TokenType.EOF, "")) {
			"Stack overflow: exceeded recursion limit of " colored ColorPalette.ERROR
			MAX_RECURSION_DEPTH.toString() colored ColorPalette.TEXT
			"." colored ColorPalette.ERROR
		}

		val previousEnv = currentEnvironment
		currentEnvironment = env
		recursionDepth++
		try {
			return evaluate(expression)
		} catch (error: EngineError) {
			throw error
		} finally {
			recursionDepth--
			currentEnvironment = previousEnv
		}
	}

	//region StatementVisitor methods
	override fun visitExpressionStatement(statement: ExpressionStatement, context: Context) = evaluate(statement.expression)

	override fun visitFunctionDefinition(statement: FunctionDefinition, context: Context): MathObject {
		val allowedLocals = mutableSetOf<String>()
		statement.parameters.map(FunctionParameter::name).forEach(allowedLocals::add)
		allowedLocals += statement.name.lexeme

		val validator = SemanticValidator(allowedLocals, currentEnvironment ?: rootEnvironment, statement.name.lexeme, statement.parameters.size)
		validator.validate(statement.body)

		val function = UserDefinedFunction(statement)
		context += EnvDelta.Set(statement.name.lexeme, function)

		return function // Preview object
	}

	override fun visitVariableAssignment(statement: VariableAssignment, context: Context): MathObject {
		val value = evaluate(statement.value)
		context += EnvDelta.Set(statement.name.lexeme, value)
		return value // Preview object
	}

	override fun visitUndefine(statement: Undefine, context: Context): MathObject? {
		val env = requireNotNull(currentEnvironment) { "Interpreter not yet initialized!" }
		val name = statement.name.lexeme
		if (name !in env) throw EvaluationError(statement.name) {
			"Cannot undefine '" colored ColorPalette.ERROR
			name colored ColorPalette.TEXT
			"' because it is not defined." colored ColorPalette.ERROR
		}
		context += EnvDelta.Remove(name)
		return null
	}

	override fun visitQuery(statement: Query, context: Context): MathObject = evaluate(statement.expression)
//endregion

//region ExpressionVisitor methods

	override fun visitLiteral(expr: Literal) = MathScalar(expr.value as Double)

	override fun visitGrouping(expr: Grouping) = evaluate(expr.expression)

	override fun visitVariable(expr: Variable): MathObject {
		val env = requireNotNull(currentEnvironment) { "Interpreter not yet initialized!" }
		return when (val obj = env[expr.name.lexeme]) {
			null -> throw EvaluationError(expr.name) {
				"Undefined variable '" colored ColorPalette.ERROR
				expr.name.lexeme colored ColorPalette.TEXT
				"'." colored ColorPalette.ERROR
			}

			is ComputedProperty -> obj.supplier()
			else -> obj
		}
	}

	override fun visitAbsoluteValue(expr: AbsoluteValue): MathObject {
		val value = evaluate(expr.expression)
		val opToken = Token(TokenType.PIPE, "|")
		return map(value, opToken) { kotlin.math.abs(it) }
	}

	override fun visitUnary(expr: Unary): MathObject {
		val right = evaluate(expr.right)

		return when (expr.operator.type) {
			TokenType.MINUS -> when (right) {
				is MathScalar -> MathScalar(-right.value)
				is MathTensor -> map(right, expr.operator) { -it }

				else -> throw EvaluationError(expr.operator) {
					"Unary minus can only be applied to scalars or tensors." colored ColorPalette.ERROR
				}
			}

			TokenType.PLUS -> right // No-op, it's there for clarity

			TokenType.TILDE -> when (right) {
				is MathScalar -> if (right.value == 0.0) MathScalar(1.0) else MathScalar(0.0)
				is MathBoolean -> MathBoolean(!right.value)
				else -> throw EvaluationError(expr.operator) {
					"Logical not (~) can only be applied to scalars or booleans." colored ColorPalette.ERROR
				}
			}

			else -> throw EvaluationError(expr.operator) {
				"Unknown unary operator." colored ColorPalette.ERROR
			}
		}
	}

	override fun visitPostfix(expr: Postfix): MathObject {
		val operand = evaluate(expr.operand)
		return when (expr.operator.type) {
			TokenType.BANG -> {
				if (operand !is MathScalar) throw EvaluationError(expr.operator) {
					"Can only apply factorial (!) to scalars." colored ColorPalette.ERROR
				}
				MathOps.fact(operand)
			}

			else -> throw EvaluationError(expr.operator) {
				"Unknown postfix operator." colored ColorPalette.ERROR
			}
		}
	}

	override fun visitBinary(expr: Binary): MathObject {
		val left = evaluate(expr.left)

		// Short-circuiting &&
		if (expr.operator.type == TokenType.AMPERSAND_AMPERSAND) {
			val leftBool = when (left) {
				is MathBoolean -> left.value
				is MathScalar -> left.value != 0.0
				else -> throw EvaluationError(expr.operator) {
					"Can only apply logical and (&&) to scalars or booleans." colored ColorPalette.ERROR
				}
			}
			if (!leftBool) return MathBoolean(false)
			return when (val right = evaluate(expr.right)) {
				is MathBoolean -> MathBoolean(right.value)
				is MathScalar -> MathBoolean(right.value != 0.0)
				else -> throw EvaluationError(expr.operator) {
					"Can only apply logical and (&&) to scalars or booleans." colored ColorPalette.ERROR
				}
			}
		}

		// Short-circuiting ||
		if (expr.operator.type == TokenType.PIPE_PIPE) {
			val leftBool = when (left) {
				is MathBoolean -> left.value
				is MathScalar -> left.value != 0.0
				else -> throw EvaluationError(expr.operator) {
					"Can only apply logical or (||) to scalars or booleans." colored ColorPalette.ERROR
				}
			}
			if (leftBool) return MathBoolean(true)
			return when (val right = evaluate(expr.right)) {
				is MathBoolean -> MathBoolean(right.value)
				is MathScalar -> MathBoolean(right.value != 0.0)
				else -> throw EvaluationError(expr.operator) {
					"Can only apply logical or (||) to scalars or booleans." colored ColorPalette.ERROR
				}
			}
		}

		val right = evaluate(expr.right)
		val op = expr.operator

		return when (op.type) {
			TokenType.EQUAL_EQUAL -> MathBoolean(left == right)
			TokenType.BANG_EQUAL -> MathBoolean(left != right)
			TokenType.LESS -> compare(left, right, op) { a, b -> a < b }
			TokenType.LESS_EQUAL -> compare(left, right, op) { a, b -> a <= b }
			TokenType.GREATER -> compare(left, right, op) { a, b -> a > b }
			TokenType.GREATER_EQUAL -> compare(left, right, op) { a, b -> a >= b }
			TokenType.PLUS -> zip(left, right, op, Double::plus)
			TokenType.MINUS -> zip(left, right, op, Double::minus)
			TokenType.DOT_STAR -> zip(left, right, op, Double::times)
			TokenType.SLASH, TokenType.DOT_SLASH -> zip(left, right, op, Double::div)
			TokenType.HAT, TokenType.DOT_HAT -> MathOps.pow(left, right)
			TokenType.PERCENT -> MathOps.mod(left, right)
			TokenType.STAR -> {
				if (left is MathScalar && right is MathScalar) MathScalar(left.value * right.value)
				else if (left is MathScalar || right is MathScalar) zip(left, right, op, Double::times)
				else if (left is MathTensor && right is MathTensor) matMul(left, right)
				else throw EvaluationError(op) { "Can only multiply scalars or tensors." colored ColorPalette.ERROR }
			}

			else -> throw EvaluationError(op) {
				"Unknown binary operator '" colored ColorPalette.ERROR
				op.lexeme colored ColorPalette.TEXT
				"'." colored ColorPalette.ERROR
			}
		}
	}

	override fun visitIndexAccess(expr: IndexAccess): MathObject {
		val operand = evaluate(expr.operand)
		if (operand !is MathTensor) throw EvaluationError(Token(TokenType.LBRACKET, "[")) {
			"Can only index tensors." colored ColorPalette.ERROR
		}

		var currentTensor: MathTensor = operand

		for (indexExpr in expr.indices) {
			when (indexExpr) {
				is Slice -> {
					val start = indexExpr.start?.let { (evaluate(it) as MathScalar).value.toInt() } ?: 0
					val end = indexExpr.end?.let { (evaluate(it) as MathScalar).value.toInt() } ?: currentTensor.shape[0]
					val step = indexExpr.step?.let { (evaluate(it) as MathScalar).value.toInt() } ?: 1

					// Python-style negative indexing
					val s = if (start < 0) currentTensor.shape[0] + start else start
					val e = if (end < 0) currentTensor.shape[0] + end else end

					currentTensor = currentTensor.slice(s, e, step)
				}

				else -> {
					val indexVal = evaluate(indexExpr)
					if (indexVal !is MathScalar) throw EvaluationError(Token(TokenType.LBRACKET, "[")) {
						"Tensor indices must be scalars." colored ColorPalette.ERROR
					}

					var idx = indexVal.value.toInt()
					if (idx < 0) idx += currentTensor.shape[0] // Negative indexing

					if (idx !in 0 until currentTensor.shape[0]) throw EvaluationError(Token(TokenType.LBRACKET, "[")) {
						"Index " colored ColorPalette.ERROR
						idx.toString() colored ColorPalette.TEXT
						" out of bounds for tensor with shape " colored ColorPalette.ERROR
						currentTensor.shape.joinToString(prefix = "[", postfix = "]") colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}

					currentTensor = currentTensor.getIndex(idx)
				}
			}
		}

		if (currentTensor.rank == 0) return MathScalar(currentTensor.getFlat(0), currentTensor.radix)

		return currentTensor
	}

	override fun visitFunctionCall(expr: FunctionCall): MathObject {
		val callee = evaluate(expr.callee)

		val errorToken = if (expr.callee is Variable) expr.callee.name else Token(TokenType.IDENTIFIER, "call")
		if (callee !is MathFunction) throw EvaluationError(errorToken) {
			"Non-function object of type '" colored ColorPalette.ERROR
			callee.type.asString() colored ColorPalette.TEXT
			"' cannot be called." colored ColorPalette.ERROR
		}

		val arguments = expr.arguments.map(::evaluate)

		val overload = callee.selectOverload(this, arguments) ?: throw EvaluationError(errorToken) {
			"No matching signature for arguments (" colored ColorPalette.ERROR
			arguments.asSequence()
				.map { it.type.id.text(ColorPalette.TEXT) }
				.interleaveWith(PrettyPrinter.COMMA)
				.forEach(::append)
			"). " colored ColorPalette.ERROR
			if (callee.signatures.size == 1) {
				"Expected signature: " colored ColorPalette.ERROR
				+callee.signatures.first().toPrettyText()
			} else {
				"Expected one of:" colored ColorPalette.ERROR
				+ScreenTexts.LINE_BREAK
				callee.signatures.asSequence()
					.map {
						text {
							when (callee) {
								is BuiltinFunction -> callee.name colored ColorPalette.TEXT
								is UserDefinedFunction -> callee.definition.name.lexeme colored ColorPalette.TEXT
								else -> {}
							}
							+it.toPrettyText()
						}
					}
					.interleaveWith(ScreenTexts.LINE_BREAK)
					.forEach(::append)
			}
		}

		val result = overload.executable(this, arguments)

		if (!overload.signature.returnConstraint.accepts(result)) throw EvaluationError(errorToken) {
			"Function promised to return a " colored ColorPalette.ERROR
			+overload.signature.returnConstraint.toPrettyText()
			" but returned a " colored ColorPalette.ERROR
			result.type.id colored ColorPalette.TEXT
			"." colored ColorPalette.ERROR
		}

		return result
	}

	override fun visitVector(expr: Vector): MathObject {
		val elements = expr.elements.map(::evaluate)

		if (elements.isEmpty()) return ArrayTensor(doubleArrayOf(), intArrayOf(0))

		val first = elements.first()

		// Array of scalars:
		if (first is MathScalar) {
			val data = DoubleArray(elements.size)
			var allRadixesEqual = true
			var lastRadix = first.radix
			for (i in elements.indices) {
				val element = elements[i]
				if (element !is MathScalar) throw EvaluationError(Token(TokenType.COMMA, ",")) {
					"Mixed types in vector literal: expected all elements to be scalars." colored ColorPalette.ERROR
				}
				if (element.radix != lastRadix) allRadixesEqual = false
				lastRadix = element.radix
				data[i] = element.value
			}
			return ArrayTensor.vector(data, if (allRadixesEqual) lastRadix else 10)
		}

		// Array of arrays:
		if (first is MathTensor) {
			val subShape = first.shape
			val totalSize = elements.size * first.size
			val data = DoubleArray(totalSize)
			var offset = 0

			for (element in elements) {
				if (element !is MathTensor) throw EvaluationError(Token(TokenType.COMMA, "[")) {
					"Mixed types in vector literal: expected all elements to be tensors." colored ColorPalette.ERROR
				}
				if (!element.shape.contentEquals(subShape)) throw EvaluationError(Token(TokenType.COMMA, "[")) {
					"Mixed shapes in vector literal: expected all elements to have the same shape." colored ColorPalette.ERROR
				}
				when (element) {
					is ArrayTensor -> element.data.copyInto(data, offset, 0, element.size)
					is RangeTensor -> {
						val iterator = element.iterator()
						for (i in 0 until element.size) data[offset + i] = iterator.nextDouble()
					}
				}
				offset += element.size
			}

			val newShape = IntArray(subShape.size + 1)
			newShape[0] = elements.size
			subShape.copyInto(newShape, 1)

			return ArrayTensor(data, newShape)
		}

		throw EvaluationError(Token(TokenType.COMMA, "[")) {
			"Invalid element type in vector literal: expected scalars or tensors." colored ColorPalette.ERROR
		}
	}

	override fun visitConditional(expr: Conditional): MathObject {
		val isTrue = when (val condition = evaluate(expr.condition)) {
			is MathBoolean -> condition.value
			is MathScalar -> condition.value != 0.0
			else -> throw EvaluationError(Token(TokenType.IF, "if")) {
				"Condition must be a boolean or a scalar." colored ColorPalette.ERROR
			}
		}

		return if (isTrue) evaluate(expr.ifTrue) else evaluate(expr.ifFalse)
	}

	override fun visitLambda(expr: Lambda): MathObject {
		// Capture the current environment as the lambda's closure
		val scope = currentEnvironment ?: rootEnvironment
		return LambdaFunction(expr, scope)
	}
//endregion

	private fun compare(l: MathObject, r: MathObject, op: Token, comp: (Double, Double) -> Boolean): MathBoolean {
		if (l is MathScalar && r is MathScalar) return MathBoolean(comp(l.value, r.value))
		throw EvaluationError(op) { "Comparison is only supported for scalars." colored ColorPalette.ERROR }
	}

	fun matMul(a: MathTensor, b: MathTensor): MathTensor {
		// Special case, which would lead to the dummy dimension being removed twice with the promotion logic below
		if (a.rank == 1 && b.rank == 1) return ArrayTensor.vector(MathOps.dotProduct(a, b).value) // 0D tensor should probably be the scalar itself, but it complicates the logic

		// Vector promotion
		if (a.rank == 1) {
			val promotedA = a.reshape(intArrayOf(1, a.shape[0]))
			val result = matMul(promotedA, b)
			val newShape = result.shape.toMutableList().apply { removeAt(lastIndex - 1) }.toIntArray() // [..., 1, M] * [..., M, N] = [..., 1, N], so the dummy dimension is always the 2nd last that we can drop
			return result.reshape(newShape)
		}

		if (b.rank == 1) {
			val promotedB = b.reshape(intArrayOf(b.shape[0], 1))
			val result = matMul(a, promotedB)
			val newShape = result.shape.copyOfRange(0, result.shape.lastIndex) // [..., M, N] * [..., N, 1] = [..., M, 1], so the dummy dimension is always the last that we can drop
			return result.reshape(newShape)
		}

		// Inner dimension check
		val colsA = a.shape[a.rank - 1]
		val rowsB = b.shape[b.rank - 2]
		if (colsA != rowsB) throw EvaluationError(Token(TokenType.STAR, "*")) {
			"Cannot multiply matrices with different inner dimensions: " colored ColorPalette.ERROR
			colsA.toString() colored ColorPalette.TEXT
			" != " colored ColorPalette.SUBTEXT2
			rowsB.toString() colored ColorPalette.TEXT
		}

		// 2D x 2D
		if (a.rank == 2 && b.rank == 2) {
			val rows = a.shape[0]
			val cols = b.shape[1]
			val common = a.shape[1]
			val newData = DoubleArray(rows * cols)
			if (a is ArrayTensor && b is ArrayTensor) { // Small optimization using direct array accesses. The logic is the same otherwise.
				val aData = a.data
				val bData = b.data

				val strideRowA = a.strides[0]
				val strideColA = a.strides[1]
				val strideRowB = b.strides[0]
				val strideColB = b.strides[1]

				for (row in 0 until rows) {
					val rowOffsetA = row * strideRowA
					for (col in 0 until cols) {
						val colOffsetB = col * strideColB
						var sum = 0.0
						for (k in 0 until common) {
							// A[row, k] * B[k, col]
							val valA = aData[rowOffsetA + k * strideColA]
							val valB = bData[k * strideRowB + colOffsetB]
							sum += valA * valB
						}
						newData[row * cols + col] = sum
					}
				}
			} else {
				val strideRowA = a.strides[0]
				val strideColA = a.strides[1]
				val strideRowB = b.strides[0]
				val strideColB = b.strides[1]

				for (row in 0 until rows) {
					val rowOffsetA = row * strideRowA
					for (col in 0 until cols) {
						val colOffsetB = col * strideColB
						var sum = 0.0
						for (k in 0 until common) {
							// A[row, k] * B[k, col]
							val valA = a.getFlat(rowOffsetA + k * strideColA)
							val valB = b.getFlat(k * strideRowB + colOffsetB)
							sum += valA * valB
						}
						newData[row * cols + col] = sum
					}
				}
			}
			return ArrayTensor(newData, intArrayOf(rows, cols))
		}

		// Recursive batching
		val batchSize = if (a.rank > 2 && b.rank > 2) {
			if (a.shape[0] != b.shape[0]) throw EvaluationError(Token(TokenType.STAR, "*")) {
				"Batch sizes of matrices do not match: ${a.shape[0]} != ${b.shape[0]}" colored ColorPalette.ERROR
			}
			a.shape[0]
		} else if (a.rank > 2) {
			a.shape[0]
		} else if (b.rank > 2) {
			b.shape[0]
		} else throw EvaluationError(Token(TokenType.STAR, "*")) {
			"Cannot multiply matrices with rank ${a.rank} and ${b.rank}." colored ColorPalette.ERROR
		}

		val results = mutableListOf<MathTensor>()

		for (i in 0 until batchSize) {
			val sliceA = if (a.rank > 2) a.getIndex(i) else a
			val sliceB = if (b.rank > 2) b.getIndex(i) else b

			results += matMul(sliceA, sliceB)
		}

		// Reassembly
		val subResultShape = results[0].shape
		val finalShape = IntArray(subResultShape.size + 1)
		finalShape[0] = batchSize
		subResultShape.copyInto(finalShape, 1)

		val totalSize = results.sumOf(MathTensor::size)
		val newData = DoubleArray(totalSize)
		var offset = 0
		for (result in results) {
			if (result is ArrayTensor) result.data.copyInto(newData, offset) // Array copy optimization for array tensors
			else { // Fallback. This shouldn't happen since we always return ArrayTensors from matMul, but just in case we change later and forget.
				for (k in 0 until result.size) {
					newData[offset + k] = result.getFlat(k)
				}
			}
			offset += result.size
		}
		return ArrayTensor(newData, finalShape)
	}

	companion object {
		private const val MAX_RECURSION_DEPTH = 256

		fun map(obj: MathObject, opToken: Token, function: Double2DoubleFunction): MathObject = when (obj) {
			is MathScalar -> MathScalar(function.get(obj.value))
			// Optimized negation for range tensors
			is RangeTensor if opToken.type == TokenType.MINUS -> obj.copy(start = -obj.start, step = -obj.step, radix = obj.radix)

			is ArrayTensor -> {
				val newData = DoubleArray(obj.size)
				for (i in obj.data.indices) newData[i] = function.get(obj.data[i])
				ArrayTensor(newData, obj.shape, obj.radix)
			}

			is RangeTensor -> {
				val iter = obj.iterator()
				val newData = DoubleArray(obj.size) { function.get(iter.nextDouble()) }
				ArrayTensor(newData, obj.shape, obj.radix)
			}

			else -> throw EvaluationError(opToken) {
				"Operation " colored ColorPalette.ERROR
				opToken.lexeme colored ColorPalette.TEXT
				" can only be applied to scalars or tensors." colored ColorPalette.ERROR
			}
		}

		fun zip(left: MathObject, right: MathObject, opToken: Token, function: DoubleBinaryOperator): MathObject {
			if (left is MathScalar && right is MathScalar)
				return MathScalar(function.apply(left.value, right.value), if (left.radix == right.radix) left.radix else 10) // Inherit radix if matching

			val rangeResult = tryOptimizeRangeZip(left, right, opToken)
			if (rangeResult != null) return rangeResult

			val (lTensor, rTensor) = when {
				left is MathTensor && right is MathTensor -> left to right
				left is MathTensor -> left to null // Tensor-Scalar
				right is MathTensor -> null to right // Scalar-Tensor
				else -> throw EvaluationError(opToken) {
					"Invalid types for zip: " colored ColorPalette.ERROR
					left.type.asString() colored ColorPalette.TEXT
					" and " colored ColorPalette.ERROR
					right.type.asString() colored ColorPalette.TEXT
					"." colored ColorPalette.ERROR
				}
			}

			val shape = lTensor?.shape ?: rTensor!!.shape
			val size = lTensor?.size ?: rTensor!!.size
			val radix = lTensor?.radix ?: rTensor!!.radix

			if (lTensor != null && rTensor != null && !lTensor.shape.contentEquals(rTensor.shape)) throw EvaluationError(opToken) {
				"Shape mismatch: " colored ColorPalette.ERROR
				lTensor.shape.contentToString() colored ColorPalette.TEXT
				" vs " colored ColorPalette.ERROR
				rTensor.shape.contentToString() colored ColorPalette.TEXT
				"." colored ColorPalette.ERROR
			}

			val newData = DoubleArray(size)

			if ((lTensor is ArrayTensor?) && (rTensor is ArrayTensor?)) { // Array optimizations with direct access
				// Logic: If one is null (scalar), use the scalar value. If both exist, use arrays.
				when {
					lTensor != null && rTensor != null -> {
						val lData = lTensor.data
						val rData = rTensor.data
						for (i in 0 until size) newData[i] = function.apply(lData[i], rData[i])
					}

					lTensor != null && right is MathScalar -> {
						val lData = lTensor.data
						val rVal = right.value
						for (i in 0 until size) newData[i] = function.apply(lData[i], rVal)
					}

					left is MathScalar && rTensor != null -> {
						val lVal = left.value
						val rData = rTensor.data
						for (i in 0 until size) newData[i] = function.apply(lVal, rData[i])
					}
				}
				return ArrayTensor(newData, shape, radix)
			}

			when {
				lTensor != null && rTensor != null -> {
					val lIter = lTensor.iterator()
					val rIter = rTensor.iterator()

					for (i in 0 until size) {
						newData[i] = function.apply(lIter.nextDouble(), rIter.nextDouble())
					}
				}

				lTensor != null && right is MathScalar -> {
					val lIter = lTensor.iterator()
					val rVal = right.value

					for (i in 0 until size) {
						newData[i] = function.apply(lIter.nextDouble(), rVal)
					}
				}

				left is MathScalar && rTensor != null -> {
					val lVal = left.value
					val rIter = rTensor.iterator()

					for (i in 0 until size) {
						newData[i] = function.apply(lVal, rIter.nextDouble())
					}
				}
			}
			return ArrayTensor(newData, shape, radix)
		}

		/**
		 * Tries to optimize zip operations that involve range tensors by not iterating over the values but directly modifying the start and step values.
		 */
		@Suppress("IntroduceWhenSubject") // Makes it harder to read
		private fun tryOptimizeRangeZip(left: MathObject, right: MathObject, opToken: Token): MathTensor? = when {
			// We can only optimize if one is a RangeTensor and the other is a Scalar
			left is RangeTensor && right is MathScalar -> when (opToken.type) {
				TokenType.PLUS -> left.copy(start = left.start + right.value)
				TokenType.MINUS -> left.copy(start = left.start - right.value)
				TokenType.STAR -> left.copy(start = left.start * right.value, step = left.step * right.value)
				TokenType.SLASH -> left.copy(start = left.start / right.value, step = left.step / right.value)
				else -> null
			}

			left is MathScalar && right is RangeTensor -> when (opToken.type) {
				TokenType.PLUS -> right.copy(start = left.value + right.start)
				TokenType.MINUS -> right.copy(start = left.value - right.start, step = -right.step)
				TokenType.STAR -> right.copy(start = right.start * left.value, step = right.step * left.value)
				TokenType.SLASH -> null // scalar / range isn't linear, e.g., 1/x is a curve.
				else -> null
			}

			else -> null
		}
	}
}