package com.example.mixin.client;

import net.minecraft.client.option.ServerList;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ServerList.class)
public interface ServerListAccessor {
    @Accessor("servers")
    List<ServerInfo> getServers();

    @Accessor("servers")
    void setServers(List<ServerInfo> servers);

    @Accessor("hiddenServers")
    List<ServerInfo> getHiddenServers();

    @Accessor("hiddenServers")
    void setHiddenServers(List<ServerInfo> hiddenServers);
}
