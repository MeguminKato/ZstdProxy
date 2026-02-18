package cn.tohsaka.factory.zstdproxy;

import cn.tohsaka.factory.zstdpiping.api.ZstdProxyLaunchWrapper;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.include.com.google.common.base.Charsets;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@OnlyIn(Dist.CLIENT)
public class ZstdServerListImpl extends ServerList {

    public ZstdServerListImpl(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    public void load() {
        super.load();
        List<ServerData> list = Lists.newArrayList();
        for (ZstdServerList.ZstdServer server : Zstdproxy.servers) {
            var serverdata = new ZstdServerDataImpl(server.name(),"127.0.0.1:"+Zstdproxy.proxyportmap.get(server.mask()), ServerData.Type.OTHER);
            if(server.icon()!=null){
                try {
                    byte[] icon = Base64.getDecoder().decode(server.icon());
                    serverdata.setIconBytes(icon);
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            list.addFirst(serverdata);
        }
        list.addAll(this.serverList);
        this.serverList = list;
    }

    @Override
    public void save() {
        serverList.removeIf(serverData -> serverData instanceof ZstdServerDataImpl);
        super.save();
        load();
    }
}
