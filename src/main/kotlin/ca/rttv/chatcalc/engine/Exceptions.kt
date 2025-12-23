package ca.rttv.chatcalc.engine

import ca.rttv.chatcalc.engine.lexer.Token
import me.ancientri.rimelib.util.text.TextBuilder
import me.ancientri.rimelib.util.text.text
import net.minecraft.text.PlainTextContent
import net.minecraft.text.Style
import net.minecraft.text.Text

/**
 * A base class for all exceptions thrown by the language engine.
 */
abstract class EngineError(val messageText: Text) : RuntimeException(messageText.string)

/**
 * An error that occurs during the Lexing phase.
 *
 */
class LexerError(message: Text) : EngineError(message) {
    constructor(builder: TextBuilder.() -> Unit) : this(text(builder))
}

/**
 * An error that occurs during the Parsing phase
 */
class ParserError(val token: Token, message: Text) : EngineError(message) {
    constructor(token: Token, builder: TextBuilder.(Token) -> Unit) : this(token, TextBuilder(PlainTextContent.EMPTY, Style.EMPTY).apply { builder(token) }.build())
}

/**
 * An error that occurs during the Evaluation/Interpretation phase
 */
class EvaluationError(val token: Token, message: Text) : EngineError(message) {
    constructor(token: Token, builder: TextBuilder.(Token) -> Unit) : this(token, TextBuilder(PlainTextContent.EMPTY, Style.EMPTY).apply { builder(token) }.build())
}

class EnvironmentError(message: Text) : EngineError(message) {
    constructor(builder: TextBuilder.() -> Unit) : this(text(builder))
}