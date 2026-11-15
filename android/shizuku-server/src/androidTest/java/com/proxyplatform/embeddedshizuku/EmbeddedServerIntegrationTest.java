package com.proxyplatform.embeddedshizuku;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuApiConstants;

/** Real shell UID, app_process, provider, and UserService; no external Shizuku. */
@RunWith(AndroidJUnit4.class)
@SuppressLint("RestrictedApi")
public class EmbeddedServerIntegrationTest {
    @Test(timeout = 90_000)
    public void shellServerDeliversBinderAndStartsUserService() throws Exception {
        Log.i("KProxyXShizukuTest", "test entered");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String pkg = context.getPackageName();
        assertNotNull(context.getPackageManager().resolveContentProvider(pkg + ".embedded-shizuku", 0));
        Log.i("KProxyXShizukuTest", "cleaning previous process");
        shell(EmbeddedServerCommand.stop(pkg));
        Log.i("KProxyXShizukuTest", "starting shell server");
        CountDownLatch ready = new CountDownLatch(1);
        Shizuku.OnBinderReceivedListener listener = ready::countDown;
        Shizuku.addBinderReceivedListenerSticky(listener);
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(new ComponentName(pkg, EchoService.class.getName()))
                .tag("embedded-integration").daemon(false).debuggable(false).version(1);
        CountDownLatch bound = new CountDownLatch(1);
        AtomicReference<IBinder> remote = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder service) {
                remote.set(service);
                bound.countDown();
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        try (ParcelFileDescriptor stream = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(shellScript(EmbeddedServerCommand.start(pkg)))) {
            Log.i("KProxyXShizukuTest", "waiting for server Binder");
            assertTrue("Server startup failed", await(ready, pkg, false));
            Log.i("KProxyXShizukuTest", "server Binder received");
            assertEquals(2000, Shizuku.getUid());
            assertTrue(Shizuku.getVersion() >= 13);
            assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission());
            Shizuku.bindUserService(args, connection);
            Log.i("KProxyXShizukuTest", "waiting for UserService Binder");
            assertTrue("UserService handshake failed", await(bound, pkg, true));
            assertNotNull(remote.get());
            assertEquals("kproxyx.integration", remote.get().getInterfaceDescriptor());
            Log.i("KProxyXShizukuTest", "UserService Binder verified");
        } finally {
            Shizuku.removeBinderReceivedListener(listener);
            try { Shizuku.unbindUserService(args, connection, true); } catch (Throwable ignored) {}
            try { Shizuku.exit(); } catch (Throwable ignored) {}
            shell(EmbeddedServerCommand.stop(pkg));
        }
    }

    private static boolean await(CountDownLatch latch, String pkg, boolean userService) throws Exception {
        if (latch.await(25, TimeUnit.SECONDS)) return true;
        fail(shell("cat '" + EmbeddedServerCommand.logPath(pkg) + "'; "
                + (userService ? "cat '/data/local/tmp/kproxyx-" + pkg + "-userservice.log'; " : "")
                + "logcat -d -t 200 -s KProxyXShizuku:V Service:V ShizukuServiceStarter:V AndroidRuntime:E '*:S'"));
        return false;
    }

    private static String shell(String command) throws Exception {
        try (ParcelFileDescriptor fd = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(shellScript(command));
             FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String shellScript(String command) {
        // UiAutomation uses Runtime.exec(String), not adb shell: it tokenizes
        // whitespace and does not interpret quotes/redirections. Carry the
        // exact script in one whitespace-free sh -c argument instead.
        String encoded = Base64.encodeToString(command.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return "sh -c echo${IFS}" + encoded + "|base64${IFS}-d|sh";
    }

    public static class EchoService extends Binder {
        public EchoService() { attachInterface(null, "kproxyx.integration"); }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code == ShizukuApiConstants.USER_SERVICE_TRANSACTION_destroy) {
                System.exit(0);
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    }
}
