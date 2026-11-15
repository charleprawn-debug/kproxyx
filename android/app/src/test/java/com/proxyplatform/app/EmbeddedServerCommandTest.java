package com.proxyplatform.app;

import com.proxyplatform.embeddedshizuku.EmbeddedServerCommand;
import org.junit.Test;
import static org.junit.Assert.*;

public class EmbeddedServerCommandTest {
    @Test public void launchUsesBootstrapAndExactPid() {
        String command = EmbeddedServerCommand.start("com.proxyplatform.app.debug");
        assertTrue(command.contains("echo $$ >"));
        assertTrue(command.contains("exec /system/bin/app_process"));
        assertTrue(command.contains("com.proxyplatform.embeddedshizuku.EmbeddedServerMain"));
        assertTrue(command.contains("-Dkproxyx.manager.package=com.proxyplatform.app.debug"));
        assertFalse(command.contains("moe.shizuku.privileged.api"));
    }

    @Test public void debugAndReleaseDoNotShareStateOrProcesses() {
        String release = "com.proxyplatform.app";
        String debug = release + ".debug";
        assertNotEquals(EmbeddedServerCommand.processName(release), EmbeddedServerCommand.processName(debug));
        assertNotEquals(EmbeddedServerCommand.pidPath(release), EmbeddedServerCommand.pidPath(debug));
        assertNotEquals(EmbeddedServerCommand.logPath(release), EmbeddedServerCommand.logPath(debug));
    }

    @Test public void stopValidatesPidAndProcessNameBeforeSignalling() {
        String command = EmbeddedServerCommand.stop("com.proxyplatform.app");
        assertTrue(command.contains("*[!0-9]*"));
        assertTrue(command.contains("/proc/$P/cmdline"));
        assertTrue(command.contains("kproxyx_shizuku_com_proxyplatform_app"));
        assertFalse(command.contains("killall"));
        assertFalse(command.contains("pkill"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShellInjection() {
        EmbeddedServerCommand.start("com.example'; exit 0; #");
    }
}
