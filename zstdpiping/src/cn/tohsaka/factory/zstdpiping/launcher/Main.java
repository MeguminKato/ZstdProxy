package cn.tohsaka.factory.zstdpiping.launcher;

import cn.tohsaka.factory.zstdpiping.Config;
import cn.tohsaka.factory.zstdpiping.DualTrafficStats;
import cn.tohsaka.factory.zstdpiping.ProxyNode;
import cn.tohsaka.factory.zstdpiping.Side;
import cn.tohsaka.factory.zstdpiping.api.ZstdProxyLaunchWrapper;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {

    public static Config cfg = null;
    public static void main(String[] args) throws Exception {
        if(args.length == 0){
            System.out.println("======= MC Zstd Proxy =======");
            System.out.println("Using:");
            System.out.println("    --side server/client");
            System.out.println("    --listen 127.0.0.1:25566");
            System.out.println("    --target 127.0.0.1:25565");
            System.out.println("    --proxyprotocol v2");
            System.out.println("    --level 4");
            System.exit(0);
        }
        cfg = Config.parse(args);

        System.out.println("Side   : " + cfg.side);
        System.out.println("Listen : " + cfg.listenHost + ":" + cfg.listenPort);
        System.out.println("Target : " + cfg.targetHost + ":" + cfg.targetPort);
        System.out.println("ProxyProtocolV2: "+ (cfg.proxyprotocolv2?"yes":"no"));

        ZstdProxyLaunchWrapper.startStatsPrinter();

        if (cfg.side == Side.CLIENT) {
            ZstdProxyLaunchWrapper.startClientProxy(cfg.targetHost, cfg.targetPort,cfg.listenHost,cfg.listenPort,cfg.level);
        } else {
            ZstdProxyLaunchWrapper.startServerProxy(cfg.targetHost, cfg.targetPort,cfg.listenHost,cfg.listenPort,cfg.level,cfg.proxyprotocolv2);
        }
    }
}
