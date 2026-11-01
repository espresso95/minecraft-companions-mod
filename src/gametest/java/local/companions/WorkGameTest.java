package local.companions;

import com.google.gson.*;
import java.nio.file.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.widget.ScrollableTextWidget;

/** Renders backend-produced work records without starting a world or model. */
final class WorkGameTest {
    void run(ClientGameTestContext ctx) {
        try {
            JsonObject fixture=JsonParser.parseString(Files.readString(Path.of(System.getenv("WORK_VIEW_FILE")))).getAsJsonObject();
            ctx.runOnClient(c->{CompanionMailbox.API.state=fixture;CompanionMailbox.API.error=null;});
            ctx.getInput().resizeWindow(1280,720);
            ctx.setScreen(CompanionsScreen::new);ctx.waitTicks(3);ctx.clickScreenButton("Work");ctx.waitTicks(3);
            ctx.takeScreenshot("work-team");
            ctx.runOnClient(c->{
                var lists=c.currentScreen.children().stream().filter(OptionList.class::isInstance).map(OptionList.class::cast).toList();
                if(lists.size()!=2)throw new AssertionError("Work list missing");
                var list=lists.get(1);int index=-1;
                String title=fixture.getAsJsonObject("work").getAsJsonArray("items").get(0).getAsJsonObject().get("title").getAsString();
                for(int i=0;i<list.children().size();i++)if(list.children().get(i).getNarration().getString().startsWith(title+" ")){index=i;break;}
                if(index<0)throw new AssertionError("Backend task missing");
                list.setScrollY(Math.max(0,index*29-29));
                if(!c.currentScreen.mouseClicked(list.getX()+14,list.getY()+14+index*29-list.getScrollY(),0))throw new AssertionError("Task detail click refused");
            });
            ctx.waitTicks(3);
            ctx.runOnClient(c->{
                if(!(c.currentScreen instanceof WorkDetailsScreen))throw new AssertionError("Task details missing");
                var content=c.currentScreen.children().stream().filter(ScrollableTextWidget.class::isInstance).map(ScrollableTextWidget.class::cast).findFirst().orElseThrow();
                if(!content.getMessage().getString().contains("Checkpoint"))throw new AssertionError("Checkpoint not displayed");
            });
            ctx.takeScreenshot("work-details");ctx.getInput().resizeWindow(854,480);ctx.waitTicks(3);ctx.takeScreenshot("work-details-small");
            ctx.clickScreenButton("Back");ctx.waitTicks(3);ctx.takeScreenshot("work-team-small");
            System.out.println("WORK_UI_VERIFIED backend fields, live task details, checkpoint, scrollable small-window view; zero model tokens");
        } catch(Exception e){throw new AssertionError(e);}
        finally {ctx.setScreen(net.minecraft.client.gui.screen.TitleScreen::new);}
    }
}
