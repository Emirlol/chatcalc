package ca.rttv.chatcalc

import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.MathHelper
import kotlin.math.abs

object Testcases {
    fun test(list: Map<String, Double>) {
        val client = MinecraftClient.getInstance()

        for ((expression, expectedResult) in list) {
            try {
                val result = Config.makeEngine().eval(expression, arrayOfNulls(0))
                if (abs(expectedResult - result) <= 0.000001) {
                    client.player?.sendMessage(Text.literal("Test case passed: $expression, got $expectedResult").formatted(Formatting.GREEN))
                } else {
                    client.player?.sendMessage(Text.literal("Test case failed: $expression, expected $expectedResult, got $result").formatted(Formatting.RED))
                }
            } catch (e: Exception) {
                client.player?.sendMessage(Text.literal("Test case failed with exception: $expression, expected $expectedResult, got $e").formatted(Formatting.RED))
            }
        }
    }

    val TESTCASES = mapOf(
        "3+3" to 6.0,
        "4*4" to 16.0,
        "5(6)" to 30.0,
        "(6)5" to 30.0,
        "4^2" to 16.0,
        "sqrt(16)" to 4.0,
        "4*(4+3*3)" to 52.0,
        "8*2" to 16.0,
        "26+cos(0" to 27.0,
        "1+2)/3" to 1.0,
        "1+(2*3" to 7.0,
        "pie" to Math.PI * Math.E,
        "(2phi-1)^2" to 5.0,
        "1+1" to 2.0,
        "1+2*3" to 7.0,
        "1+2*4/2" to 5.0,
        "1+12/3(2)" to 3.0,
        "1+(2*3)^2" to 37.0,
        "0.5(-2.5-0.1)" to -1.3,
        "sqrt(9)" to 3.0,
        "cbrt(9^3)" to 9.0,
        "ln(e^2)" to 2.0,
        "ln(exp(pi))" to Math.PI,
        "log_10(1000)" to 3.0,
        "log(1000)" to 3.0,
        "2^3*2" to 16.0,
        "sin(90deg)" to 1.0,
        "cos(180deg)" to -1.0,
        "tan(45deg)" to 1.0,
        "cot(-45deg)" to -1.0,
        "sec(180deg)" to -1.0,
        "csc(90deg)" to 1.0,
        "arcsin(sin(90deg))" to 90.0,
        "arccos(cos(180deg))" to 180.0,
        "arctan(tan(45deg))" to 45.0,
        "arccot(cot(45deg))" to 45.0,
        "arcsec(sec(89deg))" to 89.0,
        "arccsc(csc(91deg))" to 89.0,
        "floor(-2.5)" to -3.0,
        "ceil(-2.5)" to -2.0,
        "round(-2.5-0.1)" to -3.0,
        "abs(-2.5-0.1)" to 2.6,
        "|-2.5-0.1|" to 2.6,
        "0.5|-2.5-0.1|" to 1.3,
        "5%360" to 5.0,
        "-5%360" to 355.0,
        "5%-360" to -355.0,
        "-5%-360" to -5.0,
        "min(sqrt(37);6" to 6.0,
        "max(sqrt(37);7" to 7.0,
        "max(sqrt(2);sqrt(3);sqrt(5);sqrt(7);sqrt(11);sqrt(13);sqrt(17);sqrt(19);sqrt(23);sqrt(29);sqrt(31);sqrt(37);sqrt(41" to MathHelper.sqrt(41f).toDouble(),
        "clamp(-e;-2;4)" to -2.0,
        "clamp(pi^2;-2;4" to 4.0,
        "clamp(pi;-2;4)" to Math.PI,
        "cmp(-2;3)" to -1.0,
        "cmp(5;3)" to 1.0,
        "cmp(5;3;5)" to 0.0,
        "gcf(4;2)" to 2.0,
        "lcm(6;9)" to 18.0,
        "1)+((2)" to 3.0
    )
}
