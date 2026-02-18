package cn.tohsaka.factory.zstdpiping.protocol;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;

public final class ProxyProtocolParser {

    private static final byte[] V2_SIGNATURE = new byte[]{
            0x0D,0x0A,0x0D,0x0A,0x00,0x0D,0x0A,0x51,0x55,0x49,0x54,0x0A
    };

    public static ProxyInfo parse(BufferedInputStream bin) throws IOException {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        bin.mark(128);
        byte[] sig = readExactly(bin, 12);
        raw.write(sig);
        if (!Arrays.equals(sig, V2_SIGNATURE)) {
            return ProxyInfo.none();
        }

        byte verCmd = readByte(bin); raw.write(verCmd);
        byte famProto = readByte(bin); raw.write(famProto);
        int length = readUnsignedShort(bin);

        raw.write((length >>> 8) & 0xFF);
        raw.write(length & 0xFF);

        int version = (verCmd & 0xF0) >> 4;
        int command = (verCmd & 0x0F);

        if (version != 2 || command != 0x1) {
            byte[] rest = readExactly(bin, length);
            raw.write(rest);
            return new ProxyInfo(false, null, -1, null, -1, raw.toByteArray());
        }

        byte[] addrPart = readExactly(bin, length);
        raw.write(addrPart);

        ByteBuffer buf = ByteBuffer.wrap(addrPart);

        int family = (famProto & 0xF0) >> 4;
        int proto  = (famProto & 0x0F);

        if (family == 0x1 && proto == 0x1) {
            // IPv4 + TCP
            byte[] srcAddr = new byte[4];
            byte[] dstAddr = new byte[4];
            buf.get(srcAddr);
            buf.get(dstAddr);

            int srcPort = Short.toUnsignedInt(buf.getShort());
            int dstPort = Short.toUnsignedInt(buf.getShort());
            return new ProxyInfo(
                    true,
                    InetAddress.getByAddress(srcAddr).getHostAddress(),
                    srcPort,
                    InetAddress.getByAddress(dstAddr).getHostAddress(),
                    dstPort,
                    raw.toByteArray()
            );
        }

        if (family == 0x2 && proto == 0x1) {
            // IPv6 + TCP
            byte[] srcAddr = new byte[16];
            byte[] dstAddr = new byte[16];
            buf.get(srcAddr);
            buf.get(dstAddr);

            int srcPort = Short.toUnsignedInt(buf.getShort());
            int dstPort = Short.toUnsignedInt(buf.getShort());
            return new ProxyInfo(
                    true,
                    InetAddress.getByAddress(srcAddr).getHostAddress(),
                    srcPort,
                    InetAddress.getByAddress(dstAddr).getHostAddress(),
                    dstPort,
                    raw.toByteArray()
            );
        }
        // 其他类型
        return new ProxyInfo(false, null, -1, null, -1, raw.toByteArray());
    }

    // ---------- 工具方法 ----------

    private static byte readByte(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return (byte) b;
    }

    private static int readUnsignedShort(InputStream in) throws IOException {
        int hi = in.read();
        int lo = in.read();
        if ((hi | lo) < 0) throw new EOFException();
        return (hi << 8) | lo;
    }

    private static byte[] readExactly(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) throw new EOFException();
            off += r;
        }
        return buf;
    }
}