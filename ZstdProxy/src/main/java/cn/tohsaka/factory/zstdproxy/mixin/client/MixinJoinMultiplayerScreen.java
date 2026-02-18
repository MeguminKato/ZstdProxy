package cn.tohsaka.factory.zstdproxy.mixin.client;

import cn.tohsaka.factory.zstdproxy.ZstdServerListImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(JoinMultiplayerScreen.class)
public class MixinJoinMultiplayerScreen {
    @Redirect(
            method = "init",
            at = @At(
                    value = "NEW",
                    target = "(Lnet/minecraft/client/Minecraft;)Lnet/minecraft/client/multiplayer/ServerList;"
            )
    )
    private ServerList redirectServerListInit(Minecraft mc) {
        return new ZstdServerListImpl(mc);
    }


    @Inject(method = "onClose",at = @At("HEAD"))
    public void onClose(CallbackInfo ci){

    }
}
