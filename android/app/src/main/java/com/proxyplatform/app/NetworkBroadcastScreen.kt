package com.proxyplatform.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
internal fun NetworkBroadcastScreen(
    vm: AppViewModel,
    padding: PaddingValues,
    onGoToAdb: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adbState by remember { mutableStateOf(WirelessDebuggingManager.checkState(context)) }
    var serverReady by remember { mutableStateOf(NetworkBroadcastController.isServerReady(context)) }
    var running by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val adbReady = adbState == WirelessDebuggingManager.DebuggingState.READY
    val vpnReady = ProxyVpnService.isRunning(context)

    LaunchedEffect(Unit) {
        var checkedOnce = false
        while (true) {
            adbState = WirelessDebuggingManager.checkState(context)
            serverReady = NetworkBroadcastController.isServerReady(context)
            if (!busy && (!checkedOnce || running || serverReady)) {
                checkedOnce = true
                val liveResult = withContext(Dispatchers.IO) { NetworkBroadcastController.status(context) }
                liveResult.onSuccess { raw ->
                    val parsed = runCatching { JSONObject(raw) }.getOrNull()
                    val state = parsed?.optString("state").orEmpty()
                    if (state == "ACTIVE") running = true
                    else if (state == "READY" || state == "IDLE" || state == "ERROR") running = false
                    if (state == "ERROR") message = BroadcastUiMessages.operationResult(state, false)
                }
            }
            delay(2_500)
        }
    }

    fun showFailure() {
        // Do not display exception messages, stack traces, remote JSON detail,
        // paths, commands, or connection data on the customer-facing screen.
        message = BroadcastUiMessages.failure(
            NetworkBroadcastController.isServerReady(context),
            WirelessDebuggingManager.checkState(context) == WirelessDebuggingManager.DebuggingState.READY,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "Kun cloak",
                title = tr("بث شبكة", "Network broadcast"),
                subtitle = tr("اضغط Start لتشغيل نقطة الاتصال. يُستخدم ADB لإقلاع الخدمة فقط، ثم تعمل بشكل مستقل.", "Press Start to turn on the hotspot. ADB is only used to start the service; it then runs independently."),
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = KunPalette.PrimaryDeep),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan)))
                        .padding(vertical = 28.dp, horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Box(
                        Modifier.size(152.dp).clip(CircleShape)
                            .background(if (running) Color(0xFF2F8CFF) else Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            painter = painterResource(R.drawable.ic_nav_broadcast),
                            contentDescription = tr("بث شبكة", "Network broadcast"),
                            modifier = Modifier.size(86.dp),
                            tint = Color.White,
                        )
                    }
                    Text(
                        if (running) tr("البث نشط", "Broadcast active") else tr("البث متوقف", "Broadcast stopped"),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (running) tr("نقطة الاتصال مربوطة بالبروكسي", "The hotspot is connected to the proxy") else tr("جهّز الاتصال ثم اضغط Start", "Set up the connection, then press Start"),
                        color = Color.White.copy(alpha = 0.86f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text(tr("جاهزية البث", "Broadcast readiness"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    StatusRow("Wireless ADB", adbReady, broadcastAdbStateLabel(adbState))
                    StatusRow(tr("خدمة البث", "Broadcast service"), serverReady, if (serverReady) tr("جاهزة", "Ready") else tr("غير مشغّلة", "Not running"))
                    StatusRow(tr("اتصال VPN", "VPN connection"), vpnReady, if (vpnReady) tr("نشط", "Active") else tr("اختياري", "Optional"))
                    if (!adbReady && !serverReady) {
                        BroadcastInfoNote(tr("فعّل Wireless ADB وأكمل الاقتران من صفحة ADB. لا تحتاج صفحة البث إلى تثبيت Shizuku.", "Enable Wireless ADB and complete pairing on the ADB page. Network broadcast does not require Shizuku."), warning = true)
                        KUNSecondaryButton(tr("فتح صفحة ADB", "Open ADB page"), onGoToAdb, Modifier.fillMaxWidth())
                    } else if (!adbReady) {
                        BroadcastInfoNote(tr("خادم البث يعمل بشكل مستقل. انقطاع Wireless ADB بعد الإقلاع لا يتطلب إعادة الاقتران.", "The broadcast server runs independently. If Wireless ADB disconnects after startup, pairing again is not required."), warning = false)
                    }
                }
            }
        }
        item {
            KUNPrimaryButton(
                text = if (running) tr("إيقاف البث", "Stop broadcast") else "Start",
                onClick = {
                    val stopping = running
                    busy = true
                    message = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (stopping) NetworkBroadcastController.stop(context) else NetworkBroadcastController.start(context)
                        }
                        result.fold(
                            onSuccess = { raw ->
                                val state = runCatching { JSONObject(raw).optString("state") }.getOrDefault("")
                                if (state == "ACTIVE") running = true
                                else if (state == "READY" || state == "IDLE" || state == "ERROR") {
                                    running = false
                                }
                                message = BroadcastUiMessages.operationResult(state, stopping)
                            },
                            onFailure = { showFailure() },
                        )
                        busy = false
                    }
                },
                enabled = adbReady || serverReady || running,
                loading = busy,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        message?.let { text ->
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(text, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, ready: Boolean, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(if (ready) KunPalette.Success else KunPalette.Warning, CircleShape))
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = if (ready) KunPalette.Success else KunPalette.Warning, style = MaterialTheme.typography.labelLarge)
    }
}

private fun broadcastAdbStateLabel(state: WirelessDebuggingManager.DebuggingState): String = when (state) {
    WirelessDebuggingManager.DebuggingState.READY -> tr("جاهز", "Ready")
    WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> tr("مقترن لكن غير متصل", "Paired but not connected")
    WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> tr("غير مقترن", "Not paired")
    WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> tr("غير مفعّل", "Disabled")
    WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> tr("خيارات المطوّر غير مفعّلة", "Developer options are disabled")
    WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> tr("يتطلب Android 11+", "Requires Android 11+")
}

@Composable
private fun BroadcastInfoNote(text: String, warning: Boolean) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (warning) KunPalette.WarningSoft else KunPalette.SurfaceSoft)
            .padding(13.dp),
        color = if (warning) KunPalette.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}
