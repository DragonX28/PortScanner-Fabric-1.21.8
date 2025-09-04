package com.example.mixin.client;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.option.ServerList;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.MinecraftClient;

import java.util.List;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    protected MultiplayerScreenMixin(Text text) {
        super(text);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        // Кнопка Scan Servers
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.serverscanner.Scan"), button -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ServerList serverList = new ServerList(mc);
            mc.setScreen(new com.example.screen.ServerScannerScreen(serverList));
        }).dimensions(5, 5, 100, 20).build());

        // Кнопка Clear Servers (расположена справа от Scan Servers)
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("screen.serverscanner.Clear"), button -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            ServerList serverList = new ServerList(mc);
            clearScannedServers(serverList);
        }).dimensions(315, 5, 100, 20).build());
    }

    private void clearScannedServers(ServerList serverList) {
        ServerListAccessor accessor = (ServerListAccessor) serverList;
        List<ServerInfo> currentServers = accessor.getServers();

        currentServers.removeIf(s -> s.name.startsWith("Scanned: "));

        serverList.saveFile();

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new MultiplayerScreen(mc.currentScreen));
    }


}
