package cn.tohsaka.factory.zstdproxy;

import net.minecraft.client.multiplayer.ServerData;

public class ZstdServerDataImpl extends ServerData {
    public ZstdServerDataImpl(String name, String ip, Type type) {
        super(name, ip, type);
    }
}
