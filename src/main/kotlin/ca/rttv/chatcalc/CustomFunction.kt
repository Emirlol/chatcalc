package ca.rttv.chatcalc

import com.mojang.datafixers.util.Pair

//eval is nullable to allow removing the function from the config JSON.
//It's only null when the constant is being removed.
data class CustomFunction(val name: String, val eval: String?, val params: Array<String>) {
    fun get(values: DoubleArray): Double {
        require(values.size == params.size) { "Invalid number of arguments for custom function" }
        requireNotNull(eval) { "This function isn't meant to be called!" }

        val pair = Pair(name, params.size)

        require(!ChatCalc.FUNCTION_TABLE.contains(pair)) { "Tried to call function twice, recursively" }

        val parameters = arrayOfNulls<FunctionParameter>(values.size)
        for (i in parameters.indices) {
            parameters[i] = FunctionParameter(params[i], values[i])
        }

        ChatCalc.FUNCTION_TABLE.add(pair)
        val value = Config.makeEngine().eval(eval, parameters)
        ChatCalc.FUNCTION_TABLE.remove(pair)
        return value
    }

    override fun toString() = name + '(' + params.joinToString(ChatCalc.SEPARATOR) + ")=" + eval

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CustomFunction

        if (name != other.name) return false
        if (eval != other.eval) return false
        if (!params.contentEquals(other.params)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + eval.hashCode()
        result = 31 * result + params.contentHashCode()
        return result
    }

    companion object {
        private val FUNCTION_REGEX = Regex("(?<name>[a-zA-Z]+)\\((?<params>(?:[a-zA-Z]+;)*?[a-zA-Z]+)\\)")
        fun fromString(text: String) = fromString(text.split('s').dropLastWhile { it.isEmpty() })
        fun fromString(split: List<String>): CustomFunction? {
            val lhs = split.firstOrNull()?.trim() ?: return null
            val match = FUNCTION_REGEX.matchEntire(lhs) ?: return null

            return CustomFunction(match.groupValues[1], split.lastOrNull()?.trim(), match.groupValues[2].split(ChatCalc.SEPARATOR).toTypedArray())
        }
    }
}
