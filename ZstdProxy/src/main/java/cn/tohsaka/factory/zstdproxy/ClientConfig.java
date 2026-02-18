package cn.tohsaka.factory.zstdproxy;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
@EventBusSubscriber(modid = Zstdproxy.MODID)
public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.ConfigValue<String> Url = BUILDER.define("url","");
    public static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName));
    }
    private static String url = "";
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if(event.getConfig().getSpec() == SPEC) {
            ClientConfig.url = Url.get();
        }
    }

    public static String getUrl(){
        return url;
    }
}
