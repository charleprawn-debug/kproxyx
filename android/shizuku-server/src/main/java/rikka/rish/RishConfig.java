package rikka.rish;

/** KProxyX server build does not expose the optional RISH terminal protocol. */
public final class RishConfig {
    private RishConfig() {}
    public static void init(String descriptor, int timeoutMillis) {}
    public static void setLibraryPath(String path) {}
}
