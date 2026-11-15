package com.proxyplatform.embeddedshizuku;

import android.os.Process;
import android.system.Os;
import android.util.Log;

import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

/** Keep bootstrap/linkage failures visible even before ShizukuService can load. */
public final class EmbeddedServerMain {
    private static final String TAG = "KProxyXShizuku";

    private EmbeddedServerMain() {}

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) -> {
            report("Uncaught failure on " + thread.getName(), failure);
            System.exit(1);
        });
        try {
            System.err.println(TAG + ": bootstrap uid=" + Process.myUid());
            if (Process.myUid() != 2000 && Process.myUid() != 0) {
                throw new SecurityException("Embedded server must be started by ADB shell");
            }
            // Match the upstream native starter's independent session. Merely
            // backgrounding app_process (or retaining its ADB stream) leaves it
            // vulnerable to adbd's terminal/process-group teardown when Wi-Fi
            // is switched off to enable the hotspot.
            int session = Os.setsid();
            String managerPackage = System.getProperty("kproxyx.manager.package");
            try (FileOutputStream pid = new FileOutputStream(EmbeddedServerCommand.pidPath(managerPackage))) {
                pid.write(Integer.toString(Process.myPid()).getBytes(StandardCharsets.US_ASCII));
            }
            System.err.println(TAG + ": detached session=" + session + " pid=" + Process.myPid());
            // Reflection is intentional: resolving the server's superclass can
            // fail before its main() runs. Catch that failure here, not in it.
            Class.forName("rikka.shizuku.server.ShizukuService")
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) args);
        } catch (Throwable failure) {
            if (failure instanceof InvocationTargetException && failure.getCause() != null) {
                failure = failure.getCause();
            }
            report("Server bootstrap failed", failure);
            System.exit(1);
        }
    }

    private static void report(String message, Throwable failure) {
        System.err.println(TAG + ": " + message);
        failure.printStackTrace(System.err);
        System.err.flush();
        Log.e(TAG, message, failure);
    }
}
