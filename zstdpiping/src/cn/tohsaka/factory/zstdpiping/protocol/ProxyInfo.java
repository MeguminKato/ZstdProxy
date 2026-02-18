package cn.tohsaka.factory.zstdpiping.protocol;

public final class ProxyInfo {
    public final boolean proxied;
    public final String srcIp;
    public final int srcPort;
    public final String dstIp;
    public final int dstPort;

    /** PROXY protocol 原始二进制（完整头） */
    public final byte[] rawHeader;

    public ProxyInfo(boolean proxied,
                     String srcIp,
                     int srcPort,
                     String dstIp,
                     int dstPort,
                     byte[] rawHeader) {
        this.proxied = proxied;
        this.srcIp = srcIp;
        this.srcPort = srcPort;
        this.dstIp = dstIp;
        this.dstPort = dstPort;
        this.rawHeader = rawHeader;
    }

    public static ProxyInfo none() {
        return new ProxyInfo(false, null, -1, null, -1, null);
    }
}