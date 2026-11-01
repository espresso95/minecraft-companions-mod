package local.companions;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.*;
import java.io.IOException;

final class ChatPreferences {
    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("companion-chat.json");
    private boolean showAgentChat = true;

    ChatPreferences() {
        try {
            if (Files.isRegularFile(file)) {
                var value = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (value.has("showAgentChat")) showAgentChat = value.get("showAgentChat").getAsBoolean();
            }
        } catch (Exception error) { System.err.println("Could not read companion chat preference: " + error.getMessage()); }
    }
    boolean showAgentChat() { return showAgentChat; }
    void showAgentChat(boolean value) throws IOException {
        JsonObject settings = new JsonObject(); settings.addProperty("showAgentChat", value);
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(temporary, settings.toString() + "\n");
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        showAgentChat = value;
    }
}
