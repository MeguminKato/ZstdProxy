package cn.tohsaka.factory.zstdpiping.api;

import cn.tohsaka.factory.zstdpiping.DualTrafficStats;
import cn.tohsaka.factory.zstdpiping.ProxyNode;
import cn.tohsaka.factory.zstdpiping.Side;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ZstdProxyLaunchWrapper {
    public static void startClientProxy(String targetHost,int targetPort,String listenHost,int listenPort,int level) throws Exception {
        startClientProxy(targetHost,targetPort,listenHost,listenPort,level,null);
    }
    public static void startClientProxy(String targetHost,int targetPort,String listenHost,int listenPort,int level,Consumer<ServerSocket> startCallback) throws Exception{
        try(ServerSocket serverSocket = new ServerSocket(listenPort,50,InetAddress.getByName(targetHost))){
            if(startCallback!=null){
                startCallback.accept(serverSocket);
            }
            try{
                while (true) {
                    if(serverSocket.isClosed()){
                        break;
                    }
                    Socket incoming = serverSocket.accept();
                    Socket outgoing = new Socket(
                            targetHost,
                            targetPort
                    );
                    System.out.println("New connection from " + incoming.getRemoteSocketAddress());
                    startClientSide(incoming, outgoing,level);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    public static void startClientProxyLocalRandomPort(String targetHost, int targetPort, int level, BiConsumer<ServerSocket,Integer> startCallback) throws Exception{
        ServerSocket serverSocket = new ServerSocket(0,50, InetAddress.getByName("127.0.0.1"));
        if(startCallback!=null && !serverSocket.isClosed()){
            startCallback.accept(serverSocket,serverSocket.getLocalPort());
        }
        while (true) {
            if(serverSocket.isClosed()){
                break;
            }
            Socket incoming = serverSocket.accept();
            Socket outgoing = new Socket(
                    targetHost,
                    targetPort
            );
            System.out.println("New connection from " + incoming.getRemoteSocketAddress());
            startClientSide(incoming, outgoing,level);
        }
    }

    public static void startServerProxy(String targetHost, int targetPort, String listenHost, int listenPort, int level, boolean proxyprotocolv2) throws Exception {
        startServerProxy(targetHost,targetPort,listenHost,listenPort,level,proxyprotocolv2,null);
    }
    public static void startServerProxy(String targetHost, int targetPort, String listenHost, int listenPort, int level, boolean proxyprotocolv2, Consumer<ServerSocket> startCallback) throws Exception{
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.bind(new InetSocketAddress(listenHost,listenPort));
            if(startCallback!=null){
                startCallback.accept(serverSocket);
            }
            try {
                while (true) {
                    if(serverSocket.isClosed()){
                        break;
                    }
                    Socket incoming = serverSocket.accept();
                    Socket outgoing = new Socket(
                            targetHost,
                            targetPort
                    );
                    System.out.println("New connection from " + incoming.getRemoteSocketAddress());
                    startServerSide(incoming, outgoing,level,proxyprotocolv2);
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }




    static DualTrafficStats c2s = new DualTrafficStats();
    static DualTrafficStats s2c = new DualTrafficStats();

    private static void startClientSide(Socket client, Socket serverProxy,int level) {

        // Client -> ServerProxy
        new Thread(() ->
                ProxyNode.forwardCompress(
                        client,
                        serverProxy,
                        c2s,
                        level
                ),
                "C->S-compress"
        ).start();

        // ServerProxy -> Client
        new Thread(() ->
                ProxyNode.forwardDecompress(
                        serverProxy,
                        client,
                        s2c
                ),
                "S->C-decompress"
        ).start();
    }

    private static void startServerSide(Socket clientProxy, Socket mcServer,int level,boolean proxyprotocol) {
        // ClientProxy -> MC Server
        new Thread(() ->
                ProxyNode.forwardDecompress(
                        clientProxy,
                        mcServer,
                        c2s
                ),
                "C->MC-decompress"
        ).start();

        // MC Server -> ClientProxy
        new Thread(() ->
                ProxyNode.forwardCompress(
                        mcServer,
                        clientProxy,
                        s2c,
                        level
                ),
                "MC->C-compress"
        ).start();
    }

    public static Thread startStatsPrinter() {
        var thread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(1000);
                    //up.print("Upstream");
                    s2c.print("Downstream");
                }
            } catch (InterruptedException ignored) {
            }
        }, "stats");
        thread.start();
        return thread;
    }
    public void resetStatics(){
        s2c.reset();
        c2s.reset();
    }
}
