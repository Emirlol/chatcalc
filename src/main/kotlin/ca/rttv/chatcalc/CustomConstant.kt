package ca.rttv.chatcalc

data class CustomConstant(val name: String, val eval: String) {
    fun get(): Double {
        require(!ChatCalc.CONSTANT_TABLE.contains(name)) { "Tried to compute constant a second time, recursively" }
        ChatCalc.CONSTANT_TABLE.add(name)
        val value = Config.makeEngine().eval(eval, arrayOfNulls(0))
        ChatCalc.CONSTANT_TABLE.remove(name)
        return value
    }

    override fun toString(): String {
        return "$name=$eval"
    }

    companion object {
        fun fromString(text: String): CustomConstant? {
            val equalsIdx = text.indexOf('=')
            if (equalsIdx == -1) return null


            val lhs = text.substring(0, equalsIdx)
            val rhs = text.substring(equalsIdx + 1)

            if (!ChatCalc.CONSTANT.matcher(lhs).matches()) return null

            return CustomConstant(lhs, rhs)
        }
    }
}
