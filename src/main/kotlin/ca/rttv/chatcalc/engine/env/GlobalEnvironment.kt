package ca.rttv.chatcalc.engine.env

import ca.rttv.chatcalc.config.ConfigManager.config
import ca.rttv.chatcalc.engine.EvaluationError
import ca.rttv.chatcalc.engine.MathOps
import ca.rttv.chatcalc.engine.astvisitors.Interpreter
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.lexer.TokenType
import ca.rttv.chatcalc.engine.types.*
import ca.rttv.chatcalc.engine.types.MathTensor.Companion.fastForEach
import ca.rttv.chatcalc.engine.types.MathTensor.Companion.fastForEachIndexed
import com.google.common.collect.ImmutableMap
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction
import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.player
import net.minecraft.util.math.MathHelper
import kotlin.math.*

internal typealias EnvBuilder = ImmutableMap.Builder<String, MathObject>

object GlobalEnvironment : Environment {
	private const val EPSILON = 1E-16

	/*
	Some arbitrary rules for argument naming:
	- Use 'x', 'y', 'z' for single scalar or tensor arguments.
	- If the function is a functional-style function that has a function operator, the data it acts on should be "data" and the function could be anything appropriate, usually `<function-name>er` e.g., reducer, mapper,
	- If the type is 'any', use `obj`
	- If it doesn't fit the rules above, use common sense
	 */
	private val values: Map<String, MathObject> = createGlobalEnvironment {
		// Constants
		scalar("pi", Math.PI)
		scalar("e", Math.E)
		scalar("tau", Math.PI * 2)
		scalar("phi", 1.618033988749894)

		// Computed
		computed("x") { MathScalar(player!!.entityPos.x) }
		computed("y") { MathScalar(player!!.entityPos.y) }
		computed("z") { MathScalar(player!!.entityPos.z) }
		computed("pos") { ArrayTensor.vector(player!!.entityPos.run { doubleArrayOf(x, y, z) }) }
		computed("yaw") { MathScalar(config.convertFromDegrees(MathHelper.wrapDegrees(player!!.yaw).toDouble())) }
		computed("pitch") { MathScalar(config.convertFromDegrees(player!!.pitch.coerceIn(-90f, 90f).toDouble())) }
		computed("xp") { MathScalar(player!!.totalExperience.toDouble()) }
		computed("xpLevel") { MathScalar(player!!.experienceLevel.toDouble()) }
		computed("xpToNextLevel") { MathScalar(player!!.nextLevelExperience.toDouble()) }
		aliases("radian", "rad") { computed(it) { MathScalar(if (config.radians) 1.0 else 57.29577951308232) } }
		aliases("degree", "deg") { computed(it) { MathScalar(if (config.radians) 0.017453292519943295 else 1.0) } }

		// Functions

		aliases("random", "rand") {
			builtin(it) {
				returnType = real
				executes {
					MathScalar(Math.random())
				}
			}
		}

		// Trig functions
		trig("sin", ::sin)
		trig("cos", ::cos)
		trig("tan", ::cos, ::tan)
		trig("cot", ::tan) { 1.0 / tan(it) }
		trig("csc", ::sin) { 1.0 / sin(it) }
		trig("sec", ::cos) { 1.0 / cos(it) }

		aliases("asin", "arcsin") { arcTrig(it, ::asin) }
		aliases("acos", "arccos") { arcTrig(it, ::acos) }
		aliases("atan", "arctan") { arcTrig(it, ::atan) }
		aliases("acot", "arccot") { arcTrig(it, ::atan) { 1.0 / tan(it) } }
		aliases("acsc", "arcsec") { arcTrig(it, ::asin) { 1.0 / sin(it) } }
		aliases("asec", "arccsc") { arcTrig(it, ::acos) { 1.0 / cos(it) } }

		trig("sinh", ::sinh)
		trig("cosh", ::cosh)
		trig("tanh", ::cosh, ::tanh)
		trig("coth", ::tanh) { 1.0 / tanh(it) }
		trig("sech", ::cosh) { 1.0 / cosh(it) }
		trig("csch", ::sinh) { 1.0 / sinh(it) }

		aliases("asinh", "arcsinh") { arcTrig(it, ::asinh) }
		aliases("acosh", "arccosh") { arcTrig(it, ::acosh) }
		aliases("atanh", "arctanh") { arcTrig(it, ::atanh) }
		aliases("acoth", "arccoth") { arcTrig(it, ::atanh) { 1.0 / tanh(it) } }
		aliases("asech", "arcsech") { arcTrig(it, ::asinh) { 1.0 / sinh(it) } }
		aliases("acsch", "arccsch") { arcTrig(it, ::acosh) { 1.0 / cosh(it) } }

		// Basic element-wise functions
		unary("sqrt", ::sqrt)
		unary("cbrt", ::cbrt)
		unary("floor", ::floor)
		unary("ceil", ::ceil)
		unary("round", ::round)
		unary("abs", ::abs)
		unary("ln", ::ln)
		unary("exp", ::exp)
		builtinOverloads("log") {
			overload {
				arg("x", real or tensor)
				arg("base", real)
				returnType = real or tensor
				executes { args ->
					val x = args.first()
					val base = (args[1] as MathScalar).value
					Interpreter.map(x, Token(TokenType.IDENTIFIER, "log")) { log(it, base) }
				}
			}
			overload {
				arg("x", real or tensor)
				returnType = real or tensor
				executes { args ->
					Interpreter.map(args.first(), Token(TokenType.IDENTIFIER, "log"), ::log10)
				}
			}
		}

		aliases("sgn", "sign") { unary(it, ::sign) }

		reduce("min", MathTensor::min)
		reduce("max", MathTensor::max)
		reduce("sum", MathTensor::sum)
		builtin("clamp") {
			arg("value", real or tensor)
			arg("min", real)
			arg("max", real)
			returnType = real or tensor
			executes { args ->
				val min = (args[1] as MathScalar).value
				val max = (args[2] as MathScalar).value
				Interpreter.map(args[0], Token(TokenType.IDENTIFIER, "clamp")) { it.coerceIn(min, max) }
			}
		}
		aliases("avg", "average", "mean") { reduce(it, MathTensor::mean) }

		fun gcf(a: Double, b: Double): Double {
			var a = a
			var b = b

			if (b > a) a = b.also { b = a }
			while (b != 0.0) a = b.also { b = a % b }

			return a
		}

		reduce("lcm") {
			if (it.size == 0) RealScalar(0.0)
			else {
				// Start from 1 to avoid multiplying by 0
				it.reduce(1.0) { acc, next ->
					if (acc == 1.0) next
					else (acc * next) / gcf(acc, next)
				}
			}
		}
		reduce("gcf") { it.reduce { a, b -> gcf(a, b) } }

		builtin("pow") { // Element-wise or broadcasting
			arg("base", real or tensor)
			arg("exponent", real or tensor)
			returnType = real or tensor
			executes { args -> MathOps.pow(args[0], args[1]) }
		}

		builtinOverloads("cmp") {
			overload { // Strict comparison
				arg("a", real)
				arg("b", real)
				returnType = integer
				executes { args ->
					val a = (args[0] as MathScalar).value
					val b = (args[1] as MathScalar).value
					MathScalar(sign(a - b))
				}
			}
			overload { // With tolerance
				arg("a", real)
				arg("b", real)
				arg("epsilon", real)
				returnType = integer
				executes { args ->
					val a = (args[0] as MathScalar).value
					val b = (args[1] as MathScalar).value
					val epsilon = (args[2] as MathScalar).value
					val diff = a - b
					MathScalar(if (abs(diff) <= epsilon) 0.0 else sign(diff))
				}
			}
		}

		// TENSOR-SPECIFIC FUNCTIONS

		fun magnitude(tensor: MathTensor): MathScalar {
			var sum = 0.0
			tensor.fastForEach { sum += it * it }
			return RealScalar(sqrt(sum)) // No radix inherit because that doesn't really make sense
		}

		reduce("mag", ::magnitude)

		builtin("dist") {
			arg("a", vector)
			arg("b", vector)
			returnType = real
			executes { args ->
				val a = args[0] as MathTensor
				val b = args[1] as MathTensor

				if (a.size != b.size) throw EvaluationError(Token(TokenType.IDENTIFIER, "dist")) {
					"dist(tensor, tensor) requires both tensors to be the same size, but got " colored ColorPalette.ERROR
					a.size.toString() colored ColorPalette.TEXT
					" and " colored ColorPalette.ERROR
					b.size.toString() colored ColorPalette.TEXT
					"." colored ColorPalette.ERROR
				}

				var sum = 0.0
				var aIter = a.iterator()
				var bIter = b.iterator()
				for (i in 0 until a.size) {
					val diff = aIter.nextDouble() - bIter.nextDouble()
					sum += diff * diff
				}
				MathScalar(sqrt(sum))
			}
		}

		builtin("norm") {
			arg("a", vector)
			returnType = vector
			executes { args ->
				val vector = args[0] as MathTensor
				val magnitude = magnitude(vector).value
				if (magnitude == 0.0) throw EvaluationError(Token(TokenType.IDENTIFIER, "norm")) {
					"Cannot normalize the zero vector." colored ColorPalette.ERROR
				}
				Interpreter.map(vector, Token(TokenType.IDENTIFIER, "norm")) { it / magnitude }
			}
		}

		builtin("lerp") {
			arg("start", real or tensor)
			arg("end", real or tensor)
			arg("t", real)
			returnType = real or tensor
			executes { args ->
				val start = args[0]
				val end = args[1]
				val t = (args[2] as MathScalar).value

				val diff = Interpreter.zip(end, start, Token(TokenType.MINUS, "-")) { a, b -> a - b }

				val scaled = Interpreter.map(diff, Token(TokenType.STAR, "*")) { it * t }

				Interpreter.zip(start, scaled, Token(TokenType.PLUS, "+")) { a, b -> a + b }
			}
		}

		builtinOverloads("range") {
			// range(10) -> [0, 1, 2, ..., 9]
			// range(-10) = [0, -1, -2, ..., -9]
			overload {
				arg("endExclusive", real)
				returnType = vector
				executes { args ->
					val end = (args[0] as MathScalar).value
					if (end == 0.0) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Cannot create a range with the same start and end points." colored ColorPalette.ERROR
					}
					val size = abs(end.toInt())
					if (size > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Range too long. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						", got " colored ColorPalette.ERROR
						size.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
					val step = if (end > 0) 1 else -1
					RangeTensor(0.0, step.toDouble(), intArrayOf(size))
				}
			}
			// range(1, 10) -> [1, 2, 3, ..., 9]
			overload {
				arg("startInclusive", real)
				arg("endExclusive", real)
				returnType = vector
				executes { args ->
					val start = (args[0] as MathScalar).value
					val end = (args[1] as MathScalar).value
					if (start == end) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Cannot create a range with the same start and end points." colored ColorPalette.ERROR
					}
					val size = abs(end.toInt() - start.toInt())
					if (size > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Range too long. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						", got " colored ColorPalette.ERROR
						size.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
					val step = if (end > start) 1 else -1
					RangeTensor(start, step.toDouble(), intArrayOf(size))
				}
			}
			overload {
				arg("startInclusive", real)
				arg("endExclusive", real)
				arg("step", real)
				returnType = vector
				executes { args ->
					val start = (args[0] as MathScalar).value
					val end = (args[1] as MathScalar).value
					if (start == end) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Cannot create a range with the same start and end points." colored ColorPalette.ERROR
					}
					val step = (args[2] as MathScalar).value
					if (end >= start && step <= 0) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Step must be positive for a range from a smaller number to a bigger number." colored ColorPalette.ERROR
					}
					if (end <= start && step >= 0) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Step must be negative for a range from a bigger number to a small number." colored ColorPalette.ERROR
					}
					val size = ceil(abs((end - start) / step)).toInt()
					if (size > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Range too long. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						", got " colored ColorPalette.ERROR
						size.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
					RangeTensor(start, step, intArrayOf(size))
				}
			}
			overload {
				arg("startInclusive", real)
				arg("step", real)
				arg("shape", vector)
				returnType = tensor
				executes { args ->
					val start = (args[0] as MathScalar).value
					val step = (args[1] as MathScalar).value
					val shape = args[2] as MathTensor
					val shapeInts = IntArray(shape.size)
					shape.fastForEachIndexed { index, value ->
						if (value < 1.0) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
							"Shape values must be positive integers." colored ColorPalette.ERROR
						}
						shapeInts[index] = value.toInt()
					}
					val tens = RangeTensor(start, step, shapeInts)
					if (tens.size > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "range")) {
						"Range too long. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						", got " colored ColorPalette.ERROR
						tens.size.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
					tens
				}
			}
		}

		builtinOverloads("eye") {
			overload {
				arg("n", integer)
				returnType = matrix
				executes { args ->
					val n = (args[0] as MathScalar).value.toInt()
					if (n < 0) throw EvaluationError(Token(TokenType.IDENTIFIER, "eye")) { "Dimension cannot be negative." colored ColorPalette.ERROR }

					val data = DoubleArray(n * n)
					for (i in 0 until n) {
						data[i * n + i] = 1.0
					}
					ArrayTensor(data, intArrayOf(n, n))
				}
			}

			overload {
				arg("rows", integer)
				arg("cols", integer)
				returnType = matrix
				executes { args ->
					val rows = (args[0] as MathScalar).value.toInt()
					val cols = (args[1] as MathScalar).value.toInt()
					if (rows < 0 || cols < 0) throw EvaluationError(Token(TokenType.IDENTIFIER, "eye")) { "Dimensions cannot be negative." colored ColorPalette.ERROR }

					val data = DoubleArray(rows * cols)
					val minDim = min(rows, cols)
					for (i in 0 until minDim) {
						data[i * cols + i] = 1.0
					}
					ArrayTensor(data, intArrayOf(rows, cols))
				}
			}
		}

		builtinOverloads("zeroes") {
			overload {
				arg("n", integer)
				returnType = matrix
				executes { args ->
					val n = (args[0] as RealScalar).value.toInt()
					RangeTensor(0.0, 0.0, intArrayOf(n, n))
				}
			}
			overload {
				arg("shape", vector)
				returnType = tensor
				executes { args ->
					val shape = args[0] as MathTensor
					val shapeInts = IntArray(shape.size)
					shape.fastForEachIndexed { index, value ->
						if (value < 1.0) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
							"Shape values must be positive integers." colored ColorPalette.ERROR
						}
						shapeInts[index] = value.toInt()
					}
					if (shapeInts.reduce { acc, i -> acc * i } > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
						"Tensor too large. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
					RangeTensor(0.0, 0.0, shapeInts)
				}
			}
			overload {
				arg("dims", positiveInteger)
				isVararg = true
				returnType = tensor
				executes { args ->
					val shape = args.asSequence()
						.map { it as RealScalar }
						.map(RealScalar::value)
						.map(Double::toInt)
						.onEach {
							if (it < 1) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
								"Shape values must be positive integers." colored ColorPalette.ERROR
							}
						}
						.toCollection(IntArrayList(args.size))
						.elements()

					if (shape.reduce { acc, i -> acc * i } > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
						"Tensor too large. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}

					RangeTensor(0.0, 0.0, shape)
				}
			}
		}

		builtinOverloads("ones") {
			overload {
				arg("n", integer)
				returnType = matrix
				executes { args ->
					val n = (args[0] as RealScalar).value.toInt()
					RangeTensor(1.0, 0.0, intArrayOf(n, n))
				}
			}
			overload {
				arg("shape", vector)
				returnType = tensor
				executes { args ->
					val shape = args[0] as MathTensor
					val shapeInts = IntArray(shape.size)
					shape.fastForEachIndexed { index, value ->
						if (value < 1.0) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
							"Shape values must be positive integers." colored ColorPalette.ERROR
						}
						shapeInts[index] = value.toInt()
					}
					if (shapeInts.reduce { acc, i -> acc * i } > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
						"Tensor too large. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
					RangeTensor(1.0, 0.0, shapeInts)
				}
			}
			overload {
				arg("dims", positiveInteger)
				isVararg = true
				returnType = tensor
				executes { args ->
					val shape = args.asSequence()
						.map { it as RealScalar }
						.map(RealScalar::value)
						.map(Double::toInt)
						.onEach {
							if (it < 1) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
								"Shape values must be positive integers." colored ColorPalette.ERROR
							}
						}
						.toCollection(IntArrayList(args.size))
						.elements()

					if (shape.reduce { acc, i -> acc * i } > RangeTensor.MAX_LENGTH) throw EvaluationError(Token(TokenType.IDENTIFIER, "zeroes")) {
						"Tensor too large. Maximum length is " colored ColorPalette.ERROR
						RangeTensor.MAX_LENGTH.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}

					RangeTensor(1.0, 0.0, shape)
				}
			}
		}

		builtinOverloads("diag") {
			overload {
				// Constructs a diagonal matrix out of vector
				arg("x", vector)
				returnType = matrix
				executes { args ->
					val x = args[0] as MathTensor
					val n = x.shape[0]
					val data = DoubleArray(n * n)

					for (i in 0 until n) {
						data[i * n + i] = x.getFlat(i)
					}
					ArrayTensor(data, intArrayOf(n, n))
				}
			}
			overload {
				// Extracts diagonal vector out of matrix
				arg("x", matrix)
				returnType = vector
				executes { args ->
					val x = args[0] as MathTensor
					val rows = x.shape[0]
					val cols = x.shape[1]
					val len = min(rows, cols)
					val data = DoubleArray(len)

					val strideRow = x.strides[0]
					val strideCol = x.strides[1]

					for (i in 0 until len) {
						val flatIndex = i * strideRow + i * strideCol
						data[i] = x.getFlat(flatIndex)
					}
					ArrayTensor.vector(data)
				}
			}
		}

		// Flat data count in the tensor
		builtin("count") {
			arg("x", tensor)
			returnType = integer
			executes { args ->
				val tensor = args[0] as MathTensor
				MathScalar(tensor.size.toDouble())
			}
		}

		// The number of items in the first dimension
		builtin("len") {
			arg("x", tensor)
			returnType = positiveInteger
			executes { args ->
				val tensor = args[0] as MathTensor
				if (tensor.size == 0) throw EvaluationError(Token(TokenType.IDENTIFIER, "len")) {
					"Cannot get the length of an empty tensor." colored ColorPalette.ERROR
				}
				MathScalar(tensor.shape.first().toDouble())
			}
		}

		builtin("cross") {
			arg("a", vector)
			arg("b", vector)
			returnType = vector
			executes { args ->
				val a = args[0] as MathTensor
				val b = args[1] as MathTensor
				if (a.size != 3 || b.size != 3) throw EvaluationError(Token(TokenType.IDENTIFIER, "cross")) {
					"cross(a, b) requires both a and b to be 3-dimensional vectors, but got " colored ColorPalette.ERROR
					if (a.size != 3) {
						"a: " colored ColorPalette.TEXT
						a.size.toString() colored ColorPalette.PEACH
					}
					if (b.size != 3) {
						if (a.size != 3) {
							" and " colored ColorPalette.ERROR
						}
						"b: " colored ColorPalette.TEXT
						b.size.toString() colored ColorPalette.PEACH
					}
					"." colored ColorPalette.ERROR
				}
				val ax = a.getFlat(0)
				val ay = a.getFlat(1)
				val az = a.getFlat(2)
				val bx = b.getFlat(0)
				val by = b.getFlat(1)
				val bz = b.getFlat(2)
				ArrayTensor.vector(doubleArrayOf(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx))
			}
		}

		builtin("dot") {
			arg("a", tensor)
			arg("b", tensor)
			returnType = real
			executes { args ->
				val a = args[0] as MathTensor
				val b = args[1] as MathTensor
				MathOps.dotProduct(a, b)
			}
		}

		builtin("flatten") {
			arg("x", tensor)
			returnType = vector
			executes { args ->
				val tensor = args[0] as MathTensor
				tensor.reshape(intArrayOf(tensor.size))
			}
		}

		builtin("shape") {
			arg("x", tensor)
			returnType = vector
			executes { args ->
				val tensor = args[0] as MathTensor
				val shape = DoubleArray(tensor.rank) { tensor.shape[it].toDouble() }
				ArrayTensor.vector(shape)
			}
		}

		builtin("reverse") {
			arg("data", tensor)
			returnType = tensor
			executes { args ->
				when (val tensor = args[0] as MathTensor) {
					is RangeTensor -> {
						// Start is the old last element, step is the negative old step
						val lastValue = tensor.start + (tensor.step * (tensor.size - 1))
						RangeTensor(lastValue, -tensor.step, tensor.shape, tensor.radix)
					}

					is ArrayTensor -> {
						// This reverses the flattened data.
						// For 1D vectors, this is perfect.
						// For matrices, this is equivalent to Matlab's flip(A(:))
						ArrayTensor(tensor.data.reversedArray(), tensor.shape, tensor.radix)
					}
				}
			}
		}

		// Idk how to handle this
