package local.companions;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;

final class CompanionsScreen extends CompanionScreen {
    private String selected, tab = "Chat", rosterSignature = "";
    private final ChatPanel chat = new ChatPanel();
    private final WorkPanel work = new WorkPanel();
    private OptionList roster;
    private ProfileForm form;
    private JsonObject draft, pendingMessage, baseline;
    private ButtonWidget primary, more, add, save;
    private int side, contentX, contentW;
    private boolean lifetime;
    CompanionsScreen() { super("Companions"); }
    void select(String name) { if (Objects.equals(selected, name)) return; selected = name; tab = "Chat"; draft = null; baseline = null; form = null; pendingMessage = null; chat.select(name); }
    private JsonObject agent() { return api.agent(selected); }
    private List<String> actions() { return api.actions(selected); }
    @Override protected void init() {
        if (form != null) { draft = form.value().equals(baseline) ? null : form.value(); form = null; }
        double previousScroll = roster == null ? 0 : roster.getScrollY();
        side = Math.min(172, Math.max(106, width / 4)); contentX = side + 24; contentW = width - contentX - 14;
        roster = addDrawableChild(new OptionList(client, 10, 39, side, height - 81, name -> { select(name.isEmpty() ? null : name); clearAndInit(); }));
        updateRoster(); roster.setScrollY(previousScroll);
        add = button("Add agent", 10, height - 33, side, () -> client.setScreen(new AddAgentScreen(this)));
        button("Connection", width - 194, 10, 82, () -> client.setScreen(new PairingScreen()));
        button("Host settings", width - 108, 10, 94, () -> client.setScreen(new HostSettingsScreen(this)));
        primary = button("Start", width - 132, 35, 76, this::primaryAction);
        more = button("More", width - 52, 35, 38, () -> { if (selected != null) client.setScreen(new AgentMenuScreen(this, selected)); });
        var tabs = List.of("Chat", "Work", "Settings", "Usage");
        for (int i = 0; i < tabs.size(); i++) {
            String label = tabs.get(i);
            ButtonWidget b = button(label, contentX + i * (contentW / tabs.size()), 65, contentW / tabs.size() - 3, () -> { tab = label; clearAndInit(); });
            b.active = !(selected == null && label.equals("Settings"));
        }
        save = null;
        if (tab.equals("Chat")) chat.mount(this, contentX, contentW, this::addDrawableChild, this::send);
        else if (tab.equals("Work")) work.mount(this, selected, contentX, contentW, this::addDrawableChild);
        else if (tab.equals("Settings") && selected != null) {
            form = new ProfileForm(this, draft == null ? agent() : draft, false, contentX, 92, contentW, this::addDrawableChild);
            if (draft == null) baseline = form.value();
            save = button("Save settings", contentX, height - 33, 120, () -> { JsonObject value = body(), profile = form.value(); profile.remove("name"); value.add("agent", profile); submit("PATCH", "/v1/agents/" + selected, value, () -> { status = "Settings saved"; draft = null; baseline = ProfileForm.fields(agent()); }); });
        } else if (tab.equals("Usage")) button(lifetime ? "All sessions" : "This session", contentX, 92, 130, () -> { lifetime = !lifetime; clearAndInit(); });
        api.refresh();
    }
    private List<OptionList.Option> rosterOptions() {
        var choices = new ArrayList<OptionList.Option>(); choices.add(new OptionList.Option("", "Team", api.agents().size() + " companions"));
        for (var value : api.agents()) { JsonObject a = value.getAsJsonObject(); choices.add(new OptionList.Option(ControllerClient.text(a, "name", ""), ControllerClient.text(a, "name", ""), ControllerClient.statusLabel(a))); }
        return choices;
    }
    private void updateRoster() {
        var choices = rosterOptions();
        roster.options(choices, selected == null ? "" : selected); rosterSignature = choices.toString();
    }
    private void primaryAction() {
        if (selected != null) control(selected, actions().contains("pause") ? "pause" : "resume", () -> {});
        else {
            JsonArray names = new JsonArray(); for (var item : api.agents()) { JsonObject a = item.getAsJsonObject(); if (a.has("controls") && java.util.stream.StreamSupport.stream(a.getAsJsonArray("controls").spliterator(), false).anyMatch(v -> v.getAsString().equals("pause"))) names.add(a.get("name")); }
            if (names.isEmpty()) return; JsonObject value = body(); value.add("agents", names); value.addProperty("action", "pause"); submit("POST", "/v1/control", value, () -> {});
        }
    }
    private void send() {
        if (busy || chat.draft().isBlank() || api.agents().isEmpty()) return;
        JsonArray recipients = new JsonArray(); if (selected != null) recipients.add(selected); else for (var a : api.agents()) recipients.add(a.getAsJsonObject().get("name"));
        if (pendingMessage == null || !ControllerClient.text(pendingMessage, "text", "").equals(chat.draft()) || !pendingMessage.getAsJsonArray("recipients").equals(recipients)) {
            pendingMessage = body(); pendingMessage.add("recipients", recipients); pendingMessage.addProperty("text", chat.draft()); pendingMessage.addProperty("submissionId", UUID.randomUUID().toString());
        }
        submit("POST", "/v1/messages", pendingMessage, () -> { pendingMessage = null; chat.sent(); status = "Message delivered"; });
    }
    @Override public void tick() {
        if (selected != null && agent().size() == 0) { select(null); clearAndInit(); return; }
        if (!rosterOptions().toString().equals(rosterSignature)) updateRoster();
        if (tab.equals("Settings") && form != null && !busy && form.value().equals(baseline) && !ProfileForm.fields(agent()).equals(baseline)) { draft = null; form = null; clearAndInit(); return; }
        boolean connected = api.error == null;
        add.active = connected && api.models().size() > 0 && !busy;
        more.active = connected && selected != null && !busy;
        primary.setMessage(Text.literal(selected == null ? "Pause all" : actions().contains("pause") ? "Pause" : agent().has("state") && ControllerClient.text(agent(), "state", "").equals("paused") ? "Resume" : "Start"));
        primary.active = connected && !busy && (selected == null ? java.util.stream.StreamSupport.stream(api.agents().spliterator(), false).anyMatch(a -> api.actions(ControllerClient.text(a.getAsJsonObject(), "name", "")).contains("pause")) : actions().contains("pause") || actions().contains("resume"));
        if (save != null) save.active = connected && !busy && form.valid();
        if (tab.equals("Chat")) chat.tick();
        if (tab.equals("Work")) work.update(selected);
    }
    @Override public void render(DrawContext c, int x, int y, float delta) {
        background(c); text(c, "COMPANIONS", 12, 15, 0xE8F0FF, width - 140);
        String state = selected == null ? "Team" : selected + " · " + ControllerClient.statusLabel(agent());
        text(c, state, contentX, 37, 0xE8F0FF, contentW - 125);
        JsonObject usage = selected == null ? ControllerClient.object(api.state, lifetime ? "lifetimeUsage" : "usage") : ControllerClient.object(agent(), lifetime ? "lifetimeUsage" : "usage");
        text(c, ControllerClient.compact(ControllerClient.count(usage, "total")) + " reported tokens · " + ControllerClient.count(usage, "pending") + " pending · " + ControllerClient.count(usage, "unknown") + " unknown", contentX, 55, 0x8EA8C8, contentW);
        if (tab.equals("Chat")) chat.render(c);
        if (tab.equals("Usage")) {
            String[][] fields = {{"Input", "input"}, {"Cached (part of input)", "cached"}, {"Output", "output"}, {"Reasoning (part of output)", "reasoning"}, {"Total: input + output", "total"}, {"Reported responses", "reported"}, {"Pending requests", "pending"}, {"Unknown requests", "unknown"}, {"Reported in last minute", "tokensLastMinute"}};
            int step = Math.min(19, Math.max(9, (height - 157) / fields.length));
            for (int i = 0; i < fields.length; i++) { int lineY = 122 + i * step; text(c, fields[i][0], contentX, lineY, i == 4 ? 0x77DDB0 : 0xA9C0DA, contentW - 68); String number = String.format(java.util.Locale.ROOT, "%,d", ControllerClient.count(usage, fields[i][1])); text(c, number, width - 14 - textRenderer.getWidth(number), lineY, 0xE8F0FF, 80); }
            text(c, "Subscription deductions are not reported.", contentX, height - 30, 0x8EA8C8, contentW);
        }
        if (status.isEmpty() && tab.equals("Chat")) status = chat.notice();
        footer(c); super.render(c, x, y, delta);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) { if (tab.equals("Chat") && chat.contains(x, y)) { chat.scroll(vertical); return true; } return super.mouseScrolled(x, y, horizontal, vertical); }
    @Override public boolean keyPressed(int key, int scan, int modifiers) { if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && tab.equals("Chat") && chat.focused()) { send(); return true; } return super.keyPressed(key, scan, modifiers); }
}
