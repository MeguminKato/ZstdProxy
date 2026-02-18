package cn.tohsaka.factory.zstdpiping;

public enum Side {
    CLIENT,
    SERVER;

    public static Side parse(String s) {
        return Side.valueOf(s.toUpperCase());
    }
}