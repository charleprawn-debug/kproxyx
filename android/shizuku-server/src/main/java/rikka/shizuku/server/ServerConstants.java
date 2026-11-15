package rikka.shizuku.server;

public class ServerConstants {

    public static final int MANAGER_APP_NOT_FOUND = 50;

    /** KProxyX is the embedded manager; no separate Shizuku APK is required. */
    public static final String PERMISSION = "moe.shizuku.manager.permission.API_V23";
    public static final String MANAGER_APPLICATION_ID = System.getProperty(
            "kproxyx.manager.package",
            "com.proxyplatform.app"
    );
    public static final String REQUEST_PERMISSION_ACTION = MANAGER_APPLICATION_ID + ".intent.action.REQUEST_PERMISSION";

    public static final int BINDER_TRANSACTION_getApplications = 10001;
}
