package local.companions;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import java.util.function.Consumer;

final class ChoiceScreen extends CompanionScreen {
    private final Screen parent;
    private final List<OptionList.Option> options;
    private final String selected;
    private final Consumer<String> choose;
    ChoiceScreen(Screen parent, String title, List<OptionList.Option> options, String selected, Consumer<String> choose) {
        super(title); this.parent = parent; this.options = options; this.selected = selected; this.choose = choose;
    }
    @Override protected void init() {
        OptionList list = addDrawableChild(new OptionList(client, 20, 44, width - 40, height - 90, key -> { choose.accept(key); close(); }));
        list.options(options, selected); button("Back", width - 90, height - 33, 70, this::close);
    }
    @Override public void render(DrawContext c, int x, int y, float delta) { background(c); text(c, title.getString(), 20, 20, 0xE8F0FF, width - 40); super.render(c, x, y, delta); }
    @Override public void close() { client.setScreen(parent); }
}
