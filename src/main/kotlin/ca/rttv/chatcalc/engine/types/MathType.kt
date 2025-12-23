package ca.rttv.chatcalc.engine.types

import com.google.common.collect.ImmutableMap
import net.minecraft.util.StringIdentifiable

/**
 * Math types used internally by the language engine.
 */
enum class MathType(val id: String) : StringIdentifiable {
	SCALAR("scalar"),
	TENSOR("tensor"),
	BOOLEAN("boolean"),
	FUNCTION("function"),
	ANY("any"); // Useful for wildcard args

	override fun asString() = id

	companion object {
		val CODEC: StringIdentifiable.EnumCodec<MathType> = StringIdentifiable.createCodec(MathType::values)
	}
}

/**
 * Type names and their corresponding constraints.
 */
object TypeRegistry : Map<String, TypeConstraint> by ImmutableMap.of(
	"scalar", ScalarConstraint,
	"positive_integer", PositiveIntegerConstraint,
	"integer", IntegerConstraint,
	"real", RealConstraint,
	"vector", VectorConstraint,
	"matrix", MatrixConstraint,
	"tensor", TensorConstraint,
	"boolean", BooleanConstraint,
	"function", FunctionConstraint,
	"any", AnyConstraint
)
