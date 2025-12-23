package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.lexer.Lexer
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.lexer.TokenType
import ca.rttv.chatcalc.engine.types.MathObject
import ca.rttv.chatcalc.engine.types.MathScalar
import ca.rttv.chatcalc.engine.types.MathTensor
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest

fun tokensToString(input: String) = lex(input).joinToString(" ") { "${it.type}(${it.lexeme})" }

// Separate functions to allow infix on this one
infix fun MathObject.shouldBeScalar(expected: Double) = shouldBeScalar(expected, 1e-6)

fun MathObject.shouldBeScalar(expected: Double, tolerance: Double) {
	shouldBeInstanceOf<MathScalar>()
	value shouldBe (expected plusOrMinus tolerance)
}

fun MathObject.shouldBeTensor(expectedData: DoubleArray, expectedShape: IntArray, tolerance: Double = 1e-6) {
	shouldBeInstanceOf<MathTensor>()
	shape shouldBe expectedShape
	data.size shouldBe expectedData.size
	for (i in expectedData.indices) {
		data[i] shouldBe (expectedData[i] plusOrMinus tolerance)
	}
}

@DslMarker
annotation class MathTestDsl

@MathTestDsl
class MathTestBuilder {
    val nodes = mutableListOf<DynamicNode>()

    infix fun String.shouldEvalTo(number: Number) {
		val expected = number.toDouble()
	    nodes += DynamicTest.dynamicTest("$this = $expected") {
		    MathEngine.eval(this, true).shouldBeScalar(expected)
	    }
    }

    fun tensor(input: String, data: DoubleArray, shape: IntArray) {
	    nodes += DynamicTest.dynamicTest(input) {
		    eval(input).shouldBeTensor(data, shape)
	    }
    }

	infix fun String.shouldFixTo(string: String) {
		nodes += DynamicTest.dynamicTest("$this should fix to $string") {
			Lexer.fixParenthesis(this) shouldBe string
		}
	}

	infix fun String.shouldLexTo(expected: Iterable<TokenType>) {
		nodes += DynamicTest.dynamicTest("$this should lex to ${expected.joinToString(" ")}") {
			lex(this).map(Token::type).dropLastWhile { it == TokenType.EOF } shouldBe (expected)
		}
	}

	fun String.shouldFail() {
		nodes += DynamicTest.dynamicTest("$this should fail") {
			shouldThrow<EngineError> {
				eval(this)
			}
		}
    }

    fun group(name: String, block: MathTestBuilder.() -> Unit) {
	    nodes += DynamicContainer.dynamicContainer(name, MathTestBuilder().apply(block).nodes)
    }
}

fun mathTests(block: MathTestBuilder.() -> Unit): List<DynamicNode> = MathTestBuilder().apply(block).nodes
