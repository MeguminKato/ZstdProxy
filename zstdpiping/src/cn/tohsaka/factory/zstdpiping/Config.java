package cn.tohsaka.factory.zstdpiping;

import java.util.HashMap;
import java.util.Map;

public class Config {

    public final Side side;
    public final String listenHost;
    public final int listenPort;
    public final String targetHost;
    public final int targetPort;
    public final boolean proxyprotocolv2;
    public final int level;
    private Config(
            Side side,
            String listenHost,
            int listenPort,
            String targetHost,
            int targetPort,
            boolean proxyprotocolv2,
            int level
    ) {
        this.side = side;
        this.listenHost = listenHost;
        this.listenPort = listenPort;
        this.targetHost = targetHost;
        this.targetPort = targetPort;
        this.proxyprotocolv2 = proxyprotocolv2;
        this.level = level;
    }

    public static Config parse(String[] args) {
        Map<String, String> map = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                map.put(args[i].substring(2), args[++i]);
            } else if (!map.containsKey("side")) {
                map.put("side", args[i]);
            }
        }

        Side side = Side.parse(map.getOrDefault("side", "client"));
        String listen = map.getOrDefault("listen", "127.0.0.1:25566");
        String target = map.getOrDefault("target", "127.0.0.1:25565");
        int level = Integer.parseInt(map.getOrDefault("level", "3"));

        String[] l = listen.split(":");
        String[] t = target.split(":");
        boolean proxyprotocolv2 = false;
        if(map.containsKey("proxyprotocol") && map.get("proxyprotocol").equalsIgnoreCase("v2")){
            proxyprotocolv2 = true;
        }
        return new Config(
                side,
                l[0],
                Integer.parseInt(l[1]),
                t[0],
                Integer.parseInt(t[1]),
                proxyprotocolv2,
                level
        );
    }
}
