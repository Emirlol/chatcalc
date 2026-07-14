package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.config.ConfigManager
import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.engine.astvisitors.Interpreter
import ca.rttv.chatcalc.engine.env.GlobalEnvironment
import ca.rttv.chatcalc.engine.env.MapEnvironment
import ca.rttv.chatcalc.engine.env.MutableEnvironment
import ca.rttv.chatcalc.engine.lexer.Lexer
import ca.rttv.chatcalc.engine.lexer.Token
import ca.rttv.chatcalc.engine.parser.Parser
import ca.rttv.chatcalc.engine.types.MathBoolean
import ca.rttv.chatcalc.engine.types.MathObject
import ca.rttv.chatcalc.engine.types.MathScalar
import ca.rttv.chatcalc.engine.types.MathTensor
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Assertions.fail

@Volatile
private var initialized = false

private fun ensureConfigInitialized() {
	if (!initialized) {
		synchronized(Unit) {
			if (!initialized) {
				ConfigManager.init()
				UserEnvironmentManager.init()
				initialized = true
			}
		}
	}
}

fun lex(input: String): List<Token> = Lexer(input).scanTokens().first

fun parse(input: String): List<Statement> {
	val (tokens, source) = Lexer(input).scanTokens()
	return Parser(tokens, source).parse()
}

fun runProgram(
	input: String,
	dryRun: Boolean = true,
	env: MutableEnvironment = MapEnvironment(parent = GlobalEnvironment),
): EngineResult {
	ensureConfigInitialized()
	val statements = parse(input)
	return Interpreter({ env }, dryRun).interpret(statements)
}

class EngineSession(
	private val env: MutableEnvironment = MapEnvironment(parent = GlobalEnvironment),
) {
	fun run(input: String, dryRun: Boolean = false): EngineResult = runProgram(input, dryRun, env)

	fun eval(input: String, dryRun: Boolean = false): MathObject = run(input, dryRun).toMathObjectOrThrow()
}

fun EngineResult.toMathObjectOrThrow(): MathObject = when (this) {
	is EngineResult.Value -> output
	is EngineResult.Append -> output
	is EngineResult.Error<*> -> throw error
	is EngineResult.Clear -> fail("Expected a value result, got clear.")
}

infix fun MathObject.shouldBeScalar(expected: Double) = shouldBeScalar(expected, 1e-9)

fun MathObject.shouldBeScalar(expected: Double, tolerance: Double) {
	shouldBeInstanceOf<MathScalar>()
	value shouldBe (expected plusOrMinus tolerance)
}

fun MathObject.shouldBeBoolean(expected: Boolean) {
	shouldBeInstanceOf<MathBoolean>()
	value shouldBe expected
}

fun MathObject.shouldBeTensor(expectedData: DoubleArray, expectedShape: IntArray, tolerance: Double = 1e-9) {
	shouldBeInstanceOf<MathTensor>()
	shape shouldBe expectedShape
	size shouldBe expectedData.size
	for (i in expectedData.indices) {
		getFlat(i) shouldBe (expectedData[i] plusOrMinus tolerance)
	}
}

fun String.shouldFail(dryRun: Boolean = true) {
	try {
		when (runProgram(this, dryRun = dryRun)) {
			is EngineResult.Error<*> -> Unit
			else -> fail("Expected failure for input: $this")
		}
	} catch (_: EngineError) {
		// Parse/lex errors can be thrown before EngineResult is produced.
	}
}
