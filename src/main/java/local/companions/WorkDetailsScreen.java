package local.companions;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ScrollableTextWidget;
import net.minecraft.text.Text;

final class WorkDetailsScreen extends CompanionScreen {
    private final CompanionsScreen parent;
    private final long id;
    private ScrollableTextWidget content;
    private String signature = "";
    WorkDetailsScreen(CompanionsScreen parent, long id) { super("Work #" + id); this.parent = parent; this.id = id; }
    @Override protected void init() {
        content = addDrawableChild(new ScrollableTextWidget(18, 40, width - 36, height - 85, Text.empty(), textRenderer).textColor(0xE8F0FF));
        button("Back", 18, height - 33, 80, this::close);
        signature = ""; tick();
    }
    @Override public void tick() {
        for (var value : WorkPanel.array(api.work(null), "items")) {
            JsonObject item = value.getAsJsonObject();
            if (ControllerClient.count(item, "id") != id) continue;
            String next = item.toString();
            if (next.equals(signature)) return;
            signature = next;
            StringBuilder text = new StringBuilder(ControllerClient.text(item, "title", ""));
            for (var field : WorkPanel.array(item, "fields")) {
                JsonObject entry = field.getAsJsonObject();
                text.append("\n\n").append(ControllerClient.text(entry, "label", "")).append(": ").append(ControllerClient.text(entry, "value", ""));
            }
            content.setMessage(Text.literal(text.toString()));
            return;
        }
    }
    @Override public void render(DrawContext c, int x, int y, float delta) { background(c); text(c, title.getString(), 18, 18, 0xE8F0FF, width - 36); footer(c); super.render(c, x, y, delta); }
    @Override public void close() { client.setScreen(parent); }
}
