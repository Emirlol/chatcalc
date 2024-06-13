package ca.rttv.chatcalc

data class CustomConstant(val name: String, val eval: String) {
    fun get(): Double {
        require(name !in ChatCalc.CONSTANT_TABLE) { "Tried to compute constant a second time, recursively" }
        ChatCalc.CONSTANT_TABLE += name
        val value = Config.makeEngine().eval(eval, arrayOfNulls(0))
        ChatCalc.CONSTANT_TABLE -= name
        return value
    }

    override fun toString() = "$name=$eval"

    companion object {
        fun fromString(text: String): CustomConstant? {
            val split = text.split('=')
            if (split.size != 2) return null

            val lhs = split.first().trim()
            if (!ChatCalc.CONSTANT.matches(lhs)) return null
            val rhs = split.last().trim()

            return CustomConstant(lhs, rhs)
        }
    }
}
