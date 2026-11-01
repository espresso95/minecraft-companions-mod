package local.companions;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

abstract class CompanionScreen extends Screen {
    protected final ControllerClient api = CompanionMailbox.API;
    protected boolean busy;
    protected String status = "";
    CompanionScreen(String title) { super(Text.literal(title)); }
    protected ButtonWidget button(String label, int x, int y, int w, Runnable action) {
        return addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> action.run()).dimensions(x, y, w, 20).build());
    }
    protected JsonObject body() { JsonObject value = new JsonObject(); value.addProperty("player", client.getSession().getUsername()); return value; }
    protected void submit(String method, String path, JsonObject body, Runnable done) {
        if (busy) return;
        busy = true; status = "Working…";
        api.request(method, path, body).thenCompose(result -> api.fetchState()).whenComplete((result, error) -> client.execute(() -> {
            busy = false;
            if (error != null) status = ControllerClient.message(error);
            else { status = "Saved"; done.run(); }
        }));
    }
    protected void control(String name, String action, Runnable done) {
        JsonObject value = body(); value.addProperty("action", action); JsonArray names = new JsonArray(); names.add(name); value.add("agents", names);
        submit("POST", "/v1/control", value, done);
    }
    protected void text(DrawContext c, String value, int x, int y, int color, int max) { c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(value, Math.max(1, max)), x, y, color | 0xFF000000); }
    protected void background(DrawContext c) { c.fill(0, 0, width, height, 0xFA0D1420); }
    protected void footer(DrawContext c) { text(c, api.error != null ? api.error : status, 14, height - 12, 0x8EA8C8, width - 28); }
    @Override public boolean shouldPause() { return false; }
}
