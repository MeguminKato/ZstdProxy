package cn.tohsaka.factory.zstdpiping;

import cn.tohsaka.factory.zstdpiping.launcher.Main;
import cn.tohsaka.factory.zstdpiping.protocol.ProxyProtocolParser;
import cn.tohsaka.factory.zstdpiping.protocol.ProxyProtocolV2Builder;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

public class ProxyNode {
    public static void forward(Socket in, Socket out, DualTrafficStats stats, int level) {
        Thread t1 = new Thread(() -> forwardCompress(in, out, stats, level));
        Thread t2 = new Thread(() -> forwardDecompress(in, out, stats));
        t1.start();
        t2.start();
    }

    public static void forwardCompress(Socket in, Socket out, DualTrafficStats stats, int level) {
        ZstdOutputStream zstdOut = null;
        try {
            var rawOut = out.getOutputStream();
            zstdOut = new ZstdOutputStream(rawOut, level);
            zstdOut.setCloseFrameOnFlush(false);
            InputStream rawIn = in.getInputStream();

            //fake PPv2 packet
            /*if(Main.cfg.side == Side.CLIENT){
                var header = ProxyProtocolV2Builder.build(new InetSocketAddress(InetAddress.getByName("8.8.8.8"),4444),new InetSocketAddress(InetAddress.getByName("192.168.199.40"),25565));
                rawOut.write(header);
                rawOut.flush();
            }*/


            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = rawIn.read(buf)) != -1) {
                zstdOut.write(buf, 0, n);
                zstdOut.flush();
            }
        } catch (Exception e) {
            System.err.println("Compress Error: " + e.getMessage());
        } finally {
            try {
                if (zstdOut != null) {
                    // 必须先完成 Zstd 帧，再关掉输出流
                    zstdOut.close();
                }
                // 只关闭输出方向的半连接，不要 close 整个 socket
                if (!out.isClosed()) out.shutdownOutput();
            } catch (Exception e) {}
        }
    }

    public static void forwardDecompress(Socket in, Socket out, DualTrafficStats stats) {
        ZstdInputStream zstdIn = null;
        try {
            zstdIn = new ZstdInputStream(in.getInputStream());
            OutputStream rawOut = out.getOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = zstdIn.read(buf)) != -1) {
                rawOut.write(buf, 0, n);
                rawOut.flush();
            }
        } catch (Exception e) {
            System.err.println("Decompress Error: " + e.getMessage());
        } finally {
            try {
                if (zstdIn != null) zstdIn.close();
                if (!out.isClosed()) out.shutdownOutput();
            } catch (Exception e) {}
        }
    }
}
