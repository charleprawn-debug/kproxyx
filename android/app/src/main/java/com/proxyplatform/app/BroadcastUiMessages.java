package com.proxyplatform.app;

/**
 * Closed set of customer-facing broadcast messages. Remote service details and
 * exceptions are deliberately not accepted as inputs or interpolated here.
 */
public final class BroadcastUiMessages {
    private BroadcastUiMessages() {}

    public static String operationResult(String state, boolean stopping) {
        if (stopping) {
            if ("READY".equals(state) || "IDLE".equals(state)) return "نجح إيقاف البث.";
            return "تعذر إيقاف البث. أعد المحاولة.";
        }
        if ("ACTIVE".equals(state)) return "نجح تشغيل البث.";
        if ("STARTING".equals(state)) return "جارٍ تجهيز البث.";
        return "تعذر تشغيل البث. تحقق من جاهزية الاتصال ثم أعد المحاولة.";
    }

    public static String failure(boolean serverReady, boolean adbReady) {
        if (!serverReady && !adbReady) {
            return "خدمة البث غير متاحة. افتح صفحة ADB وأعد الاتصال ثم حاول مجدداً.";
        }
        return "تعذر تنفيذ أمر البث. أعد المحاولة.";
    }
}
