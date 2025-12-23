package ca.rttv.chatcalc.engine

import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
internal class InterpreterTest {

	@TestFactory
	fun `Scalar tests`() = mathTests {
		group("Addition & Subtraction") {
			"1+1" shouldEvalTo 2
			"2+2" shouldEvalTo 4
			"1+2+3" shouldEvalTo 6
			"1+2+3+4" shouldEvalTo 10
			"-1+12" shouldEvalTo 11
			"+12-1" shouldEvalTo 11
			"-4+-4" shouldEvalTo -8
			"1+-4" shouldEvalTo -3
		}

		group("Multiplication") {
			"1*1" shouldEvalTo 1
			"2*2" shouldEvalTo 4
			"1*2*3" shouldEvalTo 6
			"1*2*3*4" shouldEvalTo 24
			"-1*12" shouldEvalTo -12
			"+12*1" shouldEvalTo 12
			"-4*-4" shouldEvalTo 16
		}

		group("Division") {
			"1/1" shouldEvalTo 1
			"0/1" shouldEvalTo 0
			"1/0" shouldEvalTo Double.POSITIVE_INFINITY
			"4/2" shouldEvalTo 2
			"-5/-2" shouldEvalTo 2.5
			"4/-4" shouldEvalTo -1
			"1.1/-1.1" shouldEvalTo -1
		}

		group("Exponentiation") {
			"2^3" shouldEvalTo 8
			"2^-3" shouldEvalTo 0.125
			"2^0" shouldEvalTo 1
			"2^1" shouldEvalTo 2
			"2^2" shouldEvalTo 4
			"-4^4" shouldEvalTo -256
			"4^-4" shouldEvalTo 0.00390625
		}

		group("Modulo") {
			"10%3" shouldEvalTo 1
			"10%-3" shouldEvalTo 1
			"-10%3" shouldEvalTo -1
		}

		group("Unary Operations") {
			"-1" shouldEvalTo -1
			"+1" shouldEvalTo 1
			"-(5*-3)" shouldEvalTo 15
			"-(-1)" shouldEvalTo 1
			"-(-(-1))" shouldEvalTo -1
			"--1" shouldEvalTo 1
			"+--1" shouldEvalTo 1
			"1+--+1" shouldEvalTo 2
		}

		group("Grouping") {
			"(1+2)*3" shouldEvalTo 9
			"(1+2)*(3+4)" shouldEvalTo 21
			"(-4)^4" shouldEvalTo 256
			"12/(2+1)" shouldEvalTo 4
		}

		group("Postfix Operators") {
			"5!" shouldEvalTo 120
			"0!" shouldEvalTo 1
			"1+5!" shouldEvalTo 121
			"1+0!" shouldEvalTo 2
		}

		group("Failures") {
			"+".shouldFail()
			"1+".shouldFail()
			"+-".shouldFail()
			"5%%2".shouldFail()
			"(2".shouldFail()
		}
	}

	@TestFactory
	fun `Variable tests`() = mathTests {
		group("Built-in Variables") {
			"pi" shouldEvalTo Math.PI
			"e" shouldEvalTo Math.E
			"tau" shouldEvalTo 2.0 * Math.PI
			"phi" shouldEvalTo 1.618033988749894
		}
	}
}