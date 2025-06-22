package ca.rttv.chatcalc.mixin;

import ca.rttv.chatcalc.ChatCalc;
import ca.rttv.chatcalc.ChatHelper;
import ca.rttv.chatcalc.FunctionParameter;
import ca.rttv.chatcalc.MathEngine;
import ca.rttv.chatcalc.config.ConfigManager;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalDouble;

@Mixin(TextFieldWidget.class)
abstract class TextFieldWidgetMixin extends ClickableWidget {
	protected TextFieldWidgetMixin(int x, int y, int width, int height, Text message) {
		super(x, y, width, height, message);
	}

	@Shadow
	@Final
	private TextRenderer textRenderer;

	@Shadow
	public native int getCursor();

	@Shadow
	public native String getText();

	@Unique
	@Nullable
	private Pair<String, OptionalDouble> evaluationCache;

	@Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Ljava/lang/String;isEmpty()Z", ordinal = 1))
	private void chatcalc$renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci, @Local(ordinal = 6) int m) {
		displayAbove(context, m);
	}

	@Unique
	private void displayAbove(DrawContext context, int cursorX) {
		if (!(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) return;

		if (!(getMessage().getContent() instanceof TranslatableTextContent translatable && translatable.getKey().equals("chat.editBox"))) {
			return;
		}

		if (!ConfigManager.INSTANCE.getConfig().getDisplayAbove()) {
			evaluationCache = null;
			return;
		}

		String word = ChatHelper.INSTANCE.getSection(getText(), getCursor());

		if (ChatCalc.NUMBER.matches(word)) {
			evaluationCache = null;
			return;
		}

		try {
			double result;
			if (evaluationCache != null && evaluationCache.getFirst().equals(word)) {
				if (evaluationCache.getSecond().isEmpty()) {
					return;
				}

				result = evaluationCache.getSecond().getAsDouble();
			} else {
				ChatCalc.CONSTANT_TABLE.clear();
				ChatCalc.FUNCTION_TABLE.clear();
				result = MathEngine.Companion.of().eval(word, new FunctionParameter[0]);
				evaluationCache = new Pair<>(word, OptionalDouble.of(result));
			}
			Text text = Text.literal("=" + ConfigManager.INSTANCE.getConfig().getDecimalFormat().format(result)).withColor(0xffcdd6f4); // This is equal to ColorPalette.TEXT, but it's not accessible here for some reason.
			context.drawTooltip(textRenderer, text, cursorX - 8, getY() - 4);
		} catch (Exception ignored) {
			evaluationCache = new Pair<>(word, OptionalDouble.empty());
		}
	}
}
