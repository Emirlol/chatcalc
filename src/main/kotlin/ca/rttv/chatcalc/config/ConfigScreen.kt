package ca.rttv.chatcalc.config

import me.ancientri.rimelib.util.color.ColorPalette
import me.ancientri.rimelib.util.text.text
import net.minecraft.client.gui.screen.ConfirmScreen
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.*
import net.minecraft.client.gui.widget.ButtonWidget.PressAction
import net.minecraft.screen.ScreenTexts
import java.text.DecimalFormat

const val CONFIG_ENTRY_WIDTH = 250
const val ROW_HEIGHT = 20

class ConfigScreen(val parent: Screen?) : Screen("ChatCalc Config Screen".text(ColorPalette.TEXT)) {
	var initialConfig = ConfigManager.config.copy()
	var builder = ConfigManager.builder(ConfigManager.config.copy())
	val layout = ThreePartsLayoutWidget(this)

	lateinit var doneButton: ButtonWidget
	lateinit var cancelButton: ButtonWidget

	lateinit var scrollableLayoutWidget: ScrollableLayoutWidget

	lateinit var decimalFormatTextField: TextFieldWidget
	lateinit var errorDisplay: TextWidget // For DecimalFormat errors
	lateinit var radiansButton: CyclingButtonWidget<Boolean>
	lateinit var displayAboveButton: CyclingButtonWidget<Boolean>
	lateinit var copyTypeButton: CyclingButtonWidget<CopyType>

	override fun close() {
		if (isConfigChanged()) {
			val confirmScreen = ConfirmScreen(
				::onConfirmLeaving,
				"You have unsaved changes!".text(ColorPalette.ERROR),
				"Are you sure you want to leave without saving?".text(ColorPalette.TEXT),
				ScreenTexts.PROCEED,
				ScreenTexts.BACK
			)
			client?.setScreen(confirmScreen)
		} else actuallyClose()
	}

	fun actuallyClose() {
		client?.setScreen(parent)
	}

	override fun refreshWidgetPositions() {
		layout.refreshPositions()
	}

	override fun init() {
		this.initHeader()
		this.initBody()
		this.initFooter()
		layout.forEachChild(::addDrawableChild)
		this.refreshWidgetPositions()
	}

	fun initHeader() {
		layout.addHeader(title, textRenderer)
	}

