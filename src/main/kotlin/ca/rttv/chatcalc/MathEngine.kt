package ca.rttv.chatcalc

interface MathEngine {
    fun eval(input: String, parameters: Array<FunctionParameter?>): Double
}
