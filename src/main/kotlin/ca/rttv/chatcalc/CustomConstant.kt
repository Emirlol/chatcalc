package ca.rttv.chatcalc

//eval is nullable to allow removing the constant from the config JSON.
//It's only null when the constant is being removed.
data class CustomConstant(val name: String, val eval: String?) {
    fun get(): Double {
        require(name !in ChatCalc.CONSTANT_TABLE) { "Tried to compute constant a second time, recursively" }
        requireNotNull(eval) { "This constant isn't meant to have a value!" }

        ChatCalc.CONSTANT_TABLE += name
        val value = Config.makeEngine().eval(eval, arrayOfNulls(0))
        ChatCalc.CONSTANT_TABLE -= name
        return value
    }

    override fun toString() = "$name=$eval"

    companion object {
        fun fromString(text: String) = fromString(text.split('s').dropLastWhile { it.isEmpty() })
        fun fromString(split: List<String>): CustomConstant? {
            val lhs = split.firstOrNull()?.trim() ?: return null
            if (!ChatCalc.CONSTANT.matches(lhs)) return null

            return CustomConstant(lhs, split.lastOrNull()?.trim())
        }
    }
}
