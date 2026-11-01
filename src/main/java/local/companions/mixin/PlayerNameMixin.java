package local.companions.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import local.companions.CompanionMailbox;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Client-only decoration; vanilla controls name-tag visibility and distance. */
@Mixin(PlayerEntityRenderer.class)
abstract class PlayerNameMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("TAIL"))
    private void companionStatus(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (state.displayName == null) return;
        String label = CompanionMailbox.statusFor(player.getGameProfile().getName());
        if (!label.isEmpty()) state.displayName = state.displayName.copy()
            .append(Text.literal(" [" + label + "]").formatted(Formatting.GRAY));
    }
}
