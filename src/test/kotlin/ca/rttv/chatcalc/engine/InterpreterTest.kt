package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.EngineResult.Append
import ca.rttv.chatcalc.engine.EngineResult.Clear
import ca.rttv.chatcalc.engine.EngineResult.Error
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
internal class InterpreterTest {
	@Test
	fun `evaluates scalar arithmetic precedence implicit multiplication and absolute value`() {
		eval("1+2*3") shouldBeScalar 7.0
		eval("(1+2)*3") shouldBeScalar 9.0
		eval("2(3+4)") shouldBeScalar 14.0
		eval("|-3|") shouldBeScalar 3.0
		eval("2^3^2") shouldBeScalar 512.0
		eval("5!") shouldBeScalar 120.0
	}

	@Test
	fun `evaluates boolean and comparison expressions`() {
		eval("1<2").shouldBeBoolean(true)
		eval("1>=2").shouldBeBoolean(false)
		eval("1==1").shouldBeBoolean(true)
		eval("1!=1").shouldBeBoolean(false)
		eval("~0") shouldBeScalar 1.0
		eval("~1") shouldBeScalar 0.0
	}

	@Test
	fun `short circuits logical operators`() {
		runProgram("0&&undefinedVar").toMathObjectOrThrow().shouldBeBoolean(false)
		runProgram("1||undefinedVar").toMathObjectOrThrow().shouldBeBoolean(true)
	}

	@Test
	fun `evaluates vectors indexing slicing and matrix multiplication`() {
		eval("[1,2,3]+1").shouldBeTensor(doubleArrayOf(2.0, 3.0, 4.0), intArrayOf(3))
		eval("[1,2,3].*[4,5,6]").shouldBeTensor(doubleArrayOf(4.0, 10.0, 18.0), intArrayOf(3))
		eval("[1,2,3][-1]") shouldBeScalar 3.0
		eval("[1,2,3,4][1:3]").shouldBeTensor(doubleArrayOf(2.0, 3.0), intArrayOf(2))
		eval("[[1,2],[3,4]]*[[5,6],[7,8]]").shouldBeTensor(
			doubleArrayOf(19.0, 22.0, 43.0, 50.0),
			intArrayOf(2, 2),
		)
	}

	@Test
	fun `evaluates conditionals and lambda calls`() {
		eval("if(1,2,3)") shouldBeScalar 2.0
		eval("if(0,2,3)") shouldBeScalar 3.0
		eval("(x->x+1)(5)") shouldBeScalar 6.0
		eval("((x,y)->x*y)(3,4)") shouldBeScalar 12.0
	}

	@Test
	fun `supports stateful variable assignment and undefine`() {
		val session = EngineSession()
		assertClear(session.run("a=5", dryRun = false))
		session.eval("a", dryRun = false) shouldBeScalar 5.0
		assertClear(session.run("a=", dryRun = false))
		assertError(session.run("a", dryRun = false))
	}

	@Test
	fun `supports function definition invocation and recursion`() {
		val session = EngineSession()
		assertClear(session.run("inc(x)=x+1", dryRun = false))
		session.eval("inc(41)", dryRun = false) shouldBeScalar 42.0

		assertClear(session.run("fact(n:integer)=if(n<=1,1,n*fact(n-1))", dryRun = false))
		session.eval("fact(6)", dryRun = false) shouldBeScalar 720.0
	}

	@Test
	fun `returns append for query statements`() {
		val result = runProgram("2+2=")
		val append = result as? Append ?: throw AssertionError("Expected EngineResult.Append, got ${result::class.simpleName}")
		append.output shouldBeScalar 4.0
	}

	@Test
	fun `returns clear for definitions and assignments`() {
		assertClear(runProgram("a=3", dryRun = false))
		assertClear(runProgram("f(x)=x", dryRun = false))
	}

	@Test
	fun `reports evaluation failures`() {
		"[1,2][10]".shouldFail()
		"[1,2]+[1,2,3]".shouldFail()
		"sqrt()".shouldFail()
		"range(1,1)".shouldFail()
	}

	@Test
	fun `throws via eval helper on engine errors`() {
		shouldThrow<EngineError> { eval("unknownName") }
	}

	private fun eval(input: String) = runProgram(input).toMathObjectOrThrow()

	private fun assertClear(result: EngineResult) {
		if (result !is Clear) throw AssertionError("Expected EngineResult.Clear, got ${result::class.simpleName}")
	}

	private fun assertError(result: EngineResult) {
		if (result !is Error<*>) throw AssertionError("Expected EngineResult.Error, got ${result::class.simpleName}")
	}
}