//		builtin("transpose") {
//			arg("x", tensor)
//			returnType = tensor
//			executes { args ->
//				val tensor = args[0] as MathTensor
//				tensor.transpose()
//			}
//		}

		// FUNCTIONAL-STYLE FUNCTIONS

		builtin("map") {
			arg("data", tensor)
			arg("transform", function)
			returnType = tensor
			executes { args ->
				val tensor = args[0] as MathTensor
				val func = args[1] as MathFunction

				val newData = DoubleArrayList(tensor.size)
				val input = ObjectArrayList<MathScalar>(arrayOfNulls(1))
				val backing = input.elements() as Array<Any?>
				tensor.fastForEach { d ->
					backing[0] = MathScalar(d)
					val overload = func.selectOverload(this, input) ?: throw EvaluationError(Token(TokenType.IDENTIFIER, "map")) {
						"Function provided to map must accept a scalar." colored ColorPalette.ERROR
					}

					val result = overload.executable(this, input)
					if (result !is MathScalar) throw EvaluationError(Token(TokenType.IDENTIFIER, "map")) {
						"Function provided to map must return a scalar." colored ColorPalette.ERROR
					}
					newData += result.value
				}

				ArrayTensor(newData.elements(), tensor.shape)
			}
		}

		builtin("filter") {
			arg("data", tensor)
			arg("predicate", function)
			returnType = tensor
			executes { args ->
				val tensor = args[0] as MathTensor
				val func = args[1] as MathFunction

				val resultList = DoubleArrayList(tensor.size) // Start with max size
				val input = ObjectArrayList<MathScalar>(arrayOfNulls(1))
				val backing = input.elements() as Array<Any?>
				tensor.fastForEach { d ->
					backing[0] = MathScalar(d)
					val overload = func.selectOverload(this, input) ?: throw EvaluationError(Token(TokenType.IDENTIFIER, "filter")) {
						"Function provided to filter must accept a scalar." colored ColorPalette.ERROR
					}

					val keep = when (val result = overload.executable(this, input)) {
						is MathScalar -> result.value != 0.0
						is MathBoolean -> result.value
						else -> false
					}
					if (keep) resultList += d
				}

				// We have to flatten because there is no way to know the new shape
				ArrayTensor.vector(resultList.toDoubleArray()) // This also trims to size
			}
		}


		builtin("fold") {
			arg("data", tensor)
			arg("initialValue", real)
			arg("reducer", function)
			returnType = real
			executes { args ->
				val data = args[0] as MathTensor
				val initialValue = args[1] as MathScalar
				val reducer = args[2] as MathFunction

				var accumulator = initialValue.value
				val input = ObjectArrayList<MathScalar>(arrayOfNulls(2))
				val backing = input.elements() as Array<Any?>
				data.fastForEach { d ->
					backing[0] = MathScalar(accumulator)
					backing[1] = MathScalar(d)
					val overload = reducer.selectOverload(this, input) ?: throw EvaluationError(Token(TokenType.IDENTIFIER, "fold")) {
						"Function provided to fold must accept two scalars." colored ColorPalette.ERROR
					}

					val result = overload.executable(this, input)
					if (result !is MathScalar) throw EvaluationError(Token(TokenType.IDENTIFIER, "fold")) {
						"Function provided to fold must return a scalar." colored ColorPalette.ERROR
					}
					accumulator = result.value
				}
				MathScalar(accumulator)
			}
		}

		// Starts from the first element, as opposed to fold which starts from an initial value
		builtin("reduce") {
			arg("data", tensor)
			arg("reducer", function)
			returnType = real
			executes { args ->
				val data = args[0] as MathTensor
				val reducer = args[1] as MathFunction

				if (data.size == 0) throw EvaluationError(Token(TokenType.IDENTIFIER, "reduce")) {
					"Cannot reduce an empty tensor." colored ColorPalette.ERROR
				}

				var accumulator = data.getFlat(0)

				val input = ObjectArrayList<MathScalar>(arrayOfNulls(2))
				val backing = input.elements() as Array<Any?>
				data.fastForEachIndexed { i, d ->
					if (i == 0) return@fastForEachIndexed // Skip the first element since it's in the accumulator
					backing[0] = MathScalar(accumulator)
					backing[1] = MathScalar(d)

					val overload = reducer.selectOverload(this, input) ?: throw EvaluationError(Token(TokenType.IDENTIFIER, "reduce")) {
						"Function provided to reduce must accept two scalars." colored ColorPalette.ERROR
					}

					val result = overload.executable(this, input)
					if (result !is MathScalar) throw EvaluationError(Token(TokenType.IDENTIFIER, "reduce")) {
						"Function provided to reduce must return a scalar." colored ColorPalette.ERROR
					}
					accumulator = result.value
				}
				MathScalar(accumulator)
			}
		}

		builtin("any") {
			arg("data", tensor)
			arg("predicate", function)
			returnType = boolean
			executes { args ->
				val tensor = args[0] as MathTensor
				val predicate = args[1] as MathFunction
				val input = ObjectArrayList<MathScalar>(arrayOfNulls(1))
				val backing = input.elements() as Array<Any?>
				tensor.fastForEach { d ->
					backing[0] = MathScalar(d)
					val overload = predicate.selectOverload(this, input) ?: throw EvaluationError(Token(TokenType.IDENTIFIER, "any")) {
						"Function provided to any must accept a scalar." colored ColorPalette.ERROR
					}

					val isTrue = when (val result = overload.executable(this, input)) {
						is MathScalar -> result.value != 0.0
						is MathBoolean -> result.value
						else -> false
					}
					if (isTrue) return@executes MathBoolean(true)
				}
				MathBoolean(false)
			}
		}

		builtin("all") {
			arg("data", tensor)
			arg("predicate", function)
			returnType = boolean
			executes { args ->
				val tensor = args[0] as MathTensor
				val predicate = args[1] as MathFunction
				val input = ObjectArrayList<MathScalar>(arrayOfNulls(1))
				val backing = input.elements() as Array<Any?>
				tensor.fastForEach { d ->
					backing[0] = MathScalar(d)
					val overload = predicate.selectOverload(this, input) ?: throw EvaluationError(Token(TokenType.IDENTIFIER, "any")) {
						"Function provided to any must accept a scalar." colored ColorPalette.ERROR
					}

					val isTrue = when (val result = overload.executable(this, input)) {
						is MathScalar -> result.value != 0.0
						is MathBoolean -> result.value
						else -> false
					}
					if (!isTrue) return@executes MathBoolean(false)
				}
				MathBoolean(true)
			}
		}

		// MINECRAFT FUNCTIONS

		builtinOverloads("shulkers") {
			// Counts shulker boxes required to hold a number of items
			overload {
				arg("itemCount", integer)
				returnType = vector // [numOfBoxes, numOfStacks, numOfItems]
				executes { args ->
					when (val itemCount = (args[0] as MathScalar).value) {
						in Double.NEGATIVE_INFINITY..<0.0 -> throw EvaluationError(Token(TokenType.IDENTIFIER, "shulkers")) {
							"Item count cannot be negative." colored ColorPalette.ERROR
						}

						Double.POSITIVE_INFINITY -> ArrayTensor.vector(doubleArrayOf(Double.POSITIVE_INFINITY, 26.0, 63.0))
						else -> {
							val boxes = itemCount / 1728
							val remainderAfterBoxes = itemCount % 1728
							val stacks = remainderAfterBoxes / 64
							val items = remainderAfterBoxes % 64

							ArrayTensor.vector(doubleArrayOf(boxes, stacks, items))
						}
					}
				}
			}
			overload {
				arg("itemCount", integer)
				arg("stackLimit", positiveInteger)
				returnType = vector // [numOfBoxes, numOfStacks, numOfItems]
				executes { args ->
					val stackLimit = (args[1] as MathScalar).value.toInt()
					if (stackLimit == Int.MAX_VALUE) throw EvaluationError(Token(TokenType.IDENTIFIER, "shulkers")) {
						"Stack limit must be less than 2,147,483,648." colored ColorPalette.ERROR
					}
					when (val itemCount = (args[0] as MathScalar).value) {
						in Double.NEGATIVE_INFINITY..<0.0 -> throw EvaluationError(Token(TokenType.IDENTIFIER, "shulkers")) {
							"Item count cannot be negative." colored ColorPalette.ERROR
						}

						Double.POSITIVE_INFINITY -> ArrayTensor.vector(doubleArrayOf(Double.POSITIVE_INFINITY, 26.0, 63.0))
						else -> {
							val boxes = itemCount / (stackLimit*27)
							val remainderAfterBoxes = itemCount % (stackLimit*27)
							val stacks = remainderAfterBoxes / stackLimit
							val items = remainderAfterBoxes % stackLimit

							ArrayTensor.vector(doubleArrayOf(boxes, stacks, items))
						}
					}
				}
			}
		}



		builtin("stacks") {
			// Counts stacks of items required to hold a number of items
			arg("itemCount", integer)
			returnType = tensor // [numOfStacks, remainder]
			executes { args ->
				val itemCount = (args[0] as MathScalar).value.toInt()
				val stacks = itemCount / 64 // Int math so we floor it automatically
				val remainder = itemCount % 64
				ArrayTensor.vector(doubleArrayOf(stacks.toDouble(), remainder.toDouble()))
			}
		}

		builtinOverloads("nether") {
			overload {
				arg("pos", vector)
				returnType = vector
				executes { args ->
					val pos = args[0] as MathTensor
					when (pos.size) {
						// X, Z - Since Y is not scaled, we might as well accept tensors with it omitted too.
						2 -> ArrayTensor.vector(doubleArrayOf(pos.getFlat(0) / 8.0, pos.getFlat(2) / 8.0)) // We respond in the same format
						// X, Y, Z - this is more likely to be used since `pos` gives the xyz coords.
						3 -> ArrayTensor.vector(doubleArrayOf(pos.getFlat(0) / 8.0, pos.getFlat(1), pos.getFlat(2) / 8.0))
						else -> throw EvaluationError(Token(TokenType.IDENTIFIER, "nether")) {
							"Position must be either 2D (x, z) or 3D (x, y, z)." colored ColorPalette.ERROR
						}
					}
				}
			}
			overload {
				arg("x", real)
				arg("z", real)
				returnType = vector
				executes { args ->
					val x = args[0] as RealScalar
					val z = args[0] as RealScalar
					ArrayTensor.vector(doubleArrayOf(x.value / 8.0, z.value / 8.0))
				}
			}
			overload {
				arg("x", real)
				arg("y", real)
				arg("z", real)
				returnType = vector
				executes { args ->
					val x = args[0] as RealScalar
					val y = args[0] as RealScalar
					val z = args[0] as RealScalar
					ArrayTensor.vector(doubleArrayOf(x.value / 8.0, y.value, z.value / 8.0))
				}
			}
		}
		builtinOverloads("overworld") {
			overload {
				arg("pos", vector)
				returnType = vector
				executes { args ->
					val pos = args[0] as MathTensor
					when (pos.size) {
						// X, Z - Since Y is not scaled, we might as well accept tensors with it omitted too.
						2 -> ArrayTensor.vector(doubleArrayOf(pos.getFlat(0) * 8.0, pos.getFlat(2) * 8.0)) // We respond in the same format
						// X, Y, Z - this is more likely to be used since `pos` gives the xyz coords.
						3 -> ArrayTensor.vector(doubleArrayOf(pos.getFlat(0) * 8.0, pos.getFlat(1), pos.getFlat(2) * 8.0))
						else -> throw EvaluationError(Token(TokenType.IDENTIFIER, "nether")) {
							"Position must be either 2D (x, z) or 3D (x, y, z)." colored ColorPalette.ERROR
						}
					}
				}
			}
			overload {
				arg("x", real)
				arg("z", real)
				returnType = vector
				executes { args ->
					val x = args[0] as RealScalar
					val z = args[0] as RealScalar
					ArrayTensor.vector(doubleArrayOf(x.value * 8.0, z.value * 8.0))
				}
			}
			overload {
				arg("x", real)
				arg("y", real)
				arg("z", real)
				returnType = vector
				executes { args ->
					val x = args[0] as RealScalar
					val y = args[0] as RealScalar
					val z = args[0] as RealScalar
					ArrayTensor.vector(doubleArrayOf(x.value * 8.0, y.value, z.value * 8.0))
				}
			}
		}

		builtin("xpToLevel") {
			arg("xp", integer)
			returnType = integer
			executes { args ->
				when (val xp = (args[0] as MathScalar).value) {
					Double.POSITIVE_INFINITY -> MathScalar(xp) // Infinite xp is logically infinite level, even if the xp count is limited to 2^31-1.
					in Double.NEGATIVE_INFINITY..0.0 -> throw EvaluationError(Token(TokenType.IDENTIFIER, "xpToLevel")) {
						"XP cannot be negative." colored ColorPalette.ERROR
					}

					in 0.0..<353.0 -> MathScalar(floor(sqrt(xp + 9.0) - 3.0))
					in 353.0..<1508.0 -> MathScalar(floor(8.1 + sqrt(0.4 * (xp - 195.975))))
					in 1508.0..Int.MAX_VALUE.toDouble() -> MathScalar(floor((325.0 / 18.0) + sqrt(2.0 * (xp - 54215.0 / 72.0) / 9.0)))
					in Int.MAX_VALUE.toDouble()..Double.MAX_VALUE -> throw EvaluationError(Token(TokenType.IDENTIFIER, "xpToLevel")) {
						"XP cannot be greater than " colored ColorPalette.ERROR
						Int.MAX_VALUE.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}

					else -> throw EvaluationError(Token(TokenType.IDENTIFIER, "xpToLevel")) {
						"Unrecognized XP value: " colored ColorPalette.ERROR
						xp.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
				}
			}
		}

		builtin("levelToXp") {
			arg("level", integer)
			returnType = integer
			executes { args ->
				when (val level = (args[0] as MathScalar).value) {
					Double.POSITIVE_INFINITY -> MathScalar(level) // Infinite level is logically infinite xp, even if the level count is limited to 2^31-1.

					in 0.0..<17.0 -> MathScalar(floor(sqrt(level + 9.0) - 3.0))
					in 17.0..<32.0 -> MathScalar(floor(8.1 + sqrt(0.4 * (level - 195.975))))
					// Max level possible via survival is like 21863.3850539168771057589993072313465736139112223325424686728988470659755013154193542345457429066803, but the decimal points don't really matter here
					// This is due to experience per level being an integer, and after this level the xp hits Int.MAX_VALUE and can't proceed.
					// We don't have a reason to support levels not possible to reach in survival, so this is capped at 21863.
					in 32.0..21863.0 -> MathScalar(floor((325.0 / 18.0) + sqrt(2.0 * (level - 54215.0 / 72.0) / 9.0)))
					in 21863.0..Double.MAX_VALUE -> throw EvaluationError(Token(TokenType.IDENTIFIER, "xpToLevel")) {
						"Level cannot be greater than " colored ColorPalette.ERROR
						"21863" colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}

					else -> throw EvaluationError(Token(TokenType.IDENTIFIER, "xpToLevel")) {
						"Unrecognized level value: " colored ColorPalette.ERROR
						level.toString() colored ColorPalette.TEXT
						"." colored ColorPalette.ERROR
					}
				}
			}
		}

		builtin("chunk") {
			arg("pos", vector)
			returnType = vector
			executes { args ->
				val pos = args[0] as MathTensor
				if (pos.size != 3) throw EvaluationError(Token(TokenType.IDENTIFIER, "chunk")) {
					"chunk(pos) requires a 3-dimensional position vector, but got " colored ColorPalette.ERROR
					pos.size.toString() colored ColorPalette.TEXT
					"." colored ColorPalette.ERROR
				}
				val chunkCoords = DoubleArray(pos.size)
				pos.fastForEachIndexed { i, v ->
					chunkCoords[i] = floor(v / 16.0)
				}
				ArrayTensor.vector(chunkCoords)
			}
		}

		// TYPE SYSTEM FUNCTIONS
		builtin("isScalar") {
			arg("obj", any)
			returnType = boolean
			executes { args -> MathBoolean(args[0] is MathScalar) }
		}
		builtin("isTensor") {
			arg("obj", any)
			returnType = boolean
			executes { args -> MathBoolean(args[0] is MathTensor) }
		}
		builtin("isBoolean") {
			arg("obj", any)
			returnType = boolean
			executes { args -> MathBoolean(args[0] is MathBoolean) }
		}
		builtin("isFunction") {
			arg("obj", any)
			returnType = boolean
			executes { args -> MathBoolean(args[0] is MathFunction) }
		}
	}

	override fun contains(key: String): Boolean = key in values

	override fun get(key: String): MathObject? = values[key]

	private fun createGlobalEnvironment(method: EnvBuilder.() -> Unit) = EnvBuilder().apply(method).build()

	/**
	 * Registers a function which reduces a vector or a vararg of scalars to a scalar.
	 */
	context(builder: EnvBuilder)
	private inline fun reduce(name: String, crossinline operation: (MathTensor) -> MathScalar) {
		builtinOverloads(name) {
			overload {
				returnType = real
				arg("vec", vector)
				executes { args ->
					val vector = args.first() as MathTensor
					operation(vector)
				}
			}
			overload {
				returnType = real
				isVararg = true
				arg("values", real)
				executes { args ->
					val values = DoubleArray(args.size) { (args[it] as MathScalar).value }
					operation(ArrayTensor.vector(values))
				}
			}
		}
	}

	/**
	 * Registers multiple aliases for a single function.
	 */
	private inline fun aliases(vararg aliases: String, registerFunction: (name: String) -> Unit) {
		aliases.forEach { alias -> registerFunction(alias) }
	}

	context(builder: EnvBuilder)
	private infix fun String.to(namedValue: (String) -> MathObject) {
		builder.put(this, namedValue(this))
	}

	context(builder: EnvBuilder)
	private inline fun scalar(name: String, value: Double) {
		builder.put(name, MathScalar(value))
	}

	context(builder: EnvBuilder)
	private inline fun computed(name: String, noinline value: () -> MathObject) {
		builder.put(name, ComputedProperty(name, value))
	}

	/**
	 * Registers a simple unary function (Scalar -> Scalar, Tensor -> Tensor).
	 */
	context(builder: EnvBuilder)
	private fun unary(name: String, function: Double2DoubleFunction) {
		builtinOverloads(name) {
			val token = Token(TokenType.IDENTIFIER, name)
			overload {
				arg("x", real)
				returnType = real
				executes { Interpreter.map(it[0], token, function) }
			}
			overload {
				arg("x", tensor)
				returnType = tensor
				executes { Interpreter.map(it[0], token, function) }
			}
		}
	}

	/**
	 * Registers a trig function. Inputs are converted to radians if config requires it.
	 */
	context(builder: EnvBuilder)
	private fun trig(name: String, function: Double2DoubleFunction) {
		unary(name) { valIn ->
			function.get(config.convertToRadians(valIn))
		}
	}

	/**
	 * Registers a trig function which checks some other function to be within -EPSILON and EPSILON of 0.
	 */
	context(builder: EnvBuilder)
	private fun trig(name: String, check: Double2DoubleFunction, function: Double2DoubleFunction) {
		unary(name) { valIn ->
			val radians = config.convertToRadians(valIn)
			when (check.get(radians)) {
				in 0.0..EPSILON -> Double.POSITIVE_INFINITY
				in -EPSILON..0.0 -> Double.NEGATIVE_INFINITY
				else -> function.get(radians)
			}
		}
	}

	/**
	 * Registers an inverse trig function. Outputs are converted from radians if config requires it.
	 */
	context(builder: EnvBuilder)
	private fun arcTrig(name: String, function: Double2DoubleFunction) {
		unary(name) { valIn ->
			config.convertFromRadians(function.get(valIn))
		}
	}

	/**
	 * Registers an inverse trig function which checks some other function to be within -EPSILON and EPSILON of 0.
	 */
	context(builder: EnvBuilder)
	private fun arcTrig(name: String, check: Double2DoubleFunction, function: Double2DoubleFunction) {
		unary(name) { valIn ->
			when (check.get(config.convertToRadians(valIn))) { // The checked function is a trig function too, so it needs the converted value
				in 0.0..EPSILON -> Double.POSITIVE_INFINITY
				in -EPSILON..0.0 -> Double.NEGATIVE_INFINITY
				else -> config.convertFromRadians(function.get(valIn)) // However, this function is an arcTrig function, so it needs the original value
			}
		}
	}

	/**
	 * Registers a builtin function with multiple overloads.
	 */
	context(envBuilder: EnvBuilder)
	private inline fun builtinOverloads(name: String, builder: BuiltinFunctionBuilder.() -> Unit) = envBuilder.put(name, BuiltinFunctionBuilder(name).apply(builder).build())

	/**
	 * Registers a builtin function with a single overload.
	 */
	context(envBuilder: EnvBuilder)
	private inline fun builtin(name: String, builder: OverloadBuilder.() -> Unit) = envBuilder.put(name, BuiltinFunctionBuilder(name).apply { overload(builder) }.build())
}

