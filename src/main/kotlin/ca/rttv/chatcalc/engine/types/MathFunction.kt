package ca.rttv.chatcalc.engine.types

import ca.rttv.chatcalc.engine.FunctionDefinition
import ca.rttv.chatcalc.engine.Lambda
import ca.rttv.chatcalc.engine.astvisitors.Interpreter
import ca.rttv.chatcalc.engine.astvisitors.PrettyPrinter
import ca.rttv.chatcalc.engine.env.MapEnvironment
import ca.rttv.chatcalc.engine.env.MutableEnvironment
import com.mojang.serialization.MapCodec
import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.interleaveWith
import me.ancientri.rimelib.util.text.text
import net.minecraft.screen.ScreenTexts
import net.minecraft.text.Text

data class FunctionParameter(val name: String, val constraint: TypeConstraint)

data class FunctionSignature(
	val parameters: List<FunctionParameter>,
	val returnConstraint: TypeConstraint = AnyConstraint,
	val isVararg: Boolean = false
) {
	/**
	 * @return `true` if the given arguments match this signature, `false` otherwise.
	 */
	infix fun matches(arguments: List<MathObject>): Boolean {
		if (this.isVararg) {
			if (arguments.size < this.parameters.size) return false
		} else {
			if (arguments.size != this.parameters.size) return false
		}

		for (i in arguments.indices) {
			val paramIndex = if (this.isVararg && i >= this.parameters.lastIndex) this.parameters.lastIndex else i
			val constraint = this.parameters[paramIndex].constraint
			if (!constraint.accepts(arguments[i])) return false
		}

		return true
	}

	/**
	 * Generates a pretty-printed representation of this signature.
	 */
	fun toPrettyText(): Text = text {
		"(" colored ColorPalette.SUBTEXT2
		parameters.asSequence()
			.map { parameter ->
				text {
					parameter.name colored ColorPalette.TEXT
					if (parameter.constraint != AnyConstraint) { // Unannotated parameters are implicitly ANY, and ANY constraints are implicitly unannotated.
						": " colored ColorPalette.SUBTEXT2
						+parameter.constraint.toPrettyText()
					}
				}
			}
			.interleaveWith(PrettyPrinter.COMMA)
			.forEach(::append)
		if (isVararg && parameters.isNotEmpty()) { // The function being vararg means the last parameter is a vararg, the other parameters cannot be.
			"..." colored ColorPalette.TEXT
		}
		")" colored ColorPalette.SUBTEXT2
		": " colored ColorPalette.SUBTEXT2
		+returnConstraint.toPrettyText()
	}
}

data class FunctionOverload(val signature: FunctionSignature, val executable: Interpreter.(List<MathObject>) -> MathObject)

interface MathFunction : MathObject {
	override val type: MathType get() = MathType.FUNCTION

	val signatures: List<FunctionSignature>

	/**
	 * @return A matching overload for the given arguments, or null if no overload matches.
	 */
	fun selectOverload(interpreter: Interpreter, arguments: List<MathObject>): FunctionOverload?
}

data class BuiltinFunction(val name: String, private val overloads: List<FunctionOverload>) : MathFunction {
	override val signatures: List<FunctionSignature> = overloads.map(FunctionOverload::signature)

	override fun toPrettyText(): Text = text {
		signatures.asSequence()
			.map {
				text {
					name colored ColorPalette.TEXT
					+it.toPrettyText()
				}
			}
			.interleaveWith(text {
				"," colored ColorPalette.SUBTEXT2
				+ScreenTexts.LINE_BREAK
			})
			.forEach(::append)
	}

	override fun selectOverload(interpreter: Interpreter, arguments: List<MathObject>): FunctionOverload? = overloads.firstOrNull { it.signature matches arguments }
}

data class UserDefinedFunction(val definition: FunctionDefinition) : MathFunction, SourceRepresentable {
	private val overload: FunctionOverload by lazy {
		val params = definition.parameters
		val signature = FunctionSignature(params, definition.returnType)

		FunctionOverload(signature) { args ->
			val localScope = createLocalEnvironment()
			for (i in args.indices) localScope[params[i].name] = args[i]
			executeInScope(definition.body, localScope)
		}
	}
	override val signatures: List<FunctionSignature> = listOf(overload.signature)

	override fun selectOverload(interpreter: Interpreter, arguments: List<MathObject>) = if (overload.signature matches arguments) overload else null

	override fun toSourceString(): String = definition.astSource

	override fun toPrettyText(): Text = PrettyPrinter.print(definition) // Pretty printer is preferred over signature#toPrettyText since it contains the function body as well.

	companion object : MathObjectCodecProvider<UserDefinedFunction> {
		override val mapCodec: MapCodec<UserDefinedFunction> = FunctionDefinition.CODEC.fieldOf("definition").xmap(::UserDefinedFunction, UserDefinedFunction::definition)
	}
}

data class LambdaFunction(
	val declaration: Lambda,
	val closure: MutableEnvironment // Captures the scope where it was defined
) : MathFunction {
	private val overload: FunctionOverload by lazy {
        val params = declaration.parameters
        val sig = FunctionSignature(params, AnyConstraint) // Lambdas implicitly return ANY, unless we infer it (not needed for dynamic runtime)

        FunctionOverload(sig) { args ->
            val localScope = MapEnvironment(parent = closure) // The parent is closure instead of root env as opposed to UserDefinedFunction, since lambdas are not bound to any specific scope.
            for (i in args.indices) localScope[params[i].name] = args[i]
            executeInScope(declaration.body, localScope)
        }
    }

	override val signatures = listOf(overload.signature)

	override fun selectOverload(interpreter: Interpreter, arguments: List<MathObject>) = if (overload.signature matches arguments) overload else null

	override fun toPrettyText() = PrettyPrinter.print(declaration)
}