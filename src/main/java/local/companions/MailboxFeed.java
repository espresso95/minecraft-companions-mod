package local.companions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** A read-only cursor for both HUD messages and incoming-message notifications. */
final class MailboxFeed {
    private final ControllerClient api = CompanionMailbox.API;
    private String binding, session;
    private long cursor = -1, generation, nextPoll;
    private boolean fetching;

    void reset() {
        if (binding == null) return;
        binding = session = null; cursor = -1; generation++; fetching = false; nextPoll = 0;
    }
    void tick(MinecraftClient client) {
        String current = LocalConnection.key(client);
        if (!current.equals(binding)) { reset(); binding = current; }
        if (fetching || System.currentTimeMillis() < nextPoll) return;
        fetching = true; nextPoll = System.currentTimeMillis() + 2000;
        long ticket = ++generation;
        boolean mirror = CompanionMailbox.CHAT.showAgentChat();
        api.request("GET", "/v1/messages?limit=50&after=" + (cursor < 0 ? "latest" : cursor), null)
            .whenComplete((result, failure) -> client.execute(() -> {
                if (ticket != generation || !current.equals(LocalConnection.key(client))) return;
                fetching = false;
                if (failure != null) return;
                String incomingSession = ControllerClient.text(result, "session", "");
                if (session != null && !session.equals(incomingSession)) { session = incomingSession; cursor = -1; nextPoll = 0; return; }
                session = incomingSession;
                for (var element : result.getAsJsonArray("messages")) {
                    var message = element.getAsJsonObject();
                    if (message.get("agentConversation").getAsBoolean()) {
                        if (mirror && CompanionMailbox.CHAT.showAgentChat()) {
                            Text line = Text.literal("[AI] ").formatted(Formatting.DARK_AQUA)
                                .append(Text.literal(ControllerClient.text(message, "sender", "?") + " → " + ControllerClient.text(message, "recipient", "?") + ": ").formatted(Formatting.AQUA))
                                .append(Text.literal(ControllerClient.text(message, "text", "")).formatted(Formatting.WHITE));
                            // Local display only: never sends a chat packet or a mailbox acknowledgement.
                            client.inGameHud.getChatHud().addMessage(line);
                        }
                    } else if (client.player != null && client.getSession().getUsername().equalsIgnoreCase(ControllerClient.text(message, "recipient", "")) && !(client.currentScreen instanceof CompanionsScreen)) {
                        client.player.sendMessage(Text.literal("Message from " + ControllerClient.text(message, "sender", "companion") + " — press K to open"), true);
                    }
                }
                cursor = ControllerClient.count(result, "nextAfter");
                if (result.get("hasMore").getAsBoolean()) nextPoll = 0;
            }));
    }
}