class BuiltinFunctionBuilder(val name: String) {
	private val overloads = mutableListOf<FunctionOverload>()

	fun addOverload(overload: FunctionOverload) {
		overloads += overload
	}

	inline fun overload(setup: OverloadBuilder.() -> Unit) {
		addOverload(OverloadBuilder().apply(setup).build())
	}

	fun build() = BuiltinFunction(name, overloads)
}

class OverloadBuilder {
	private val parameters = mutableListOf<FunctionParameter>()
	var returnType: TypeConstraint = AnyConstraint
	var isVararg = false
	private var executable: (Interpreter.(args: List<MathObject>) -> MathObject)? = null

	// Helper methods
	inline val scalar get() = ScalarConstraint
	inline val positiveInteger get() = PositiveIntegerConstraint
	inline val integer get() = IntegerConstraint
	inline val real get() = RealConstraint // Not useful for now, it'll make more sense when we add complex numbers
	inline val tensor get() = TensorConstraint
	inline val boolean get() = BooleanConstraint
	inline val function get() = FunctionConstraint
	inline val vector get() = VectorConstraint
	inline val matrix get() = MatrixConstraint
	inline val any get() = AnyConstraint

	fun arg(name: String, type: TypeConstraint = AnyConstraint) {
		parameters += FunctionParameter(name, type)
	}

	fun executes(block: Interpreter.(args: List<MathObject>) -> MathObject) {
		executable = block
	}

	fun build(): FunctionOverload {
		requireNotNull(executable) { "Overload must have an executable block." }
		return FunctionOverload(
			FunctionSignature(parameters, returnType, isVararg),
			executable!!
		)
	}
}
