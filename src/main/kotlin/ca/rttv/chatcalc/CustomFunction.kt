package ca.rttv.chatcalc

import com.mojang.datafixers.util.Pair

data class CustomFunction(val name: String, val eval: String, val params: Array<String>) {
    fun get(values: DoubleArray): Double {
        require(values.size == params.size) { "Invalid amount of arguments for custom function" }

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
        fun fromString(text: String): CustomFunction? {
            val split = text.split('=')
            if (split.size != 2) return null

            val lhs = split.first().trim()
            val match = ChatCalc.FUNCTION.matchEntire(lhs) ?: return null
            val rhs = split.last().trim()

            return CustomFunction(match.groupValues[1], rhs, match.groupValues[2].split(ChatCalc.SEPARATOR).toTypedArray())
        }
    }
}
