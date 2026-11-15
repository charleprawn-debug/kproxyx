package com.proxyplatform.embeddedshizuku;

/** Shared by the ADB launcher and device integration tests. */
public final class EmbeddedServerCommand {
    private EmbeddedServerCommand() {}

    public static String processName(String packageName) {
        requirePackageName(packageName);
        return "kproxyx_shizuku_" + packageName.replace('.', '_');
    }

    public static String logPath(String packageName) {
        requirePackageName(packageName);
        return "/data/local/tmp/kproxyx-" + packageName + "-server.log";
    }

    public static String pidPath(String packageName) {
        requirePackageName(packageName);
        return "/data/local/tmp/kproxyx-" + packageName + "-server.pid";
    }

    public static String start(String packageName) {
        requirePackageName(packageName);
        // The PID belongs to app_process itself, not to a wrapper shell. Keep
        // the ADB stream open until stop. No writable copy of the APK is made.
        return "set -eu\n"
                + "APK=$(pm path '" + packageName + "' | sed -n 's/^package://p' | head -n 1)\n"
                + "test -n \"$APK\" && test -r \"$APK\"\n"
                + "LIB_DIR=$(dirname \"$APK\")/lib/arm64\n"
                + "echo $$ > '" + pidPath(packageName) + "'\n"
                + "export CLASSPATH=\"$APK\"\n"
                + "exec /system/bin/app_process -Djava.class.path=\"$APK\" "
                + "-Dshizuku.library.path=\"$LIB_DIR\" "
                + "-Dkproxyx.manager.package=" + packageName + " "
                + "/system/bin --nice-name=" + processName(packageName) + " "
                + "com.proxyplatform.embeddedshizuku.EmbeddedServerMain "
                + ">'" + logPath(packageName) + "' 2>&1";
    }

    public static String stop(String packageName) {
        requirePackageName(packageName);
        // Never signal a reused/stale PID belonging to another process.
        return "P=$(cat '" + pidPath(packageName) + "' 2>/dev/null); "
                + "case \"$P\" in ''|*[!0-9]*) exit 0;; esac; "
                + "NAME=$(tr '\\000' '\\n' < /proc/$P/cmdline 2>/dev/null | head -n 1); "
                + "if [ \"$NAME\" = '" + processName(packageName) + "' ]; then "
                + "kill \"$P\" 2>/dev/null || true; fi; "
                + "rm -f '" + pidPath(packageName) + "'";
    }

    private static void requirePackageName(String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")) {
            throw new IllegalArgumentException("Invalid Android package name");
        }
    }
}
