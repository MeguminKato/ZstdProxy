package cn.tohsaka.factory.zstdproxy;

import cn.tohsaka.factory.zstdpiping.api.ZstdProxyLaunchWrapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.spongepowered.include.com.google.common.base.Charsets;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod(Zstdproxy.MODID)
public class Zstdproxy {
    public static final String MODID = "zstdproxy";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Zstdproxy(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::loadComplete);
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    public void loadComplete(final FMLLoadCompleteEvent event){
        loadZstdServers();
    }

    public static List<ZstdServerList.ZstdServer> servers = new ArrayList<>();
    public static Map<String,Integer> proxyportmap = new HashMap<>();
    protected static CopyOnWriteArrayList<ServerSocket> proxies = new CopyOnWriteArrayList<>();
    public void loadZstdServers(){
        servers.clear();
        try {
            if(ClientConfig.getUrl().contains("http")){
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(ClientConfig.getUrl()))
                        .build();
                String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
                loadData(body);
            }else {
                var file = Path.of("servers.zstd.json");
                if(Files.exists(file)){
                    loadData(new String(Files.readAllBytes(file), Charsets.UTF_8));
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void loadData(String data){
        ZstdServerList serverlist = new Gson().fromJson(data,new TypeToken<ZstdServerList>(){}.getType());
        if(serverlist.servers().size()>0){
            servers.addAll(serverlist.servers());
        }
        for (ZstdServerList.ZstdServer server : servers) {
            new Thread(()->{
                try {
                    var addr = server.addr().split(":");
                    ZstdProxyLaunchWrapper.startClientProxyLocalRandomPort(addr[0], Integer.parseInt(addr.length == 2 ? addr[1] : "25565"), 3, (serverSocket,port) -> {
                        if(port!=null){
                            proxies.add(serverSocket);
                            proxyportmap.put(server.mask(),port);
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
