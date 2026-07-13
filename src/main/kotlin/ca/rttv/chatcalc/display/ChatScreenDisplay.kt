package ca.rttv.chatcalc.display

import ca.rttv.chatcalc.ChatHelper
import ca.rttv.chatcalc.Section
import ca.rttv.chatcalc.mixin.accessor.ChatInputSuggesterAccessor
import ca.rttv.chatcalc.mixin.accessor.ChatScreenAccessor
import ca.rttv.chatcalc.mixin.accessor.TextFieldWidgetAccessor
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.minecraft.client.gui.screen.ChatScreen
import net.minecraft.client.gui.widget.TextFieldWidget

class ChatScreenDisplay(val chatField: TextFieldWidget, val suggester: ChatInputSuggesterAccessor) : Display() {
	override var x = 0 // This is set by the mixin
	override val y get() = chatField.y - 4
	override val centered = false

	override fun parseSection(): Section = ChatHelper.getSection(chatField.text, chatField.cursor)

	override fun allowKeyPress(keycode: Int): Boolean = suggester.pendingSuggestions.let { suggestions ->
		val section = parseSection() ?: return true
		super.allowKeyPress(keycode)
				|| suggestions == null
				|| !suggestions.isDone
				|| suggestions.isCompletedExceptionally
				|| !suggestions.getNow(null).isEmpty
				|| !super.tryParse(section, chatField::setText)
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
				val consumer = (screen.chatField as TextFieldWidgetAccessor).changedListener

				if (consumer == null) {
					screen.chatField.setChangedListener { instance!!.calculateForRendering() }
				} else {
					screen.chatField.setChangedListener(consumer.andThen { instance!!.calculateForRendering() })
				}
			}
		}
	}
}