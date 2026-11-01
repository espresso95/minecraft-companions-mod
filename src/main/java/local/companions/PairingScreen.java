package local.companions;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;

final class PairingScreen extends CompanionScreen {
    private TextFieldWidget address, code;
    private ButtonWidget connect;
    PairingScreen() { super("Connect to companions"); }
    @Override protected void init() {
        int w = Math.min(420, width - 40), x = (width - w) / 2;
        String oldAddress = address == null ? "" : address.getText(), oldCode = code == null ? "" : code.getText();
        var server = LocalConnection.server(client);
        if (oldAddress.isEmpty() && server != null) oldAddress = "http://" + server.getAddress().getHostAddress() + ":8767";
        address = addDrawableChild(new TextFieldWidget(textRenderer, x, 65, w, 20, Text.literal("Control address"))); address.setMaxLength(200); address.setText(oldAddress);
        code = addDrawableChild(new TextFieldWidget(textRenderer, x, 105, w, 20, Text.literal("Pairing code"))); code.setMaxLength(16); code.setText(oldCode);
        connect = button("Pair this device", x, 145, 150, () -> {
            if (busy) return; busy = true; status = "Pairing…";
            api.pair(address.getText(), code.getText()).whenComplete((result, error) -> client.execute(() -> { busy = false; if (error != null) status = ControllerClient.message(error); else client.setScreen(new CompanionsScreen()); }));
        });
        button("Back", x + w - 70, 145, 70, this::close);
    }
    @Override public void tick() { connect.active = !busy && !address.getText().isBlank() && !code.getText().isBlank(); }
    @Override public void render(DrawContext c, int x, int y, float delta) {
        background(c); int left = (width - Math.min(420, width - 40)) / 2;
        text(c, "Connect to companions", left, 20, 0xE8F0FF, width - 40);
        text(c, "Host's control address", left, 50, 0xA9C0DA, width - 40); text(c, "Pairing code from the host", left, 91, 0xA9C0DA, width - 40);
        footer(c); super.render(c, x, y, delta);
    }
}
