package cn.tohsaka.factory.zstdpiping.protocol;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

public final class ProxyProtocolV2Builder {

    private static final byte[] SIGNATURE = new byte[]{
            0x0D, 0x0A, 0x0D, 0x0A,
            0x00, 0x0D, 0x0A, 0x51,
            0x55, 0x49, 0x54, 0x0A
    };

    private ProxyProtocolV2Builder() {}

    /**
     * 构造 PROXY protocol v2 (TCP)
     */
    public static byte[] build(
            InetSocketAddress src,
            InetSocketAddress dst
    ) {
        InetAddress srcAddr = src.getAddress();
        InetAddress dstAddr = dst.getAddress();

        boolean ipv4 = srcAddr.getAddress().length == 4;

        if (ipv4 != (dstAddr.getAddress().length == 4)) {
            throw new IllegalArgumentException("src/dst IP family mismatch");
        }

        ByteBuffer buf = ByteBuffer.allocate(
                16 + (ipv4 ? 12 : 36)
        );

        // ---- signature ----
        buf.put(SIGNATURE);

        // ---- ver/cmd ----
        buf.put((byte) 0x21); // version=2, command=PROXY

        // ---- fam/proto ----
        buf.put((byte) (ipv4 ? 0x11 : 0x21)); // INET/INET6 + STREAM

        // ---- length ----
        buf.putShort((short) (ipv4 ? 12 : 36));

        // ---- address block ----
        buf.put(srcAddr.getAddress());
        buf.put(dstAddr.getAddress());
        buf.putShort((short) src.getPort());
        buf.putShort((short) dst.getPort());

        return buf.array();
    }
}