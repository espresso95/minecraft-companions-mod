package local.companions;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import java.util.function.Consumer;

final class TextEditorScreen extends CompanionScreen {
    private final Screen parent;
    private final Consumer<String> save;
    private String draft;
    private EditBoxWidget editor;
    TextEditorScreen(Screen parent, String label, String value, Consumer<String> save) { super(label); this.parent = parent; draft = value; this.save = save; }
    @Override protected void init() {
        if (editor != null) draft = editor.getText();
        editor = addDrawableChild(EditBoxWidget.builder().x(20).y(43).build(textRenderer, width - 40, height - 90, title));
        editor.setMaxLength(4000); editor.setText(draft);
        button("Done", 20, height - 33, 80, () -> { save.accept(editor.getText()); close(); });
        button("Cancel", width - 100, height - 33, 80, this::close);
    }
    @Override public void render(DrawContext c, int x, int y, float delta) { background(c); text(c, title.getString(), 20, 20, 0xE8F0FF, width - 40); super.render(c, x, y, delta); }
    @Override public void close() { client.setScreen(parent); }
}
