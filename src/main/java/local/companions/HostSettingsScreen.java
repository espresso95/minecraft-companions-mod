package local.companions;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import java.util.*;

final class HostSettingsScreen extends CompanionScreen {
    private final Screen parent;
    private String difficulty;
    private Boolean permanent;
    private ButtonWidget difficultyButton, deathButton, save, lan;
    private boolean network;
    private String baselineDifficulty;
    private Boolean baselinePermanent;
    private boolean sharing() { JsonObject access = ControllerClient.object(api.state, "access"); return access.has("lanEnabled") && access.get("lanEnabled").getAsBoolean(); }
    HostSettingsScreen(Screen parent) { super("Host settings"); this.parent = parent; }
    private JsonObject server() { return ControllerClient.object(api.state, "server"); }
    @Override protected void init() {
        if (difficulty == null) difficulty = ControllerClient.text(server(), "difficulty", "");
        if (permanent == null) permanent = server().has("permanentDeath") && server().get("permanentDeath").getAsBoolean();
        if (baselineDifficulty == null) { baselineDifficulty = difficulty; baselinePermanent = permanent; }
        int w = Math.min(440, width - 40), x = (width - w) / 2;
        button("Rules", x, 43, w / 2 - 2, () -> { network = false; clearAndInit(); });
        button("Access", x + w / 2 + 2, 43, w / 2 - 2, () -> { network = true; clearAndInit(); });
        if (network) {
            lan = button("LAN sharing: " + (sharing() ? "On" : "Off"), x, 75, w, () -> { JsonObject value = body(), options = new JsonObject(); options.addProperty("lanEnabled", !sharing()); value.add("access", options); submit("PATCH", "/v1/access", value, this::clearAndInit); });
            button("Open web UI", x, height - 75, 120, () -> { try { net.minecraft.util.Util.getOperatingSystem().open(java.net.URI.create(api.webAddress())); } catch (Exception e) { status = e.getMessage(); } });
            button("Copy invite", x, height - 48, 100, () -> client.keyboard.setClipboard(invite()));
            button("Revoke devices", x + 104, height - 48, Math.min(130, w - 178), () -> { JsonObject value = body(), options = new JsonObject(); options.addProperty("rotateCode", true); value.add("access", options); submit("PATCH", "/v1/access", value, this::clearAndInit); });
        } else {
            difficultyButton = button("Difficulty: " + difficulty, x, 75, w, () -> {
                var choices = new ArrayList<OptionList.Option>(); if (server().has("difficulties")) for (var value : server().getAsJsonArray("difficulties")) choices.add(new OptionList.Option(value.getAsString(), value.getAsString(), "Minecraft server difficulty"));
                client.setScreen(new ChoiceScreen(this, "Server difficulty", choices, difficulty, key -> difficulty = key));
            });
            deathButton = button("Permanent death: " + (permanent ? "On" : "Off"), x, 107, w, () -> { permanent = !permanent; clearAndInit(); });
            save = button("Apply host settings", x, height - 48, 160, () -> { JsonObject values = new JsonObject(); values.addProperty("difficulty", difficulty); values.addProperty("permanentDeath", permanent); JsonObject value = body(); value.add("settings", values); submit("PATCH", "/v1/server", value, () -> { baselineDifficulty = difficulty; baselinePermanent = permanent; status = "Host settings applied"; }); });
        }
        button("Back", x + w - 70, height - 48, 70, this::close);
    }
    private String invite() {
        JsonObject access = ControllerClient.object(api.state, "access");
        String result = "Pairing code: " + ControllerClient.text(access, "pairingCode", "") + "\nWeb: " + api.webAddress();
        for (String field : List.of("lanUrls", "gameAddresses")) if (access.has(field)) for (var value : access.getAsJsonArray(field)) result += "\n" + value.getAsString();
        return result;
    }
    @Override public void tick() { if (network) { lan.setMessage(net.minecraft.text.Text.literal("LAN sharing: " + (sharing() ? "On" : "Off"))); lan.active = !busy; return; }
        if (!busy && difficulty.equals(baselineDifficulty) && permanent.equals(baselinePermanent) && server().has("difficulty") && server().has("permanentDeath")) {
            String next = server().get("difficulty").getAsString(); boolean death = server().get("permanentDeath").getAsBoolean();
            if (!difficulty.equals(next) || permanent != death) { difficulty = baselineDifficulty = next; permanent = baselinePermanent = death; clearAndInit(); return; }
        }
        boolean available = server().has("available") && server().get("available").getAsBoolean(), nativeHardcore = server().has("nativeHardcore") && server().get("nativeHardcore").getAsBoolean(); difficultyButton.active = deathButton.active = available && !nativeHardcore && !busy; save.active = available && !busy; }
    @Override public void render(DrawContext c, int x, int y, float delta) {
        background(c); int w = Math.min(440, width - 40), left = (width - w) / 2;
        text(c, "Host settings · applies to everyone", left, 20, 0xE8F0FF, w);
        if (network) {
            int lineY = 106;
            for (String line : invite().split("\n")) { if (lineY > height - 87) break; text(c, line, left, lineY, 0xA9C0DA, w); lineY += 12; }
        } else {
            int lineY = 139;
            for (var line : textRenderer.wrapLines(net.minecraft.text.Text.literal("Difficulty is Minecraft's server setting. Permanent death makes dead players spectators and retires companions. Disabling it does not revive recorded deaths."), w)) { c.drawTextWithShadow(textRenderer, line, left, lineY, 0xFFA9C0DA); lineY += 11; }
        }
        text(c, status, left, height - 20, 0x8EA8C8, w); super.render(c, x, y, delta);
    }
    @Override public void close() { client.setScreen(parent); }
}
