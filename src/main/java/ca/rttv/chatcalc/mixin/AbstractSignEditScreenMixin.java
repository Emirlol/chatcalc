package ca.rttv.chatcalc.mixin;

import ca.rttv.chatcalc.ChatCalc;
import ca.rttv.chatcalc.ChatHelper;
import ca.rttv.chatcalc.Config;
import ca.rttv.chatcalc.FunctionParameter;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.util.SelectionManager;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.OptionalDouble;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen {

	/**
	 * Not meant to be used.
	 */
	private AbstractSignEditScreenMixin(Text title) {
		super(title);
	}

	@Unique
	@Nullable
	private Pair<String, OptionalDouble> evaluationCache;

	@Final
	@Shadow
	private String[] messages;

	@Shadow
	private int currentRow;

	@Shadow
	@Nullable
	private SelectionManager selectionManager;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawCenteredTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;III)V"))
	public void chatcalc$render(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		displayAbove(context);
	}

	@Unique
	private void displayAbove(DrawContext context) {
		if (selectionManager == null) return;
		String line = messages[currentRow];
		if (line.isEmpty()) return;

		if (!Config.INSTANCE.displayAbove()) {
			evaluationCache = null;
			return;
		}


		String word = ChatHelper.INSTANCE.getSection(line, selectionManager.getSelectionEnd());

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
				result = Config.INSTANCE.makeEngine().eval(word, new FunctionParameter[0]);
				evaluationCache = new Pair<>(word, OptionalDouble.of(result));
			}
			Text text = Text.literal("=" + Config.INSTANCE.getDecimalFormat().format(result));
			context.drawTooltip(textRenderer, text, (width-textRenderer.getWidth(text))/2 - 11, 71);
		} catch (Throwable ignored) {
			evaluationCache = new Pair<>(word, OptionalDouble.empty());
		}
	}
}
