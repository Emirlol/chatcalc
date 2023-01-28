package ca.rttv.chatcalc;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;

import java.util.regex.Pattern;

public class ChatCalc {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Pattern NUMBER = Pattern.compile("[-+]?\\d*\\.?\\d+([eE][-+]?\\d+)?");

    @Contract(value = "_->_", mutates = "param1")
    public static boolean tryParse(TextFieldWidget field) {
        final MinecraftClient client = MinecraftClient.getInstance();
        String originalText = field.getText();
        int cursor = field.getCursor();
        String text = originalText.substring(0, cursor);
        String[] split = text.split("=");
        if (split.length == 2) {
            if (Config.JSON.has(split[0])) {
                Config.JSON.addProperty(split[0], split[1]);
                Config.refreshJson();
                return ChatHelper.replaceWord(field, "");
            } else if (split[0].matches("[A-Za-z]+\\([A-Za-z]+\\)")) {
                Config.FUNCTIONS.put(split[0].split("\\(")[0], new Pair<>(MathEngine.tokenize(split[1]), split[0].split("[()]")[1]));
                Config.refreshJson();
                return ChatHelper.replaceWord(field, "");
            }
        } else if (Config.JSON.has(split[0])) {
            return ChatHelper.replaceWord(field, Config.JSON.get(split[0]).getAsString());
        } else if (split[0].length() > 0 && Config.JSON.has(split[0].substring(0, split[0].length() - 1)) && split[0].endsWith("?") && client.player != null) {
            client.player.sendMessage(Text.translatable("chatcalc." + split[0].substring(0, split[0].length() - 1) + ".description"));
            return false;
        }

        String word = ChatHelper.getWord(originalText, cursor);
        if (NUMBER.matcher(word).matches()) {
            return false;
        } else {
            boolean add = false;
            if (word.endsWith("=")) {
                word = word.substring(0, word.length() - 1);
                add = true;
            }
            try {
                String solution = Config.getDecimalFormat().format(MathEngine.eval(word));
                Config.saveToChatHud(originalText);
                Config.saveToClipboard(originalText);
                return add ? ChatHelper.addWordAfterIndex(field, solution) : ChatHelper.replaceWord(field, solution);
            } catch (Exception e) {
                if (Config.logExceptions()) {
                    LOGGER.error("ChatCalc Parse Error: ", e);
                }
                return false;
            }
        }
    }
}