package local.companions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

final class OptionList extends AlwaysSelectedEntryListWidget<OptionList.Row> {
    record Option(String key, String label, String detail) {}
    private final Consumer<String> choose;
    OptionList(MinecraftClient client, int x, int y, int w, int h, Consumer<String> choose) {
        super(client, w, h, y, 29); setX(x); this.choose = choose;
    }
    void options(List<Option> values, String selected) {
        double scroll = getScrollY(); clearEntries();
        for (Option option : values) { Row row = new Row(option); addEntry(row); if (Objects.equals(selected, option.key())) setSelected(row); }
        setScrollY(scroll);
    }
    @Override public int getRowWidth() { return getWidth() - 12; }
    @Override protected int getScrollbarX() { return getRight() - 6; }
    @Override protected void drawMenuListBackground(DrawContext context) {}
    @Override protected void drawHeaderAndFooterSeparators(DrawContext context) {}
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) && getSelectedOrNull() != null) { choose.accept(getSelectedOrNull().option.key()); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    final class Row extends AlwaysSelectedEntryListWidget.Entry<Row> {
        private final Option option;
        Row(Option option) { this.option = option; }
        @Override public Text getNarration() { return Text.literal(option.label() + " " + option.detail()); }
        @Override public boolean mouseClicked(double x, double y, int button) {
            if (button != 0) return false; setSelected(this); choose.accept(option.key()); return true;
        }
        @Override public void render(DrawContext c, int index, int y, int x, int w, int h, int mouseX, int mouseY, boolean hovered, float delta) {
            c.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(option.label(), w - 10), x + 5, y + 3, 0xFFE8F0FF);
            c.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(option.detail(), w - 10), x + 5, y + 15, 0xFF8EA8C8);
        }
    }
}
