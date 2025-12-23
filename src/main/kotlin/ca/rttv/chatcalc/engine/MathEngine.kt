@file:Suppress("NOTHING_TO_INLINE")

package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.config.UserEnvironmentManager
import ca.rttv.chatcalc.engine.astvisitors.Interpreter
import ca.rttv.chatcalc.engine.lexer.Lexer
import ca.rttv.chatcalc.engine.parser.Parser

fun interface MathEngine {
	fun eval(input: String, dryRun: Boolean): EngineResult

	companion object Default : MathEngine {
		override fun eval(input: String, dryRun: Boolean): EngineResult {
			val (lexed, source) = Lexer(input).scanTokens()
			val parsed = Parser(lexed, source).parse()
			return Interpreter(UserEnvironmentManager::config, dryRun).interpret(parsed)
		}
	}
}
