package ca.rttv.chatcalc

import ca.rttv.chatcalc.BuiltinFunctions.factorial
import ca.rttv.chatcalc.BuiltinFunctions.log
import ca.rttv.chatcalc.BuiltinFunctions.mod
import ca.rttv.chatcalc.config.ConfigManager.config
import net.minecraft.util.math.MathHelper
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

class NibbleMathEngine : MathEngine {
	private lateinit var bytes: ByteArray
	private var idx: Int = 0
	private lateinit var params: Array<FunctionParameter?>
	private var abs: Boolean = false

	override fun eval(input: String, parameters: Array<FunctionParameter?>): Double {
		bytes = (fixParenthesis(input) + "\u0000").toByteArray(StandardCharsets.US_ASCII) // we shouldn't encounter unicode in our math
		idx = 0
		abs = false
		params = parameters
		val result = expression()
		require(idx + 1 == bytes.size) { "Evaluation had unexpected remaining characters" }
		return result
	}

	private fun fixParenthesis(input: String): String {
		var openingMissing = 0
		for (element in input) {
			if (element == ')') openingMissing++
			else if (element == '(') break
		}

		var closingMissing = 0
		for (char in input.reversed()) {
			if (char == '(') closingMissing++
			else if (char == ')') break
		}

		val fixedInput = "(".repeat(openingMissing) + input + ")".repeat(closingMissing)

		var opening = 0
		var closing = 0
		for (element in fixedInput) {
			if (element == '(') opening++
			else if (element == ')') closing++
		}

		return "(".repeat(max(0, (closing - opening))) + fixedInput + ")".repeat(max(0, opening - closing))
	}

	private fun bite(bite: Char): Boolean { // pun intended
		return if (bytes[idx] == bite.code.toByte()) {
			idx++
			true
		} else false
	}

	private fun expression(): Double {
		var x = modulo()
		while (true) {
			if (bite('+')) x += modulo()
			else if (bite('-')) x -= modulo()
			else return x
		}
	}

	private fun modulo(): Double {
		var x = term()
		while (true) {
			if (bite('%')) x = mod(x, term())
			else return x
		}
	}

	private fun term(): Double {
		var x = grouping()
		while (true) {
			if (bite('*')) x *= grouping()
			else if (bite('/')) x /= grouping()
			else return x
		}
	}

	private fun grouping(): Double {
		var sign = 0L
		while (bytes[idx] == '+'.code.toByte() || bytes[idx] == '-'.code.toByte()) {
			if (bytes[idx++] == '-'.code.toByte()) {
				sign = sign xor Long.MIN_VALUE
			}
		}

		var x = part()
		while (isStartOfPart(bytes[idx])) {
			x *= part()
		}

		return Double.fromBits(x.toBits() xor sign)
	}

