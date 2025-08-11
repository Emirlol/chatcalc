package ca.rttv.chatcalc.mixin.accessor;

import ca.rttv.chatcalc.display.ChatScreenDisplay;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextFieldWidget.class)
abstract class TextFieldWidgetAccessor extends ClickableWidget {
	private TextFieldWidgetAccessor(int x, int y, int width, int height, Text message) {
		super(x, y, width, height, message);
		throw new UnsupportedOperationException("Mixin shouldn't be instantiated");
	}

	// Using a mixin instead of ScreenEvents because the cursor location is a local value, and it's a lot of code to copy over
	@Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Ljava/lang/String;isEmpty()Z", ordinal = 1))
	private void chatcalc$renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci, @Local(ordinal = 6) int m) {
		if (!(getMessage().getContent() instanceof TranslatableTextContent translatable && translatable.getKey().equals("chat.editBox")))
			return;

		// No need to check the screen as the instance will only be set if the screen is a ChatScreen
		var instance = ChatScreenDisplay.Companion.getInstance();
		if (instance == null) return;
		if (instance.shouldRender()) instance.render(context, m - 8);
	}
}
