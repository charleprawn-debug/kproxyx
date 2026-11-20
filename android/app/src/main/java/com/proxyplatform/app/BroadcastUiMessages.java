package com.proxyplatform.app;

/**
 * Closed set of customer-facing broadcast messages. Remote service details and
 * exceptions are deliberately not accepted as inputs or interpolated here.
 */
public final class BroadcastUiMessages {
    private BroadcastUiMessages() {}

    public static String operationResult(String state, boolean stopping) {
        if (stopping) {
            if ("READY".equals(state) || "IDLE".equals(state)) {
                return AppLanguageKt.tr("نجح إيقاف البث.", "Broadcast stopped successfully.");
            }
            return AppLanguageKt.tr("تعذر إيقاف البث. أعد المحاولة.", "Could not stop the broadcast. Try again.");
        }
        if ("ACTIVE".equals(state)) return AppLanguageKt.tr("نجح تشغيل البث.", "Broadcast started successfully.");
        if ("STARTING".equals(state)) return AppLanguageKt.tr("جارٍ تجهيز البث.", "Preparing the broadcast.");
        return AppLanguageKt.tr("تعذر تشغيل البث. تحقق من جاهزية الاتصال ثم أعد المحاولة.",
                "Could not start the broadcast. Check that the connection is ready, then try again.");
    }

    public static String failure(boolean serverReady, boolean adbReady) {
        if (!serverReady && !adbReady) {
            return AppLanguageKt.tr("خدمة البث غير متاحة. افتح صفحة ADB وأعد الاتصال ثم حاول مجدداً.",
                    "The broadcast service is unavailable. Open the ADB page, reconnect, and try again.");
        }
        return AppLanguageKt.tr("تعذر تنفيذ أمر البث. أعد المحاولة.",
                "Could not run the broadcast command. Try again.");
    }
}
