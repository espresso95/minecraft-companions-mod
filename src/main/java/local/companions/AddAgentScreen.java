package local.companions;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;

final class AddAgentScreen extends CompanionScreen {
    private final CompanionsScreen parent;
    private ProfileForm form;
    private ButtonWidget add;
    AddAgentScreen(CompanionsScreen parent) { super("Add agent"); this.parent = parent; }
    @Override protected void init() {
        JsonObject draft = form == null ? ControllerClient.object(api.catalog, "defaults") : form.value();
        int w = Math.min(500, width - 40), x = (width - w) / 2;
        form = new ProfileForm(this, draft, true, x, 43, w, this::addDrawableChild);
        add = button("Add agent", x, height - 33, 120, () -> { JsonObject value = body(); value.add("agent", form.value()); submit("POST", "/v1/agents", value, () -> { parent.select(form.value().get("name").getAsString()); close(); }); });
        button("Back", x + w - 70, height - 33, 70, this::close);
    }
    @Override public void tick() { add.active = !busy && form.valid(); }
    @Override public void render(DrawContext c, int x, int y, float delta) { background(c); text(c, "Add agent · starts offline", 20, 20, 0xE8F0FF, width - 40); footer(c); super.render(c, x, y, delta); }
    @Override public void close() { client.setScreen(parent); }
}