	fun initBody() {
		val verticalLayout = DirectionalLayoutWidget.vertical().spacing(4)

		radiansButton = CyclingButtonWidget.onOffBuilder(
			ScreenTexts.ON.copy().withColor(ColorPalette.SUCCESS.value),
			ScreenTexts.OFF.copy().withColor(ColorPalette.ERROR.value)
		)
			.initially(builder.radians)
			.tooltip { Tooltip.of("Whether angles should be in radians or degrees.".text(ColorPalette.TEXT)) }
			.build("Radians".text(ColorPalette.TEXT)) { button, value ->
				builder.radians = value
			}
			.apply {
				width = CONFIG_ENTRY_WIDTH
			}
		verticalLayout.add(
			entryFactory(
				radiansButton,
				onReset = {
					builder.radians = initialConfig.radians
					radiansButton.value = builder.radians
				}
			)
		)

		displayAboveButton = CyclingButtonWidget.onOffBuilder(
			ScreenTexts.ON.copy().withColor(ColorPalette.SUCCESS.value),
			ScreenTexts.OFF.copy().withColor(ColorPalette.ERROR.value)
		)
			.initially(builder.displayAbove)
			.tooltip { Tooltip.of("Whether the result of operations should be displayed above the input.".text(ColorPalette.TEXT)) }
			.build("Display Above".text(ColorPalette.TEXT)) { button, value ->
				builder.displayAbove = value
			}
			.apply {
				width = CONFIG_ENTRY_WIDTH
			}
		verticalLayout.add(
			entryFactory(
				displayAboveButton,
				onReset = {
					builder.displayAbove = initialConfig.displayAbove
					displayAboveButton.value = builder.displayAbove
				}
			)
		)

		copyTypeButton = CyclingButtonWidget.builder<CopyType> { type ->
			when (type) {
				CopyType.NONE -> "None".text(ColorPalette.ERROR)
				CopyType.CLIPBOARD -> "Clipboard".text(ColorPalette.TEXT)
				CopyType.CHAT_HISTORY -> "Chat History".text(ColorPalette.TEXT)
			}
		}
			.initially(builder.copyType)
			.tooltip {
				Tooltip.of(text {
					"Where to save the result of a calculation after pressing TAB." colored ColorPalette.TEXT
					+ScreenTexts.LINE_BREAK
					when (it) {
						CopyType.NONE -> {
							"None" colored ColorPalette.ACCENT
							": The result won't be saved anywhere." colored ColorPalette.TEXT
						}

						CopyType.CLIPBOARD -> {
							"Clipboard" colored ColorPalette.ACCENT
							": The result will be copied to your device's clipboard." colored ColorPalette.TEXT
						}

						CopyType.CHAT_HISTORY -> {
							"Chat History" colored ColorPalette.ACCENT
							": The result will be added to your chat log, so you can use your arrow keys to access the results." colored ColorPalette.TEXT
						}
					}
				}
				)
			}
			.values(CopyType.entries)
			.build("Copy Type".text(ColorPalette.TEXT)) { button, value ->
				builder.copyType = value
			}
			.apply {
				width = CONFIG_ENTRY_WIDTH
			}
		verticalLayout.add(
			entryFactory(
				copyTypeButton,
				onReset = {
					builder.copyType = initialConfig.copyType
					copyTypeButton.value = builder.copyType
				}
			)
		)

		decimalFormatTextField = object : TextFieldWidget(textRenderer, CONFIG_ENTRY_WIDTH, ROW_HEIGHT, "Decimal Format".text) {
			init {
				text = builder.decimalFormat.toPattern()
				setChangedListener {
					if (text.isEmpty()) {
						errorDisplay.message = "DecimalFormat pattern cannot be empty.".text(ColorPalette.ERROR)
						return@setChangedListener
					}
					try {
						builder.decimalFormat = DecimalFormat(text)
						errorDisplay.message = "".text
					} catch (e: IllegalArgumentException) {
						errorDisplay.message = "Invalid DecimalFormat pattern.".text(ColorPalette.ERROR)
						return@setChangedListener
					}
				}
			}
		}.apply {
			setTooltip(Tooltip.of("The decimal format pattern used to prettify the numbers when displaying above the input. Ask your friendly neighborhood LLM to help you with this if you don't know how Java's DecimalFormat patterns work.".text(ColorPalette.TEXT)))
		}
		verticalLayout.add(
			entryFactory(
				decimalFormatTextField,
				onReset = {
					builder.decimalFormat = initialConfig.decimalFormat
					decimalFormatTextField.text = builder.decimalFormat.toPattern()
				}
			)
		)

		errorDisplay = verticalLayout.add(TextWidget(CONFIG_ENTRY_WIDTH, ROW_HEIGHT, "".text, textRenderer))

		verticalLayout.forEachChild(::addDrawableChild)
		scrollableLayoutWidget = layout.addBody(ScrollableLayoutWidget(client, verticalLayout, layout.contentHeight))
	}

	fun entryFactory(widget: ClickableWidget, onReset: PressAction): DirectionalLayoutWidget {
		val horizontal = DirectionalLayoutWidget.horizontal()
		horizontal.spacing(4)
		horizontal.add(widget)
		horizontal.add(ButtonWidget.builder("Reset".text, onReset).size(50, ROW_HEIGHT).build()).active = false // Start from false
		return horizontal
	}

	fun initFooter() {
		val layoutWidget = layout.addFooter(DirectionalLayoutWidget.horizontal().spacing(8))
		doneButton = layoutWidget.add(ButtonWidget.builder(ScreenTexts.DONE, ::onDone).width(200).build())
		cancelButton = layoutWidget.add(ButtonWidget.builder(ScreenTexts.CANCEL, ::onCancel).width(200).build())
	}

	fun onDone(widget: ButtonWidget) {
		initialConfig = ConfigManager.applyBuilder(builder)
		builder = ConfigManager.builder(initialConfig)
		actuallyClose()
	}

	fun onCancel(widget: ButtonWidget) {
		close()
	}

	fun onConfirmLeaving(confirmed: Boolean) {
		if (confirmed) actuallyClose()
		else client?.setScreen(this)
	}

	fun isConfigChanged(): Boolean {
		val newConfig = builder.build() // The builder doesn't have any side effects and is mutable, so this is safe to call multiple times
		return newConfig != initialConfig
	}
}