package ca.rttv.chatcalc.engine.types

import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.interleaveWith
import me.ancientri.rimelib.util.text.text
import net.minecraft.text.Text

interface TypeConstraint : SourceRepresentable {
	/**
	 * @return `true` if this constraint accepts the given object, `false` otherwise.
	 */
	infix fun accepts(obj: MathObject): Boolean

	/**
	 * @return true if `this` constraint accepts everything `other` accepts. Used for validating overload order.
	 */
	infix fun isSupersetOf(other: TypeConstraint): Boolean

	/**
	 * @return a string representation of this constraint for output purposes. Should fit the language format so it can be parsed back in.
	 */
	override fun toSourceString(): String

	/**
	 * @return a pretty-printed version of this constraint for display purposes. Does not have to conform to the language's format.
	 */
	fun toPrettyText(): Text = toSourceString().text(ColorPalette.OVERLAY2)
}

infix fun UnionTypeConstraint.or(other: UnionTypeConstraint) = UnionTypeConstraint(this.constraints + other.constraints)

infix fun UnionTypeConstraint.or(other: TypeConstraint) = UnionTypeConstraint(this.constraints + other)

infix fun TypeConstraint.or(other: UnionTypeConstraint) = UnionTypeConstraint(listOf(this) + other.constraints)

infix fun TypeConstraint.or(other: TypeConstraint) = UnionTypeConstraint(listOf(this, other))

/**
 * A constraint that accepts any object.
 */
object AnyConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = true
	override fun toSourceString(): String = "any"
	override fun isSupersetOf(other: TypeConstraint): Boolean = true
}

object PositiveIntegerConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathScalar && obj.value > 0.0 && obj.value % 1.0 == 0.0
	override fun toSourceString(): String = "positive_integer"
	override fun isSupersetOf(other: TypeConstraint): Boolean = other == PositiveIntegerConstraint
}

object IntegerConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathScalar && obj.value % 1.0 == 0.0
	override fun toSourceString(): String = "integer"
	override fun isSupersetOf(other: TypeConstraint): Boolean = other == IntegerConstraint || other == PositiveIntegerConstraint
}

object RealConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is RealScalar
	override fun toSourceString(): String = "real"
	override fun isSupersetOf(other: TypeConstraint): Boolean = other == RealConstraint || other == IntegerConstraint
}

object ScalarConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathScalar
	override fun toSourceString(): String = "scalar"
	override fun isSupersetOf(other: TypeConstraint): Boolean = other == ScalarConstraint || other == RealConstraint
}

/**
 * Checks if the object is a vector (rank-1 tensor).
 */
object VectorConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathTensor && obj.rank == 1
	override fun toSourceString(): String = "vector"
	override fun isSupersetOf(other: TypeConstraint): Boolean = when (other) {
		is UnionTypeConstraint -> other.constraints.all(this::isSupersetOf)
		VectorConstraint -> true
		else -> false
	}
}

/**
 * Checks if the object is a matrix (rank-2 tensor).
 */
object MatrixConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathTensor && obj.rank == 2
	override fun toSourceString(): String = "matrix"
	override fun isSupersetOf(other: TypeConstraint): Boolean = when (other) {
		is UnionTypeConstraint -> other.constraints.all(this::isSupersetOf)
		MatrixConstraint -> true
		else -> false
	}
}

object TensorConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathTensor
	override fun toSourceString(): String = "tensor"
	override fun isSupersetOf(other: TypeConstraint): Boolean = when (other) {
		is UnionTypeConstraint -> other.constraints.all(this::isSupersetOf)
		TensorConstraint, MatrixConstraint, VectorConstraint -> true
		else -> false
	}
}

object BooleanConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathBoolean
	override fun toSourceString(): String = "boolean"
	override fun isSupersetOf(other: TypeConstraint): Boolean = when (other) {
		is UnionTypeConstraint -> other.constraints.all(this::isSupersetOf)
		BooleanConstraint -> true
		else -> false
	}
}

object FunctionConstraint : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = obj is MathFunction
	override fun toSourceString(): String = "function"
	override fun isSupersetOf(other: TypeConstraint): Boolean = when (other) {
		is UnionTypeConstraint -> other.constraints.all(this::isSupersetOf)
		FunctionConstraint -> true
		else -> false
	}
}

/**
 * Checks if the object matches ANY of the provided constraints.
 */
data class UnionTypeConstraint(val constraints: List<TypeConstraint>) : TypeConstraint {
	override fun accepts(obj: MathObject): Boolean = constraints.any { it.accepts(obj) }

	override fun toSourceString(): String = constraints.joinToString("|") { it.toSourceString() }

	override fun toPrettyText(): Text = text {
		constraints.asSequence()
			.map(TypeConstraint::toPrettyText)
			.interleaveWith(" | ".text(ColorPalette.SURFACE2))
			.forEach(::append)
	}

	override fun isSupersetOf(other: TypeConstraint): Boolean = when (other) {
		is UnionTypeConstraint -> constraints.all { other.constraints.any(it::isSupersetOf) } // If all our constraints are superset of any other constraint in the other, this union is a superset of the other union.
		else -> constraints.any { it.isSupersetOf(other) } // If any of our constraints is a superset of the other constraint, this union is a superset of the other constraint.
	}
}