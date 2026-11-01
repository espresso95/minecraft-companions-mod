package local.companions;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/** Exercises the real name renderer against an isolated world and simulated model states. */
final class StatusGameTest {
    void run(ClientGameTestContext ctx) {
        try {
            Path connectionFile=Path.of(Objects.requireNonNull(System.getenv("COMPANION_CONNECTION_FILE")));
            JsonObject connection=JsonParser.parseString(Files.readString(connectionFile)).getAsJsonObject();
            Path config=FabricLoader.getInstance().getConfigDir().resolve("companion-mailbox.json");
            Files.createDirectories(config.getParent());Files.copy(connectionFile,config,StandardCopyOption.REPLACE_EXISTING);
            String address="127.0.0.1:"+connection.get("minecraftPort").getAsInt();
            ctx.getInput().resizeWindow(1280,720);
            ctx.runOnClient(c->ConnectScreen.connect(new TitleScreen(),c,ServerAddress.parse(address),new ServerInfo("Status verification",address,ServerInfo.ServerType.OTHER),false,null));
            ctx.waitFor(c->c.world!=null&&c.player!=null&&c.world.getPlayers().stream().anyMatch(p->p.getName().getString().equals("Scout")),1200);
            var http=HttpClient.newHttpClient();
            for(String phase:List.of("thinking","working","waiting","standby","paused")) {
                JsonObject body=new JsonObject();body.addProperty("phase",phase);
                var request=HttpRequest.newBuilder(URI.create(System.getenv("STATUS_CONTROL_URL")))
                    .header("Authorization","Bearer "+connection.get("token").getAsString())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
                var response=http.send(request,HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new AssertionError(response.body());
                String label=JsonParser.parseString(response.body()).getAsJsonObject().get("label").getAsString();
                ctx.waitFor(c->CompanionMailbox.statusFor("scout").equals(label),600);
                ctx.runOnClient(c->{
                    var player=(AbstractClientPlayerEntity)c.world.getPlayers().stream().filter(p->p.getName().getString().equals("Scout")).findFirst().orElseThrow();
                    var renderer=(PlayerEntityRenderer)c.getEntityRenderDispatcher().getRenderer(player);
                    var state=renderer.createRenderState();renderer.updateRenderState(player,state,0);
                    if(state.displayName==null||!state.displayName.getString().equals("Scout ["+label+"]"))throw new AssertionError("Name tag did not use shared status: "+state.displayName);
                    renderer.updateRenderState(player,state,0);
                    if(!state.displayName.getString().equals("Scout ["+label+"]"))throw new AssertionError("Name decoration accumulated");
                    if(!CompanionMailbox.statusFor("LocalPlayer").isEmpty())throw new AssertionError("Decorated a human player");
                    var delta=player.getEyePos().subtract(c.player.getEyePos());
                    c.player.setYaw((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)));
                    c.player.setPitch((float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));
                });
                ctx.waitTicks(5);
                if(phase.equals("thinking"))ctx.takeScreenshot("agent-name-thinking");
                if(phase.equals("working")) {ctx.setScreen(CompanionsScreen::new);ctx.waitTicks(5);ctx.takeScreenshot("agent-status-panel");ctx.runOnClient(c->c.setScreen(null));}
            }
            System.out.println("AGENT_STATUS_VERIFIED real renderer, shared labels, no accumulated suffix, human names unchanged; simulated requests only");
        }catch(Exception e){throw new AssertionError(e);}finally{ctx.runOnClient(c->c.disconnect(new TitleScreen(),false));}
    }
}
