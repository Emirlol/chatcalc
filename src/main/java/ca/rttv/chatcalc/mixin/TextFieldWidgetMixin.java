package ca.rttv.chatcalc.mixin;

import ca.rttv.chatcalc.ChatCalc;
import ca.rttv.chatcalc.ChatHelper;
import ca.rttv.chatcalc.Config;
import ca.rttv.chatcalc.FunctionParameter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import com.mojang.datafixers.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.OptionalDouble;

@Mixin(TextFieldWidget.class)
abstract class TextFieldWidgetMixin extends ClickableWidget {
    protected TextFieldWidgetMixin(int x, int y, int width, int height, Text message) {
        super(x, y, width, height, message);
    }

    @Shadow @Final private TextRenderer textRenderer;

    @Shadow public native int getCursor();

    @Shadow public native String getText();

    @Unique
    @Nullable
    private Pair<String, OptionalDouble> evaluationCache;

    @Inject(method = "renderWidget", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target = "Ljava/lang/String;isEmpty()Z", ordinal = 1), locals = LocalCapture.CAPTURE_FAILSOFT)
    private void chatcalc$renderWidget(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci, int i, int j, String string, boolean bl, boolean bl2, int k, int l, int m, int n, boolean bl3, int o) {
        displayAbove(context, o, l);
    }

    @Unique
    private void displayAbove(DrawContext context, int x, int y) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatScreen)) return;

        if (!(getMessage().getContent() instanceof TranslatableTextContent translatable && translatable.getKey().equals("chat.editBox"))) {
            return;
        }

        if (!Config.INSTANCE.displayAbove()) {
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
                result = Config.INSTANCE.makeEngine().eval(word, new FunctionParameter[0]);
                evaluationCache = new Pair<>(word, OptionalDouble.of(result));
            }
            Text text = Text.literal("=" + Config.INSTANCE.getDecimalFormat().format(result));
            context.drawTooltip(textRenderer, text, x - 8, y - 4);
        } catch (Throwable ignored) {
            evaluationCache = new Pair<>(word, OptionalDouble.empty());
        }
    }
}
