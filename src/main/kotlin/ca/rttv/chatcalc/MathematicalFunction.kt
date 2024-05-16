package ca.rttv.chatcalc

import com.google.common.math.DoubleMath
import it.unimi.dsi.fastutil.doubles.DoubleUnaryOperator
import net.minecraft.util.math.MathHelper
import kotlin.math.*

class MathematicalFunction(private val func: String) {

    override fun toString(): String {
        return func
    }

    fun apply(vararg values: Double): Double {
        return FUNCTIONS[func].let {
            it ?: Config.func(func, values)
            it!!.invoke(values) ?: throw IllegalArgumentException("Invalid amount of arguments for function $func")
        }
    }

    companion object {
        val FUNCTIONS = mutableMapOf(
            "sqrt" to simple(::sqrt),
            "cbrt" to simple(::cbrt),

            "sin" to simple { sin(Config.convertToRadians(it)) },
            "cos" to simple { cos(Config.convertToRadians(it)) },
            "tan" to simple { tan(Config.convertToRadians(it)) },
            "csc" to simple { 1 / sin(Config.convertToRadians(it)) },
            "sec" to simple { 1 / cos(Config.convertToRadians(it)) },
            "cot" to simple { 1 / tan(Config.convertToRadians(it)) },

            "arcsin" to simple { Config.convertFromRadians(asin(it)) },
            "asin" to simple { Config.convertFromRadians(asin(it)) },

            "arccos" to simple { Config.convertFromRadians(acos(it)) },
            "acos" to simple { Config.convertFromRadians(acos(it)) },

            "arctan" to simple { Config.convertFromRadians(atan(it)) },
            "atan" to simple { Config.convertFromRadians(atan(it)) },

            "arccsc" to simple { Config.convertFromRadians(asin(1 / it)) },
            "acsc" to simple { Config.convertFromRadians(asin(1 / it)) },

            "arcsec" to simple { Config.convertFromRadians(acos(1 / it)) },
            "asec" to simple { Config.convertFromRadians(acos(1 / it)) },

            "arccot" to simple { Config.convertFromRadians(atan(1 / it)) },
            "acot" to simple { Config.convertFromRadians(atan(1 / it)) },

            "floor" to simple(::floor),
            "ceil" to simple(::ceil),
            "round" to simple(::round),
            "abs" to simple(::abs),
            "log" to simple(::log10),
            "ln" to simple(::ln),
            "exp" to simple(::exp),

            "sgn" to simple { x -> sign(x).let { if (it.isNaN()) 0.0 else it } },
            "min" to DoubleArray::min,
            "max" to DoubleArray::max,
            "gcf" to { it.reduce { a, b -> gcf(a, b) } },
            "lcm" to { it.reduce { a, b -> lcm(a, b) } },
            "clamp" to { if (it.size == 3) MathHelper.clamp(it[0], it[1], it[2]) else null },

            "cmp" to { //What is this monstrosity?
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

        fun lcm(a: Double, b: Double): Double {
            return (a * b) / gcf(a, b)
        }

        @JvmStatic
        fun log(base: Double, value: Double): Double {
            return ln(value) / ln(base)
        }

        @JvmStatic
        fun mod(a: Double, b: Double): Double {
            return a % b + (if (((java.lang.Double.doubleToLongBits(a) ushr 63) xor (java.lang.Double.doubleToLongBits(b) ushr 63)) > 0) b else 0.0)
        }

        @JvmStatic
        fun factorial(x: Double): Double {
            return if ((x % 1.0 == 0.0) and (x >= 1.0)
            ) DoubleMath.factorial(x.toInt())
            else sqrt(2.0 * Math.PI * x) * (x / Math.E).pow(x) * (((1.0 + 1.0 / (12.0 * x) + 1.0 / (288.0 * x * x)) - 139.0 / (51840.0 * x * x * x) - 571.0 / (2488320.0 * x * x * x * x)) + 163879.0 / (209018880.0 * x * x * x * x * x) + 5246819.0 / (75246796800.0 * x * x * x * x * x * x)
                    - 534703531.0 / (902961561600.0 * x * x * x * x * x * x * x))
        }

        private fun simple(simple: DoubleUnaryOperator): (DoubleArray) -> Double? = { if (it.size == 1) simple.apply(it[0]) else null }

    }
}