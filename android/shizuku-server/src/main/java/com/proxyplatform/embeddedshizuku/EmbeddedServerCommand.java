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
        // A separate, non-interactive child execs app_process. It is not the
        // ADB process-group leader, so EmbeddedServerMain can call setsid().
        // All three standard descriptors must be detached from the ADB PTY.
        // Wait for the child's post-setsid PID marker before closing ADB, or
        // terminal teardown could race VM initialization and kill the child.
        return "set -eu\numask 077\n"
                + "APK=$(pm path '" + packageName + "' | sed -n 's/^package://p' | head -n 1)\n"
                + "test -n \"$APK\" && test -r \"$APK\"\n"
                + "rm -f '" + pidPath(packageName) + "'\n"
                + "export CLASSPATH=\"$APK\"\n"
                + "/system/bin/sh -c 'cd / && exec /system/bin/app_process -Djava.class.path=\"$CLASSPATH\" "
                + "-Dshizuku.library.path=\"$(dirname \"$CLASSPATH\")/lib/arm64\" "
                + "-Dkproxyx.manager.package=" + packageName + " "
                + "/system/bin --nice-name=" + processName(packageName) + " "
                + "com.proxyplatform.embeddedshizuku.EmbeddedServerMain' "
                + "</dev/null >'" + logPath(packageName) + "' 2>&1 &\n"
                + "P=$!\nI=0\n"
                + "while [ \"$(cat '" + pidPath(packageName) + "' 2>/dev/null)\" != \"$P\" ]; do\n"
                + "  if ! kill -0 \"$P\" 2>/dev/null || [ \"$I\" -ge 50 ]; then\n"
                + "    cat '" + logPath(packageName) + "' 2>/dev/null; exit 1\n"
                + "  fi\n"
                + "  I=$((I + 1)); sleep 0.2\n"
                + "done\n"
                + "echo \"Embedded server detached; pid=$P\"";
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
