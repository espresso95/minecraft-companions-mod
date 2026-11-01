package local.companions;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

public final class CompanionsGameTest implements FabricClientGameTest {
    private final ControllerClient api = CompanionMailbox.API;
    private String player;
    private ClientGameTestContext context;
    private net.minecraft.client.MinecraftClient game;
    private JsonObject request(String method, String path, JsonObject body) { return (game.isOnThread() ? api.request(method,path,body) : context.computeOnClient(c -> api.request(method,path,body))).join(); }
    private JsonObject body() { JsonObject b=new JsonObject(); b.addProperty("player",player); return b; }
    private JsonObject state() { return request("GET","/v1/state",null); }
    private JsonObject agent(String name) { for(var v:state().getAsJsonArray("agents")) if(name.equals(ControllerClient.text(v.getAsJsonObject(),"name","")))return v.getAsJsonObject(); return new JsonObject(); }
    private void waitFor(ClientGameTestContext ctx, BooleanSupplier condition) {
        long deadline=System.nanoTime()+90_000_000_000L;
        ctx.waitFor(c->{if(System.nanoTime()>deadline)throw new AssertionError("Companion UI timed out: "+api.error+" screen="+c.currentScreen.getTitle().getString());return condition.getAsBoolean();},ClientGameTestContext.NO_TIMEOUT);
    }
    private void idle(ClientGameTestContext ctx) { waitFor(ctx,()->net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof CompanionScreen s && !s.busy); ctx.runOnClient(c->{ if(c.currentScreen instanceof CompanionScreen s && !s.status.equals("Saved") && !s.status.endsWith("saved") && !s.status.equals("Message delivered") && !s.status.equals("Host settings applied") && !s.status.isEmpty()) throw new AssertionError(s.status); }); ctx.waitTicks(2); }
    private void choose(ClientGameTestContext ctx,String label) {
        ctx.runOnClient(c->{
            OptionList list=c.currentScreen.children().stream().filter(OptionList.class::isInstance).map(OptionList.class::cast).findFirst().orElseThrow();
            int index=-1; for(int i=0;i<list.children().size();i++)if(list.children().get(i).getNarration().getString().startsWith(label+" ")){index=i;break;}
            if(index<0)throw new AssertionError("Missing choice "+label);
            list.setScrollY(Math.max(0,index*29-29));
            double y=list.getY()+4+index*29-list.getScrollY()+10;
            if(y<list.getY()||y>=list.getBottom())throw new AssertionError("Choice outside viewport");
            if(!c.currentScreen.mouseClicked(list.getX()+14,y,0))throw new AssertionError("Choice click refused");
        }); ctx.waitTicks(3);
    }
    private void field(ClientGameTestContext ctx,int index,String value) {ctx.runOnClient(c->c.currentScreen.children().stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).toList().get(index).setText(value));ctx.waitTick();}
    private void button(ClientGameTestContext ctx,String label){ctx.waitTicks(2);ctx.clickScreenButton(label);ctx.waitTicks(2);}
    @Override public void runTest(ClientGameTestContext ctx) {
        if ("work".equals(System.getenv("COMPANION_TEST_SCOPE"))) { new WorkGameTest().run(ctx); return; }
        if ("status".equals(System.getenv("COMPANION_TEST_SCOPE"))) { new StatusGameTest().run(ctx); return; }
        if ("chat".equals(System.getenv("COMPANION_TEST_SCOPE"))) { new ChatGameTest().run(ctx); return; }
        context=ctx; game=ctx.computeOnClient(c -> c);
        try {
            Path connection=Path.of(Objects.requireNonNull(System.getenv("COMPANION_CONNECTION_FILE")));
            Path config=FabricLoader.getInstance().getConfigDir().resolve("companion-mailbox.json");Files.createDirectories(config.getParent());Files.copy(connection,config,StandardCopyOption.REPLACE_EXISTING);
            player=ctx.computeOnClient(c->c.getSession().getUsername());
            ctx.getInput().resizeWindow(1280,720);
            ctx.runOnClient(c->ConnectScreen.connect(new TitleScreen(),c,ServerAddress.parse("127.0.0.1:25565"),new ServerInfo("Test companions","127.0.0.1:25565",ServerInfo.ServerType.OTHER),false,null));
            ctx.waitFor(c->c.world!=null&&c.player!=null,1200);
            ctx.setScreen(CompanionsScreen::new);
            waitFor(ctx,()->api.error==null&&api.agents().size()==3&&api.models().size()>0);ctx.waitTicks(5);
            choose(ctx,"Scout"); button(ctx,"Settings");
            String identity=ctx.computeOnClient(c->c.getNetworkHandler().getPlayerListEntry("Scout").getProfile().getId().toString());
            field(ctx,0,"Notch");button(ctx,"Save settings");idle(ctx);
            waitFor(ctx,()->"Notch".equals(ControllerClient.text(agent("Scout"),"skin","")));
            String appearance=Files.readString(Path.of(System.getenv("COMPANION_APPEARANCES_FILE")));
            String expected=JsonParser.parseString(appearance).getAsJsonObject().getAsJsonObject("cache").getAsJsonObject("notch").get("value").getAsString();
            ctx.waitFor(c->{var entry=c.getNetworkHandler().getPlayerListEntry("Scout");return entry!=null&&entry.getProfile().getProperties().get("textures").stream().anyMatch(p->p.value().equals(expected));},600);
            if(!identity.equals(ctx.computeOnClient(c->c.getNetworkHandler().getPlayerListEntry("Scout").getProfile().getId().toString())))throw new AssertionError("Skin changed UUID");
            ctx.takeScreenshot("companions-settings");
            button(ctx,"xhigh");choose(ctx,"medium");button(ctx,"Personality…");
            ctx.runOnClient(c->c.currentScreen.children().stream().filter(EditBoxWidget.class::isInstance).map(EditBoxWidget.class::cast).findFirst().orElseThrow().setText("Careful builder who checks instructions."));button(ctx,"Done");button(ctx,"Save settings");idle(ctx);
            waitFor(ctx,()->"medium".equals(ControllerClient.text(agent("Scout"),"thinking",""))&&"paused".equals(ControllerClient.text(agent("Scout"),"state","")));
            button(ctx,"Usage");ctx.takeScreenshot("companions-usage");
            button(ctx,"Chat");field(ctx,0,"Native UI message");button(ctx,"Send");idle(ctx);
            button(ctx,"Add agent");field(ctx,0,"NewMate");button(ctx,"Add agent");idle(ctx);
            waitFor(ctx,()->api.agent("NewMate").size()>0);
            if(!"stopped".equals(ControllerClient.text(agent("NewMate"),"state","")))throw new AssertionError("Added agent started itself");
            button(ctx,"More");button(ctx,"Remove agent");waitFor(ctx,()->api.agent("NewMate").size()==0);idle(ctx);
            for(int i=0;i<7;i++){JsonObject b=body(),p=new JsonObject();p.addProperty("name","Extra"+i);b.add("agent",p);request("POST","/v1/agents",b);}ctx.computeOnClient(c -> api.fetchState()).join();ctx.waitTicks(4);
            choose(ctx,"Extra6");button(ctx,"Settings");ctx.getInput().resizeWindow(854,480);ctx.waitTicks(5);ctx.takeScreenshot("companions-settings-small");
            button(ctx,"More");button(ctx,"Remove agent");idle(ctx);
            for(int i=0;i<6;i++)request("DELETE","/v1/agents/Extra"+i,body());ctx.computeOnClient(c -> api.fetchState()).join();ctx.waitTicks(5);
            choose(ctx,"Team");button(ctx,"From: Anyone");choose(ctx,"Watcher");button(ctx,"To: Anyone");choose(ctx,"Builder");ctx.takeScreenshot("companions-team-small");
            button(ctx,"Host settings");button(ctx,"Difficulty: peaceful");choose(ctx,"normal");button(ctx,"Permanent death: Off");button(ctx,"Apply host settings");idle(ctx);
            if(!state().getAsJsonObject("server").get("permanentDeath").getAsBoolean())throw new AssertionError("Permanent death not applied");
            ctx.takeScreenshot("companions-host-small");button(ctx,"Permanent death: On");button(ctx,"Difficulty: normal");choose(ctx,"peaceful");button(ctx,"Apply host settings");idle(ctx);
            button(ctx,"Access");ctx.takeScreenshot("companions-access-small");button(ctx,"Back");ctx.getInput().resizeWindow(1280,720);ctx.waitTicks(5);ctx.takeScreenshot("companions-team");
            if(ControllerClient.count(state().getAsJsonObject("usage"),"total")!=0)throw new AssertionError("UI testing started a model request");
            System.out.println("COMPANIONS_UI_VERIFIED dynamic roster, live signed skin, effort, persona, chat, filters, usage, rules, small window; zero model tokens");
        }catch(Exception e){throw new AssertionError(e);}finally{ctx.runOnClient(c->c.disconnect(new TitleScreen(),false));}
    }
}
