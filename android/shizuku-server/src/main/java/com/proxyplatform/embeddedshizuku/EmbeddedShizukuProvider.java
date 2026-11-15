package com.proxyplatform.embeddedshizuku;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import moe.shizuku.api.BinderContainer;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuApiConstants;
import rikka.shizuku.ShizukuProvider;

/**
 * The manager half of the official Shizuku UserService handshake. The ordinary
 * client provider handles sendBinder/getBinder only, not sendUserService.
 * Access remains protected by INTERACT_ACROSS_USERS_FULL in the app manifest.
 */
@SuppressLint("RestrictedApi")
public final class EmbeddedShizukuProvider extends ShizukuProvider {
    private static final String TAG = "KProxyXShizuku";
    private static final String EXTRA_BINDER = "moe.shizuku.privileged.api.intent.extra.BINDER";
    private final HandlerThread workerThread = new HandlerThread("EmbeddedShizukuProvider");

    private Handler worker;

    @Override
    public boolean onCreate() {
        disableAutomaticSuiInitialization();
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        return super.onCreate();
    }

    @Override
    @SuppressWarnings("deprecation")
    public Bundle call(String method, String arg, Bundle extras) {
        if (!"sendUserService".equals(method)) return super.call(method, arg, extras);
        if (extras == null) return null;
        extras.setClassLoader(BinderContainer.class.getClassLoader());
        String token = extras.getString(ShizukuApiConstants.USER_SERVICE_ARG_TOKEN);
        BinderContainer container = extras.getParcelable(EXTRA_BINDER);
        IBinder service = container == null ? null : container.binder;
        if (token == null || service == null || !service.pingBinder()) return null;

        CountDownLatch attached = new CountDownLatch(1);
        AtomicReference<Bundle> reply = new AtomicReference<>();
        Shizuku.OnBinderReceivedListener listener = () -> {
            try {
                Bundle options = new Bundle();
                options.putString(ShizukuApiConstants.USER_SERVICE_ARG_TOKEN, token);
                // Provider calls arrive from shell. The attach operation must
                // run as the manager app, not with the incoming Binder identity.
                Shizuku.attachUserService(service, options);
                Bundle result = new Bundle();
                result.putParcelable(EXTRA_BINDER, new BinderContainer(Shizuku.getBinder()));
                reply.set(result);
            } catch (Throwable failure) {
                Log.e(TAG, "Cannot attach embedded UserService", failure);
            } finally {
                attached.countDown();
            }
        };
        Shizuku.addBinderReceivedListenerSticky(listener, worker);
        try {
            // await returns false on timeout; it does not throw TimeoutException.
            if (!attached.await(5, TimeUnit.SECONDS)) {
                Log.e(TAG, "Server Binder not received within 5s");
                return null;
            }
            return reply.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            Shizuku.removeBinderReceivedListener(listener);
        }
    }
}
