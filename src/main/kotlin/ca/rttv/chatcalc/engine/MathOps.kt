package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.astvisitors.Interpreter
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.lexer.TokenType
import ca.rttv.chatcalc.engine.types.*
import com.google.common.math.DoubleMath
import kotlin.math.pow
import kotlin.math.sqrt

object MathOps {
	fun pow(left: MathObject, right: MathObject): MathObject = Interpreter.zip(left, right, Token(TokenType.HAT, "^"), Double::pow)

	fun mod(left: MathObject, right: MathObject): MathObject = Interpreter.zip(left, right, Token(TokenType.PERCENT, "%"), Double::rem)

	fun fact(obj: MathObject): MathObject = Interpreter.map(obj, Token(TokenType.BANG, "!")) { x ->
		if (x == 0.0) 1.0
		else if ((x % 1.0 == 0.0) and (x >= 1.0)) DoubleMath.factorial(x.toInt())
		// Stirling approximation up to O(1/x^7)
		else sqrt(2.0 * Math.PI * x) *
				(x / Math.E).pow(x) * (
				1.0
						+ 1.0 / (12.0 * x)
						+ 1.0 / (288.0 * x * x)
						- 139.0 / (51840.0 * x * x * x)
						- 571.0 / (2488320.0 * x * x * x * x)
						+ 163879.0 / (209018880.0 * x * x * x * x * x)
						+ 5246819.0 / (75246796800.0 * x * x * x * x * x * x)
						- 534703531.0 / (902961561600.0 * x * x * x * x * x * x * x)
				)
	}

	fun dotProduct(left: MathTensor, right: MathTensor): RealScalar {
		if (left is RangeTensor && right is RangeTensor
			&& left.rank == 1 && right.rank == 1
		) {
			// Sum from k to n-1 for (A+B*k)(C+D*k) = n*A*C + (A*D + B*C) * sum(k) + B*D*sum(k^2)
			// sum(k) = n(n-1)/2
			// sum(k^2) = n(n-1)(2n-1)/6
			val n = left.size.toDouble()
			val A = left.start;
			val B = left.step
			val C = right.start;
			val D = right.step

			val sumK = n * (n - 1) / 2
			val sumK2 = n * (n - 1) * (2 * n - 1) / 6

			return RealScalar((A * C * n) + ((A * D) + (B * C)) * sumK + (B * D * sumK2))
		}

		// Fallback to general dot product
		var sum = 0.0
		if (left is ArrayTensor && right is ArrayTensor) { // Small optimization to use direct array access when we can
			for (i in 0 until left.size) sum += left.data[i] * right.data[i]
		} else {
			for (i in 0 until left.size) sum += left.getFlat(i) * right.getFlat(i)
		}
		return RealScalar(sum)
	}
}