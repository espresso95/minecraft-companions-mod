package local.companions;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.Consumer;

final class ProfileForm {
    private final ControllerClient api = CompanionMailbox.API;
    private final MinecraftClient client = MinecraftClient.getInstance();
    private final Screen parent;
    private final JsonObject draft;
    private final TextFieldWidget name, skin;
    ProfileForm(Screen parent, JsonObject value, boolean creating, int x, int y, int w, Consumer<ClickableWidget> add) {
        this.parent = parent; this.draft = value.deepCopy();
        int top = y;
        if (creating) { name = new TextFieldWidget(client.textRenderer, x, top, w, 20, Text.literal("Name")); name.setMaxLength(16); name.setPlaceholder(Text.literal("Unique Minecraft name")); name.setText(ControllerClient.text(draft, "name", "")); add.accept(name); top += 26; }
        else name = null;
        final String model = ControllerClient.text(draft, "model", ""), effort = ControllerClient.text(draft, "thinking", "");
        add.accept(ButtonWidget.builder(Text.literal(model), b -> chooseModel()).dimensions(x, top, Math.max(70, w - 82), 20).build());
        add.accept(ButtonWidget.builder(Text.literal(effort), b -> chooseEffort()).dimensions(x + w - 78, top, 78, 20).build());
        skin = new TextFieldWidget(client.textRenderer, x, top + 26, w, 20, Text.literal("Skin account"));
        skin.setMaxLength(16); skin.setText(ControllerClient.text(draft, "skin", "")); skin.setPlaceholder(Text.literal("Skin: Minecraft username (optional)")); add.accept(skin);
        add.accept(ButtonWidget.builder(Text.literal("Personality…"), b -> edit("personality", "Personality")).dimensions(x, top + 52, w, 20).build());
        add.accept(ButtonWidget.builder(Text.literal("Task…"), b -> edit("objective", "Task")).dimensions(x, top + 78, w, 20).build());

    }
    static JsonObject fields(JsonObject input) {
        JsonObject value = new JsonObject();
        for (String key : List.of("name", "model", "thinking", "personality", "objective", "skin")) value.addProperty(key, ControllerClient.text(input, key, ""));
        return value;
    }
    JsonObject value() {
        JsonObject result = fields(draft);
        result.addProperty("name", name == null ? ControllerClient.text(draft, "name", "") : name.getText());
        result.addProperty("skin", skin.getText()); return result;
    }
    boolean valid() { return !value().get("name").getAsString().isBlank() && api.model(ControllerClient.text(draft, "model", "")).size() > 0; }
    private void chooseModel() {
        var options = new ArrayList<OptionList.Option>();
        for (var item : api.models()) { JsonObject m = item.getAsJsonObject(); options.add(new OptionList.Option(ControllerClient.text(m, "id", ""), ControllerClient.text(m, "name", ""), ControllerClient.text(m, "id", ""))); }
        client.setScreen(new ChoiceScreen(parent, "Model", options, ControllerClient.text(draft, "model", ""), key -> {
            draft.addProperty("model", key); var efforts = api.model(key).getAsJsonArray("efforts");
            String current = ControllerClient.text(draft, "thinking", "");
            if (java.util.stream.StreamSupport.stream(efforts.spliterator(), false).noneMatch(v -> v.getAsString().equals(current))) draft.addProperty("thinking", efforts.get(0).getAsString());
        }));
    }
    private void chooseEffort() {
        JsonObject model = api.model(ControllerClient.text(draft, "model", ""));
        if (!model.has("efforts")) return;
        var options = new ArrayList<OptionList.Option>(); for (var e : model.getAsJsonArray("efforts")) options.add(new OptionList.Option(e.getAsString(), e.getAsString(), ""));
        client.setScreen(new ChoiceScreen(parent, "Thinking effort", options, ControllerClient.text(draft, "thinking", ""), key -> draft.addProperty("thinking", key)));
    }
    private void edit(String key, String label) { client.setScreen(new TextEditorScreen(parent, label, ControllerClient.text(draft, key, ""), value -> draft.addProperty(key, value))); }
}
