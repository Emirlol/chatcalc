package ca.rttv.chatcalc.mixin.accessor;

import ca.rttv.chatcalc.display.ChatScreenDisplay;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EditBox.class)
abstract class TextFieldWidgetAccessor extends AbstractWidget {
	private TextFieldWidgetAccessor(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
		throw new UnsupportedOperationException("Mixin shouldn't be instantiated");
	}

	@Inject(method = "extractWidgetRenderState", at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/EditBox;hint:Lnet/minecraft/network/chat/Component;", opcode = Opcodes.GETFIELD))
	private void chatcalc$renderWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci, @Local(name = "cursorX") int cursorX) {
		if (!(getMessage().getContents() instanceof TranslatableContents translatable && translatable.getKey().equals("chat.editBox")))
			return;

		// No need to check the screen as the instance will only be set if the screen is a ChatScreen
		var instance = ChatScreenDisplay.Companion.getInstance();
		if (instance == null) return;
		if (instance.shouldRender()) instance.render(graphics, cursorX - 8);
	}
}
