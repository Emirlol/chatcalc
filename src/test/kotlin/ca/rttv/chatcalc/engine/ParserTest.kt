package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.lexer.TokenType
import ca.rttv.chatcalc.engine.types.PositiveIntegerConstraint
import ca.rttv.chatcalc.engine.types.RealConstraint
import ca.rttv.chatcalc.engine.types.ScalarConstraint
import ca.rttv.chatcalc.engine.types.UnionTypeConstraint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
internal class ParserTest {
	private fun parseSingle(input: String): Statement {
		val statements = parse(input)
		statements.size shouldBe 1
		return statements.first()
	}

	@Test
	fun `parses all statement kinds`() {
		parseSingle("a=1").shouldBeInstanceOf<VariableAssignment>()
		parseSingle("a=").shouldBeInstanceOf<Undefine>()
		parseSingle("1+2=").shouldBeInstanceOf<Query>()
		parseSingle("f(x)=x").shouldBeInstanceOf<FunctionDefinition>()
		parseSingle("1+2").shouldBeInstanceOf<ExpressionStatement>()
	}

	@Test
	fun `parses type annotations from registry`() {
		val fn = parseSingle("f(x:positive_integer|real):scalar=x").shouldBeInstanceOf<FunctionDefinition>()
		fn.parameters.single().constraint.shouldBeInstanceOf<UnionTypeConstraint>().constraints shouldBe listOf(
			PositiveIntegerConstraint,
			RealConstraint,
		)
		fn.returnType shouldBe ScalarConstraint
	}

	@Test
	fun `rejects unknown type annotations`() {
		shouldThrow<ParserError> { parse("f(x:number)=x") }
	}

	@Test
	fun `parses lambda forms`() {
		parseSingle("x->x+1").shouldBeInstanceOf<ExpressionStatement>()
		parseSingle("(x:scalar,y)->x+y").shouldBeInstanceOf<ExpressionStatement>()
		parseSingle("()->1").shouldBeInstanceOf<ExpressionStatement>()
	}

	@Test
	fun `parses conditional absolute vector indexing and slicing`() {
		val expr = parseSingle("if(1,| -2 |,[1,2,3][1:3])")
			.shouldBeInstanceOf<ExpressionStatement>()
			.expression
			.shouldBeInstanceOf<Conditional>()

		expr.ifTrue.shouldBeInstanceOf<AbsoluteValue>()
		val index = expr.ifFalse.shouldBeInstanceOf<IndexAccess>()
		index.operand.shouldBeInstanceOf<Vector>()
		index.indices.single().shouldBeInstanceOf<Slice>()
	}

	@Test
	fun `parses mixed index items in one bracket`() {
		val index = parseSingle("[[1,2],[3,4]][1,0:1]")
			.shouldBeInstanceOf<ExpressionStatement>()
			.expression
			.shouldBeInstanceOf<IndexAccess>()

		index.indices.size shouldBe 2
		index.indices[0].shouldBeInstanceOf<Literal>().value shouldBe 1.0
		index.indices[1].shouldBeInstanceOf<Slice>()
	}

	@Test
	fun `parses implicit multiplication and exponent associativity`() {
		val implicit = parseSingle("2(3+4)").shouldBeInstanceOf<ExpressionStatement>().expression.shouldBeInstanceOf<Binary>()
		implicit.operator.type shouldBe TokenType.STAR
		implicit.right.shouldBeInstanceOf<Grouping>()

		val root = parseSingle("2^3^2").shouldBeInstanceOf<ExpressionStatement>().expression.shouldBeInstanceOf<Binary>()
		root.operator.type shouldBe TokenType.HAT
		root.right.shouldBeInstanceOf<Binary>().operator.type shouldBe TokenType.HAT
	}

	@Test
	fun `comparison is non associative`() {
		shouldThrow<ParserError> { parse("1<2<3") }
	}

	@Test
	fun `literal call-like and index-like forms parse as implicit multiplication`() {
		val callLike = parseSingle("1(2)").shouldBeInstanceOf<ExpressionStatement>().expression.shouldBeInstanceOf<Binary>()
		callLike.operator.type shouldBe TokenType.STAR
		callLike.left.shouldBeInstanceOf<Literal>().value shouldBe 1.0
		callLike.right.shouldBeInstanceOf<Grouping>()

		val indexLike = parseSingle("1[0]").shouldBeInstanceOf<ExpressionStatement>().expression.shouldBeInstanceOf<Binary>()
		indexLike.operator.type shouldBe TokenType.STAR
		indexLike.left.shouldBeInstanceOf<Literal>().value shouldBe 1.0
		indexLike.right.shouldBeInstanceOf<Vector>()
	}

	@Test
	fun `supports implicit multiplication with absolute value`() {
		val parsed = parseSingle("2|x|").shouldBeInstanceOf<ExpressionStatement>()
		val expr = parsed.expression.shouldBeInstanceOf<Binary>()
		expr.operator.type shouldBe TokenType.STAR
		expr.left.shouldBeInstanceOf<Literal>().value shouldBe 2.0
		expr.right.shouldBeInstanceOf<AbsoluteValue>()
	}

	@Test
	fun `query and undefine only valid at end of input`() {
		shouldThrow<ParserError> { parse("a= b=1") }
		shouldThrow<ParserError> { parse("1= 2") }
	}

	@Test
	fun `rejects malformed constructs`() {
		shouldThrow<ParserError> { parse("f(x:)=x") }
		shouldThrow<ParserError> { parse("if(1,2)") }
	}
}
