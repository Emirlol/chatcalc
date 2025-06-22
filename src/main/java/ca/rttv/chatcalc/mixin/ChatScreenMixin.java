package ca.rttv.chatcalc.mixin;

import ca.rttv.chatcalc.ChatCalc;
import ca.rttv.chatcalc.duck.ChatInputSuggesterDuck;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
	@Shadow
	protected TextFieldWidget chatField;

	@Shadow
	ChatInputSuggestor chatInputSuggestor;

	@Inject(at = @At("HEAD"), method = "keyPressed(III)Z", cancellable = true)
	private void keyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
		CompletableFuture<Suggestions> suggestions = ((ChatInputSuggesterDuck) this.chatInputSuggestor).chatcalc$pendingSuggestions();
		if ((suggestions != null && suggestions.isDone() && !suggestions.isCompletedExceptionally() && suggestions.getNow(null).isEmpty())) {
			if (keyCode == GLFW.GLFW_KEY_TAB && ChatCalc.tryParse(chatField.getText(), chatField.getCursor(), chatField::setText)) {
				cir.setReturnValue(true);
			}
		}
	}
}
