package com.proxyplatform.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class BroadcastUiMessagesTest {
    @Test public void recognizesOnlySuccessfulOperationStates() {
        assertEquals("نجح تشغيل البث.", BroadcastUiMessages.operationResult("ACTIVE", false));
        assertEquals("نجح إيقاف البث.", BroadcastUiMessages.operationResult("IDLE", true));
        assertEquals("نجح إيقاف البث.", BroadcastUiMessages.operationResult("READY", true));
        assertEquals("تعذر إيقاف البث. أعد المحاولة.", BroadcastUiMessages.operationResult("ACTIVE", true));
    }

    @Test public void neverReflectsUntrustedRemoteValues() {
        String payload = "secret-value /data/local/tmp/internal.log 192.0.2.10 java.lang.Exception\nstack";
        for (String state : new String[] {payload, "ERROR", "", null}) {
            for (boolean stopping : new boolean[] {true, false}) {
                String message = BroadcastUiMessages.operationResult(state, stopping);
                assertFalse(message.contains("secret-value"));
                assertFalse(message.contains("/data"));
                assertFalse(message.contains("192.0.2.10"));
                assertFalse(message.contains("Exception"));
                assertFalse(message.contains("\n"));
            }
        }
    }

    @Test public void doesNotMistakeStartingForSuccess() {
        assertEquals("جارٍ تجهيز البث.", BroadcastUiMessages.operationResult("STARTING", false));
        assertFalse(BroadcastUiMessages.operationResult("ERROR", false).contains("نجح"));
    }

    @Test public void suggestsAdbOnlyForAnUnavailableServerAndTransport() {
        assertTrue(BroadcastUiMessages.failure(false, false).contains("ADB"));
        assertFalse(BroadcastUiMessages.failure(true, false).contains("ADB"));
        assertFalse(BroadcastUiMessages.failure(false, true).contains("ADB"));
        assertFalse(BroadcastUiMessages.failure(true, true).contains("ADB"));
    }
}
