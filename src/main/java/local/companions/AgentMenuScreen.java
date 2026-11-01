package local.companions;

import net.minecraft.client.gui.DrawContext;

final class AgentMenuScreen extends CompanionScreen {
    private final CompanionsScreen parent;
    private final String agent;
    private net.minecraft.client.gui.widget.ButtonWidget stop;
    AgentMenuScreen(CompanionsScreen parent, String agent) { super(agent); this.parent = parent; this.agent = agent; }
    @Override protected void init() {
        int x = (width - 230) / 2, y = Math.max(65, height / 2 - 36);
        stop = button("Stop agent", x, y, 230, () -> control(agent, "stop", this::close));
        button("Remove agent", x, y + 26, 230, () -> submit("DELETE", "/v1/agents/" + agent, body(), () -> { parent.select(null); close(); }));
        button("Back", x, y + 52, 230, this::close);
    }
    @Override public void tick() { stop.active = !busy && api.actions(agent).contains("stop"); }
    @Override public void render(DrawContext c, int x, int y, float delta) { background(c); text(c, agent, 20, 20, 0xE8F0FF, width - 40); text(c, "Removing preserves memory, messages and usage history.", 20, 40, 0x8EA8C8, width - 40); footer(c); super.render(c, x, y, delta); }
    @Override public void close() { client.setScreen(parent); }
}
