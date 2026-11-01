package local.companions;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Runs against an isolated server and a fixture with no model workers. */
final class ChatGameTest {
    private String token, control;
    private final HttpClient http = HttpClient.newHttpClient();
    private JsonObject fixture(JsonObject body) {
        try {
            var request = HttpRequest.newBuilder(URI.create(control)).header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(15)).POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new AssertionError(response.body());
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private JsonObject status() { var body = new JsonObject(); body.addProperty("operation", "status"); return fixture(body); }
    private long send(String from, String to, String text, int count) {
        var body = new JsonObject(); body.addProperty("operation", "send"); body.addProperty("sender", from); body.addProperty("recipient", to); body.addProperty("text", text); body.addProperty("count", count); return fixture(body).get("lastId").getAsLong();
    }
    @SuppressWarnings("unchecked")
    private List<String> lines(MinecraftClient client) {
        try {
            // Test-only access to the pinned Minecraft version's rendered chat history.
            var field = ChatHud.class.getDeclaredField("messages"); field.setAccessible(true);
            return ((List<ChatHudLine>)field.get(client.inGameHud.getChatHud())).stream().map(line -> line.content().getString()).toList();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    private void shown(ClientGameTestContext ctx, String marker) { ctx.waitFor(c -> lines(c).stream().anyMatch(line -> line.contains(marker)), 600); }
    private void once(ClientGameTestContext ctx, String marker) { if (ctx.computeOnClient(c -> lines(c).stream().filter(line -> line.contains(marker)).count()) != 1) throw new AssertionError("Missing or repeated chat: " + marker); }
    private void clickFilter(ClientGameTestContext ctx) {
        ctx.runOnClient(c -> { var box = c.currentScreen.children().stream().filter(CheckboxWidget.class::isInstance).map(CheckboxWidget.class::cast).findFirst().orElseThrow(); c.currentScreen.mouseClicked(box.getX()+5, box.getY()+5, 0); }); ctx.waitTicks(4);
    }
    void run(ClientGameTestContext ctx) {
        try {
            var file = Path.of(Objects.requireNonNull(System.getenv("COMPANION_CONNECTION_FILE")));
            var connection = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            token = connection.get("token").getAsString(); control = System.getenv("COMPANION_CHAT_CONTROL");
            var config = FabricLoader.getInstance().getConfigDir().resolve("companion-mailbox.json"); Files.createDirectories(config.getParent()); Files.copy(file, config, StandardCopyOption.REPLACE_EXISTING);
            ctx.runOnClient(c -> c.options.getGuiScale().setValue(2)); ctx.getInput().resizeWindow(1280, 720);
            connect(ctx, connection.get("minecraftPort").getAsInt());
            ctx.waitFor(c -> CompanionMailbox.API.agents().size()==3 && CompanionMailbox.API.error==null, 600);
            ctx.waitFor(c -> status().get("baselines").getAsInt()>0, 600);
            ctx.waitTicks(45);
            if (ctx.computeOnClient(c -> lines(c).stream().anyMatch(line -> line.contains("OLD_ONLY")))) throw new AssertionError("Replayed old history");
            send("Cedar", "Flint", "LIVE_ONE", 1); shown(ctx,"LIVE_ONE");
            send("Flint", "Cedar", "LIVE_TWO", 1); shown(ctx,"LIVE_TWO");
            long human=send("HumanTester", "Cedar", "HUMAN_ONLY", 1);
            ctx.waitFor(c -> status().get("lastAfter").getAsLong()>=human,600);
            if(ctx.computeOnClient(c -> lines(c).stream().anyMatch(line -> line.contains("HUMAN_ONLY")))) throw new AssertionError("Mirrored a human DM");
            once(ctx,"LIVE_ONE"); once(ctx,"LIVE_TWO");
            ctx.setScreen(CompanionsScreen::new); ctx.waitTicks(5);
            ctx.clickScreenButton("Game chat: On"); ctx.waitTicks(3);
            if(new ChatPreferences().showAgentChat()) throw new AssertionError("Toggle was not persisted");
            long hidden=send("Rune","Cedar","HIDDEN_OFF",1);
            ctx.waitFor(c -> status().get("lastAfter").getAsLong()>=hidden,600);
            ctx.clickScreenButton("Game chat: Off"); ctx.waitTicks(3);
            send("Cedar","Rune","AFTER_ON",1); shown(ctx,"AFTER_ON");
            ctx.waitTicks(50);
            if(ctx.computeOnClient(c -> lines(c).stream().anyMatch(line -> line.contains("HIDDEN_OFF")))) throw new AssertionError("Off messages replayed");
            clickFilter(ctx); ctx.waitFor(c -> status().get("agentsOnly").getAsBoolean(),600);
            ctx.takeScreenshot("chat-mirror-filter");
            clickFilter(ctx); ctx.waitFor(c -> !status().get("agentsOnly").getAsBoolean(),600);
            ctx.runOnClient(c -> c.setScreen(null));
            send("Cedar","Flint","BURST_",65); shown(ctx,"BURST_64");
            for(int i=0;i<65;i++) once(ctx,"BURST_"+i+" ");
            // A real public message proves the server-side witness is listening.
            ctx.runOnClient(c -> c.getNetworkHandler().sendChatMessage("PUBLIC_CONTROL"));
            ctx.waitFor(c -> status().get("publicControlSeen").getAsBoolean(),600);
            if(status().get("mirrorEchoes").getAsInt()!=0) throw new AssertionError("Mirror sent public chat packets");
            ctx.getInput().resizeWindow(854,480);ctx.waitTicks(5);ctx.takeScreenshot("chat-mirror-hud-small");
            int baselines=status().get("baselines").getAsInt();
            ctx.runOnClient(c -> c.disconnect(new TitleScreen(),false));ctx.waitTicks(5);
            send("Cedar","Flint","WHILE_AWAY",1);
            connect(ctx,connection.get("minecraftPort").getAsInt());
            ctx.waitFor(c -> status().get("baselines").getAsInt()>baselines,600);ctx.waitTicks(45);
            if(ctx.computeOnClient(c -> lines(c).stream().anyMatch(line -> line.contains("WHILE_AWAY")))) throw new AssertionError("Reconnect replayed history");
            send("Rune","Flint","AFTER_RECONNECT",1);shown(ctx,"AFTER_RECONNECT");
            var restart=new JsonObject();restart.addProperty("operation","restart");fixture(restart);
            final int prior=status().get("baselines").getAsInt();
            ctx.waitFor(c -> status().get("baselines").getAsInt()>prior,600);
            send("Flint","Rune","AFTER_RESTART",1);shown(ctx,"AFTER_RESTART");
            var proof=status();if(!proof.get("receiptsUntouched").getAsBoolean()) throw new AssertionError("Viewer consumed unread receipts");
            if(proof.get("modelRequests").getAsLong()!=0) throw new AssertionError("Display started model work");
            ctx.setScreen(CompanionsScreen::new);ctx.waitTicks(4);clickFilter(ctx);ctx.takeScreenshot("chat-mirror-filter-small");
            System.out.println("CHAT_MIRROR_VERIFIED cursor batching, both directions, local-only HUD, persisted toggle, no replay, filters, reconnect, controller restart, receipts, zero model requests");
        }catch(Exception e){throw new AssertionError(e);}finally{ctx.runOnClient(c -> c.disconnect(new TitleScreen(),false));}
    }
    private void connect(ClientGameTestContext ctx,int port) {
        String address="127.0.0.1:"+port;
        ctx.runOnClient(c -> ConnectScreen.connect(new TitleScreen(),c,ServerAddress.parse(address),new ServerInfo("Chat verification",address,ServerInfo.ServerType.OTHER),false,null));
        ctx.waitFor(c -> c.world!=null && c.player!=null,1200);
    }
}
