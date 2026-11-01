package local.companions;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

final class ChatPanel {
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ControllerClient api = CompanionMailbox.API;
    private final List<OrderedText> lines = new ArrayList<>();
    private JsonArray messages = new JsonArray();
    private TextFieldWidget composer;
    private ButtonWidget older, latest, mirror;
    private String selected, sender = "", recipient = "", requested = "", notice = "";
    private int x, top, bottom, width, scroll;
    private long before, nextRefresh;
    private boolean fetching, hasOlder, agentsOnly;
    void select(String name) { selected = name; sender = recipient = ""; before = 0; scroll = 0; nextRefresh = 0; messages = new JsonArray(); lines.clear(); requested = ""; }
    void mount(CompanionsScreen parent, int x, int width, Consumer<ClickableWidget> add, Runnable send) {
        String draft = composer == null ? "" : composer.getText();
        this.x = x; this.width = width; top = selected == null ? 116 : 92; bottom = parent.height - 76;
        if (selected == null) {
            int modeWidth = 96, choiceWidth = (width - modeWidth - 8) / 2;
            add.accept(CheckboxWidget.builder(Text.literal("Agents only"), client.textRenderer).pos(x, 90).maxWidth(modeWidth).checked(agentsOnly).callback((box, checked) -> { agentsOnly = checked; before = 0; scroll = 0; nextRefresh = 0; requested = ""; }).build());
            add.accept(ButtonWidget.builder(Text.literal("From: " + (sender.isEmpty() ? "Anyone" : sender)), b -> choose(parent, true)).dimensions(x + modeWidth + 4, 90, choiceWidth, 20).build());
            add.accept(ButtonWidget.builder(Text.literal("To: " + (recipient.isEmpty() ? "Anyone" : recipient)), b -> choose(parent, false)).dimensions(x + modeWidth + choiceWidth + 8, 90, choiceWidth, 20).build());
        }
        composer = new TextFieldWidget(client.textRenderer, x, parent.height - 61, width - 64, 20, Text.literal("Message")); composer.setMaxLength(2000); composer.setText(draft); composer.setPlaceholder(Text.literal(selected == null ? "Message all companions…" : "Message " + selected + "…")); add.accept(composer);
        add.accept(ButtonWidget.builder(Text.literal("Send"), b -> send.run()).dimensions(x + width - 58, parent.height - 61, 58, 20).build());
        older = ButtonWidget.builder(Text.literal("Older"), b -> { if (!messages.isEmpty()) { before = ControllerClient.count(messages.get(0).getAsJsonObject(), "id"); nextRefresh = 0; } }).dimensions(x, parent.height - 35, 60, 20).build(); add.accept(older);
        latest = ButtonWidget.builder(Text.literal("Latest"), b -> { before = 0; scroll = 0; nextRefresh = 0; }).dimensions(x + 64, parent.height - 35, 60, 20).build(); add.accept(latest);
        mirror = ButtonWidget.builder(Text.literal("Game chat: " + (CompanionMailbox.CHAT.showAgentChat() ? "On" : "Off")), b -> {
            try { CompanionMailbox.CHAT.showAgentChat(!CompanionMailbox.CHAT.showAgentChat()); notice = ""; }
            catch (Exception error) { notice = "Could not save chat preference: " + error.getMessage(); }
        }).dimensions(x + 128, parent.height - 35, width - 128, 20).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Show agent-to-agent messages in this client's normal Minecraft chat."))).build(); add.accept(mirror);
        rebuild(); nextRefresh = 0;
    }
    private void choose(CompanionsScreen parent, boolean from) {
        var choices = new ArrayList<OptionList.Option>(); choices.add(new OptionList.Option("", "Anyone", ""));
        String player = client.getSession().getUsername(); choices.add(new OptionList.Option(player, "You", player));
        var names = new LinkedHashSet<String>(); for (var value : api.agents()) names.add(ControllerClient.text(value.getAsJsonObject(), "name", ""));
        if (api.state.has("participants")) for (var value : api.state.getAsJsonArray("participants")) names.add(value.getAsString());
        names.remove(player); for (String name : names) choices.add(new OptionList.Option(name, name, ""));
        client.setScreen(new ChoiceScreen(parent, from ? "Message sender" : "Message recipient", choices, from ? sender : recipient, key -> { if (from) sender = key; else recipient = key; before = 0; scroll = 0; nextRefresh = 0; }));
    }
    String draft() { return composer == null ? "" : composer.getText(); }
    void sent() { composer.setText(""); before = 0; scroll = 0; nextRefresh = 0; }
    boolean focused() { return composer != null && composer.isFocused(); }
    String notice() { return notice; }
    void tick() {
        if (older == null) return;
        mirror.setMessage(Text.literal("Game chat: " + (CompanionMailbox.CHAT.showAgentChat() ? "On" : "Off")));
        older.active = hasOlder && !fetching; latest.active = before != 0;
        if (System.currentTimeMillis() < nextRefresh) return;
        String query = "/v1/messages?limit=100&before=" + before;
        if (selected != null) query += "&participants=" + encode(client.getSession().getUsername() + "," + selected);
        else { if (agentsOnly) query += "&agentsOnly=true"; if (!sender.isEmpty()) query += "&sender=" + encode(sender); if (!recipient.isEmpty()) query += "&recipient=" + encode(recipient); }
        if (fetching && requested.equals(query)) return;
        fetching = true; requested = query; final String ticket = query; nextRefresh = before == 0 ? System.currentTimeMillis() + 2000 : Long.MAX_VALUE;
        api.request("GET", query, null).whenComplete((result, error) -> client.execute(() -> {
            if (!requested.equals(ticket)) return;
            fetching = false;
            if (error != null) notice = ControllerClient.message(error);
            else { notice = ""; messages = result.getAsJsonArray("messages"); hasOlder = result.get("hasOlder").getAsBoolean(); rebuild(); }
        }));
    }
    private String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private void rebuild() {
        lines.clear(); if (width <= 20) return;
        for (var value : messages) {
            JsonObject m = value.getAsJsonObject();
            String time = java.time.Instant.ofEpochMilli(ControllerClient.count(m, "createdAt")).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0).toString();
            String header = time + "  " + ControllerClient.text(m, "sender", "?") + " → " + ControllerClient.text(m, "recipient", "?");
            if (client.getSession().getUsername().equalsIgnoreCase(ControllerClient.text(m, "sender", ""))) header += m.get("readByRecipient").getAsBoolean() ? " [read]" : " [delivered]";
            lines.addAll(client.textRenderer.wrapLines(Text.literal(header).withColor(0x8EA8C8), width - 16));
            lines.addAll(client.textRenderer.wrapLines(Text.literal(ControllerClient.text(m, "text", "")), width - 16)); lines.add(Text.literal(" ").asOrderedText());
        }
    }
    void scroll(double amount) { scroll = Math.max(0, scroll + (int) (amount * 3)); }
    boolean contains(double mx, double my) { return mx >= x && mx < x + width && my >= top && my < bottom; }
    void render(DrawContext c) {
        c.fill(x, top - 2, x + width, bottom, 0xFF141F30);
        int visible = Math.max(1, (bottom - top - 4) / 11); scroll = Math.min(scroll, Math.max(0, lines.size() - visible));
        int start = Math.max(0, lines.size() - visible - scroll);
        c.enableScissor(x, top, x + width, bottom);
        for (int i = 0; i < visible && start + i < lines.size(); i++) c.drawTextWithShadow(client.textRenderer, lines.get(start + i), x + 8, top + i * 11, 0xFFE8F0FF);
        if (lines.isEmpty()) c.drawTextWithShadow(client.textRenderer, fetching ? "Loading…" : "No messages yet", x + 8, top + 8, 0xFF8EA8C8);
        c.disableScissor();
    }
}