	private fun part(): Double {
		var x: Double
		run {
			if (bite('(')) {
				val absBefore = abs
				abs = false
				x = expression()
				require(bite(')')) { "Expected closing parenthesis" }
				abs = absBefore
				return@run
			}
			if (!abs && bite('|')) {
				val absBefore = abs
				abs = true
				x = abs(expression())
				require(bite('|')) { "Expected closing absolute value character" }
				abs = absBefore
				return@run
			}

			if (bytes[idx].toInt() == '0'.code) {
				when (bytes[idx + 1].toInt()) {
					'x'.code -> {
						idx += 2
						val start = idx
						while (bytes[idx].toInt() in ('0'.code..'9'.code) || bytes[idx].toInt() in ('a'.code..'f'.code) || bytes[idx].toInt() in ('A'.code..'F'.code)) idx++
						if (idx != start) {
							x = String(bytes, start, idx - start, StandardCharsets.US_ASCII).toInt(16).toDouble()
							return@run
						}
					}

					'b'.code -> {
						idx += 2
						val start = idx
						while (bytes[idx].toInt() in ('0'.code..'1'.code)) idx++
						if (idx != start) {
							x = String(bytes, start, idx - start, StandardCharsets.US_ASCII).toInt(2).toDouble()
							return@run
						}
					}

					'o'.code -> {
						idx += 2
						val start = idx
						while (bytes[idx].toInt() in ('0'.code..'7'.code)) idx++
						if (idx != start) {
							x = String(bytes, start, idx - start, StandardCharsets.US_ASCII).toInt(8).toDouble()
							return@run
						}
					}
				}
			}

			if (bytes[idx].toInt() in ('0'.code..'9'.code) || bytes[idx] == '.'.code.toByte() || bytes[idx] == ','.code.toByte()) {
				val start = idx
				while (bytes[idx].toInt() in ('0'.code..'9'.code) || bytes[idx] == '.'.code.toByte() || bytes[idx] == ','.code.toByte()) idx++
				x = String(bytes, start, idx - start, StandardCharsets.US_ASCII).replace(",", "").toDouble()
				return@run
			}

			if (bytes[idx].toInt() in ('a'.code..'z'.code) || bytes[idx].toInt() in ('A'.code..'Z'.code)) {
				var start = idx
				while (bytes[idx].toInt() in ('a'.code..'z'.code) || bytes[idx] in ('A'.code..'Z'.code)) idx++
				if (bytes[idx] == '_'.code.toByte()) idx++
				val str = String(bytes, start, idx - start, StandardCharsets.US_ASCII)

				if ((config.functions.asSequence().map(CustomFunction::name) + BuiltinFunctions.FUNCTIONS.keys.asSequence()).none(str::startsWith)) {
					for (param in params) {
						if (str.startsWith(param!!.name)) {
							idx -= str.length - param.name.length
							x = param.value
							return@run
						}
					}

					for ((name, value) in BuiltinConstants.CONSTANTS) {
						if (str.startsWith(name)) {
							idx -= str.length - name.length
							x = value.asDouble
							return@run
						}
					}

					for (constant in config.constants) {
						if (str.startsWith(constant.name)) {
							idx -= str.length - constant.name.length
							x = constant.get()
							return@run
						}
					}
				}

				if (str == "sum") {
					val absBefore = abs
					abs = false
					require(bite('(')) { "Expected parenthesis for summation" }
					start = idx
					while (((bytes[idx] >= 'a'.code.toByte()) and (bytes[idx] <= 'z'.code.toByte())) or ((bytes[idx] >= 'A'.code.toByte()) and (bytes[idx] <= 'Z'.code.toByte()))) idx++
					val param = String(bytes, start, idx - start, StandardCharsets.US_ASCII)
					require(bite('=')) { "Expected starting value for parameter in summation" }
					val lowerBound = MathHelper.floor(expression())
					require(bite(';')) { "Expected multiple parameters in summation" }
					val upperBound = MathHelper.floor(expression())
					require(bite(';')) { "Expected multiple parameters in summation" }
					start = idx
					var parenthesis = 0
					while (parenthesis >= 0) {
						when (val c = bytes[idx]) {
							'('.code.toByte() -> parenthesis++
							')'.code.toByte() -> parenthesis--
							else -> require(c != '\u0000'.code.toByte()) { "Expected closing parenthesis in summation" }
						}
						idx++
					}
					val expression = String(bytes, start, idx - 1 - start, StandardCharsets.US_ASCII)
					var sum = 0.0
					val summationParams = arrayOfNulls<FunctionParameter>(params.size + 1)
					System.arraycopy(params, 0, summationParams, 0, params.size)
					for (i in lowerBound..upperBound) {
						summationParams[params.size] = FunctionParameter(param, i.toDouble())
						sum += MathEngine.of().eval(expression, summationParams)
					}
					abs = absBefore
					x = sum
					return@run
				}

				if (str == "prod") {
					val absBefore = abs
					abs = false
					require(bite('(')) { "Expected parenthesis for product" }
					start = idx
					while (((bytes[idx] >= 'a'.code.toByte()) and (bytes[idx] <= 'z'.code.toByte())) or ((bytes[idx] >= 'A'.code.toByte()) and (bytes[idx] <= 'Z'.code.toByte()))) idx++
					val param = String(bytes, start, idx - start, StandardCharsets.US_ASCII)
					require(bite('=')) { "Expected starting value for parameter in product" }
					val lowerBound = MathHelper.floor(expression())
					require(bite(';')) { "Expected multiple parameters in product" }
					val upperBound = MathHelper.floor(expression())
					require(bite(';')) { "Expected multiple parameters in product" }
					start = idx
					var parenthesis = 0
					while (parenthesis >= 0) {
						when (val c = bytes[idx]) {
							'('.code.toByte() -> parenthesis++
							')'.code.toByte() -> parenthesis--
							else -> require(c != '\u0000'.code.toByte()) { "Expected closing parenthesis in product" }
						}
						idx++
					}
					val expression = String(bytes, start, idx - 1 - start, StandardCharsets.US_ASCII)
					var prod = 1.0
					val productParams = arrayOfNulls<FunctionParameter>(params.size + 1)
					System.arraycopy(params, 0, productParams, 0, params.size)
					for (i in lowerBound..upperBound) {
						productParams[params.size] = FunctionParameter(param, i.toDouble())
						prod *= MathEngine.of().eval(expression, productParams)
					}
					abs = absBefore
					x = prod
					return@run
				}

				if (str == "log_") {
					val absBefore = abs
					abs = false
					// cannot be grouping because `log_2(3)` becomes `log_6`
					val base = part()
					require(bite('(')) { "Expected parenthesis for logarithmic function" }
					val value = expression()
					require(bite(')')) { "Expected closing parenthesis for logarithmic function" }
					abs = absBefore
					x = log(base, value)
					return@run
				}

				val absBefore = abs
				abs = false
				var paramCount = 1
				// cannot be grouping because `sqrt^2(3)` becomes `sqrt^6`
				val exponent = if (bite('^')) part() else 1.0
				require(bite('(')) { "Expected parenthesis for function" }
				var depth = 0
				val before = idx

				while (idx < bytes.size) {
					when {
						bytes[idx] == ChatCalc.SEPARATOR_CHAR.code.toByte() && depth == 0 -> paramCount++
						bytes[idx] == ')'.code.toByte() -> if (depth-- == 0) break
						bytes[idx] == '('.code.toByte() -> depth++
					}
					idx++
				}
				idx = before
				val values = DoubleArray(paramCount)
				var valueCount = 0
				while (true) {
					require(!bite('\u0000')) { "Expected closing parenthesis for function" }
					values[valueCount++] = expression()
					if (bite(')')) break
					require(bite(';')) { "Expected that a semicolon exists between the parameters" }
				}
				abs = absBefore
				x = BuiltinFunctions.apply(str, *values).pow(exponent)
				return@run
			}
			throw IllegalArgumentException("Expected a valid character for equation, not '" + bytes[idx].toInt().toChar() + "' (at index " + idx + ")")
		}

		if (bite('!')) x = factorial(x)
		if (bite('^')) {
			val absBefore = abs
			abs = false
			x = x.pow(grouping())
			abs = absBefore
		}

		return x
	}

	private fun isStartOfPart(byte: Byte): Boolean {
		val char = byte.toInt().toChar()
		return char in 'a'..'z' || char in '0'..'9' || char == '(' || ((char == '|') && !abs)
	}

	override fun toString(): String = String(bytes, idx, bytes.size - idx - 1)
}
