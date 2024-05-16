package ca.rttv.chatcalc

interface MathEngine {
    fun eval(input: String, paramaters: Array<FunctionParameter?>): Double
}
