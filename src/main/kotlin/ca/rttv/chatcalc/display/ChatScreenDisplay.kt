package ca.rttv.chatcalc.display

import ca.rttv.chatcalc.ChatCalc.tryParse
import ca.rttv.chatcalc.ChatHelper
import ca.rttv.chatcalc.mixin.accessor.ChatInputSuggesterAccessor
import ca.rttv.chatcalc.mixin.accessor.ChatScreenAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.ChatScreen

class ChatScreenDisplay(val chatField: EditBox, val suggester: ChatInputSuggesterAccessor) : DisplayAbove() {
	override var x = 0 // This is set by the mixin
	override val y get() = chatField.y - 4
	override val centered = false

	override fun parseWord(): String = ChatHelper.getSection(chatField.value, chatField.cursorPosition)

	override fun allowKeyPress(keycode: Int): Boolean {
		if (super.allowKeyPress(keycode)) return true

		val success = tryParse(chatField.value, chatField.cursorPosition) { replacement ->
			chatField.setValue(replacement)
		}

		if (success) {
			return false
		}

		val suggestions = suggester.pendingSuggestions
		return suggestions == null || !suggestions.isDone || suggestions.isCompletedExceptionally || !suggestions.getNow(null).isEmpty
	}

	companion object {
		var instance: ChatScreenDisplay? = null
			private set

		fun init() {
			ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
				if (screen !is ChatScreen) return@register

				val accessor = screen as ChatScreenAccessor
				instance = ChatScreenDisplay(accessor.chatField, accessor.chatInputSuggestor as ChatInputSuggesterAccessor)

				accessor.chatField.setResponder {
					instance!!.x = accessor.chatField.x + 4
				}

				ScreenKeyboardEvents.allowKeyPress(screen).register { _, keyEvent ->
					instance!!.allowKeyPress(keyEvent.key)
				}
			}
		}
	}
}