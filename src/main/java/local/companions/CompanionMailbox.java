package local.companions;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class CompanionMailbox implements ClientModInitializer {
    static final ControllerClient API = new ControllerClient();
    private long nextPoll;
    static final ChatPreferences CHAT = new ChatPreferences();
    private final MailboxFeed feed = new MailboxFeed();

    static boolean localWorld(MinecraftClient client) { return LocalConnection.server(client) != null; }
    public static String statusFor(String name) { return API.statusFor(name); }

    @Override public void onInitializeClient() {
        KeyBinding open = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.companion_mailbox.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "category.companion_mailbox"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (open.wasPressed()) {
                if (localWorld(client)) client.setScreen(API.paired() ? new CompanionsScreen() : new PairingScreen());
                else if (client.player != null) client.player.sendMessage(Text.literal("Companions is available in local worlds only."), true);
            }
            if (!localWorld(client) || !API.paired()) { feed.reset(); return; }
            if (System.currentTimeMillis() >= nextPoll) {
                nextPoll = System.currentTimeMillis() + 2000;
                API.refresh();

            }
            feed.tick(client);
        });
    }
}
