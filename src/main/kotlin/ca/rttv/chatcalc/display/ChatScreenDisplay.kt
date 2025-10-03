package ca.rttv.chatcalc.display

import ca.rttv.chatcalc.ChatCalc.tryParse
import ca.rttv.chatcalc.ChatHelper
import ca.rttv.chatcalc.mixin.accessor.ChatInputSuggesterAccessor
import ca.rttv.chatcalc.mixin.accessor.ChatScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.widget.TextFieldWidget

class ChatScreenDisplay(val chatField: TextFieldWidget, val suggester: ChatInputSuggesterAccessor) : DisplayAbove() {
	override var x = 0 // This is set by the mixin
	override val y get() = chatField.y - 4
	override val centered = false

	override fun parseWord(): String = ChatHelper.getSection(chatField.text, chatField.cursor)

	override fun allowKeyPress(keycode: Int): Boolean = suggester.pendingSuggestions.let { suggestions ->
		super.allowKeyPress(keycode)
				|| suggestions == null
				|| !suggestions.isDone
				|| suggestions.isCompletedExceptionally
				|| !suggestions.getNow(null).isEmpty
				|| !tryParse(chatField.text, chatField.cursor, chatField::setText)
	}

	companion object {
		var instance: ChatScreenDisplay? = null
			private set

		fun init() {
			ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
				if (screen !is ChatScreen) return@register
				screen as ChatScreenAccessor
				instance = ChatScreenDisplay(screen.chatField, screen.chatInputSuggestor as ChatInputSuggesterAccessor)
				ScreenKeyboardEvents.allowKeyPress(screen).register { _, keyInput, ->
					instance!!.allowKeyPress(keyInput.keycode)
				}
			}
		}
	}
}