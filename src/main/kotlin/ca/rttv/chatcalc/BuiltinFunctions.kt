package ca.rttv.chatcalc

import ca.rttv.chatcalc.BuiltinFunctions.Function
import ca.rttv.chatcalc.config.ConfigManager.config
import com.google.common.math.DoubleMath
import it.unimi.dsi.fastutil.doubles.DoubleUnaryOperator
import net.minecraft.util.math.MathHelper
import kotlin.math.*

object BuiltinFunctions {
	fun apply(function: String, vararg values: Double) = FUNCTIONS[function]?.let {
		it(values) ?: throw IllegalArgumentException("Invalid number of arguments for function $function")
	}
		?: config.functions.firstOrNull { it.name == function }?.get(values)
		?: throw IllegalArgumentException("Function $function not found")

	val FUNCTIONS: MutableMap<String, Function> = mutableMapOf(
		"sqrt" to simple(::sqrt),
		"cbrt" to simple(::cbrt),

		"sin" to simple { sin(config.convertToRadians(it)) },
		"cos" to simple { cos(config.convertToRadians(it)) },
		"tan" to simple { custom_tan(config.convertToRadians(it)) },
		"csc" to simple { 1 / sin(config.convertToRadians(it)) },
		"sec" to simple { 1 / cos(config.convertToRadians(it)) },
		"cot" to simple { 1 / custom_tan(config.convertToRadians(it)) },

		"arcsin" to simple { config.convertFromRadians(asin(it)) },
		"asin" to simple { config.convertFromRadians(asin(it)) },

		"arccos" to simple { config.convertFromRadians(acos(it)) },
		"acos" to simple { config.convertFromRadians(acos(it)) },

		"arctan" to simple { config.convertFromRadians(atan(it)) },
		"atan" to simple { config.convertFromRadians(atan(it)) },

		"arccsc" to simple { config.convertFromRadians(asin(1 / it)) },
		"acsc" to simple { config.convertFromRadians(asin(1 / it)) },

		"arcsec" to simple { config.convertFromRadians(acos(1 / it)) },
		"asec" to simple { config.convertFromRadians(acos(1 / it)) },

		"arccot" to simple { config.convertFromRadians(atan(1 / it)) },
		"acot" to simple { config.convertFromRadians(atan(1 / it)) },

		"floor" to simple(::floor),
		"ceil" to simple(::ceil),
		"round" to simple(::round),
		"abs" to simple(::abs),
		"log" to simple(::log10),
		"ln" to simple(::ln),
		"exp" to simple(::exp),

		"sgn" to simple { x -> sign(x).let { if (it.isNaN()) 0.0 else it } },
		"min" to Function { it.minOrNull() },
		"max" to Function { it.maxOrNull() },
		"gcf" to Function { it.reduce { a, b -> gcf(a, b) } },
		"lcm" to Function { it.reduce { a, b -> lcm(a, b) } },
		"clamp" to Function { if (it.size == 3) MathHelper.clamp(it[0], it[1], it[2]) else null },

		"cmp" to Function { //What is this monstrosity?
			if (it.size in 2..3) {
				if (abs(it[0] - it[1]) <= if (it.size == 2) 0.0 else it[2]) {
					0.0
				} else {
					if (it[0] < it[1]) {
						-1.0
					} else {
						(if (it[0] > it[1]) {
							1.0
						} else {
							0.0
						})
					}
				}
			} else null
		}
	)

	fun gcf(a: Double, b: Double): Double {
		var a = a
		var b = b
		if (b > a) {
			val t = a
			a = b
			b = t
		}

		while (b != 0.0) {
			val t = b
			b = mod(a, b)
			a = t
		}

		return a
	}

	fun lcm(a: Double, b: Double) = (a * b) / gcf(a, b)

	fun log(base: Double, value: Double) = ln(value) / ln(base)

	fun mod(a: Double, b: Double) = a % b + (if (a.sign != b.sign) b else 0.0)

	fun factorial(x: Double) = if ((x % 1.0 == 0.0) and (x >= 1.0)) DoubleMath.factorial(x.toInt())
	else sqrt(2.0 * Math.PI * x) * (x / Math.E).pow(x) * (((1.0 + 1.0 / (12.0 * x) + 1.0 / (288.0 * x * x)) - 139.0 / (51840.0 * x * x * x) - 571.0 / (2488320.0 * x * x * x * x)) + 163879.0 / (209018880.0 * x * x * x * x * x) + 5246819.0 / (75246796800.0 * x * x * x * x * x * x)
			- 534703531.0 / (902961561600.0 * x * x * x * x * x * x * x))

	// See https://github.com/Emirlol/chatcalc/issues/3 for details.
	fun custom_tan(x: Double): Double = if (abs(cos(x)) < 1E-16) Double.POSITIVE_INFINITY else sin(x) / cos(x)

	private fun simple(simple: DoubleUnaryOperator) = Function { if (it.size == 1) simple.apply(it[0]) else null }

	fun interface Function {
		operator fun invoke(values: DoubleArray): Double?
	}
}