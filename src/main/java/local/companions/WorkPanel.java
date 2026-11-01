package local.companions;

import com.google.gson.*;
import net.minecraft.client.gui.Element;
import java.util.*;
import java.util.function.Function;

final class WorkPanel {
    private final ControllerClient api = CompanionMailbox.API;
    private OptionList list;
    private String signature = "", selected;
    void mount(CompanionsScreen screen, String agent, int x, int w, Function<OptionList, Element> add) {
        list = new OptionList(net.minecraft.client.MinecraftClient.getInstance(), x, 92, w, screen.height - 112, key -> {
            JsonObject work = api.work(agent);
            if (key.startsWith("task:")) for (var value : array(work, "items")) {
                JsonObject item = value.getAsJsonObject();
                if (key.equals("task:" + ControllerClient.count(item, "id"))) {
                    selected = key;
                    net.minecraft.client.MinecraftClient.getInstance().setScreen(new WorkDetailsScreen(screen, ControllerClient.count(item, "id")));
                    break;
                }
            }
        });
        add.apply(list); signature = ""; update(agent);
    }
    void update(String agent) {
        JsonObject work = api.work(agent);
        String next = work.toString();
        if (next.equals(signature)) return;
        signature = next;
        var options = new ArrayList<OptionList.Option>();
        for (var value : array(work, "summary")) {
            JsonObject field = value.getAsJsonObject();
            options.add(new OptionList.Option("summary:" + options.size(), ControllerClient.text(field, "label", ""), ControllerClient.text(field, "value", "")));
        }
        for (var value : array(work, "items")) {
            JsonObject item = value.getAsJsonObject();
            options.add(new OptionList.Option("task:" + ControllerClient.count(item, "id"), ControllerClient.text(item, "title", ""), ControllerClient.text(item, "detail", "")));
        }
        if (options.isEmpty()) options.add(new OptionList.Option("", "No shared work", "Agents record projects and choose ready tasks."));
        list.options(options, selected);
    }
    static JsonArray array(JsonObject value, String key) { return value.has(key) && value.get(key).isJsonArray() ? value.getAsJsonArray(key) : new JsonArray(); }
}
