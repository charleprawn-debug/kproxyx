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
    var shizukuStatus by remember { mutableStateOf(ShizukuGate.status()) }
    var running by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val shizukuReady = shizukuStatus is ShizukuStatus.Ready
    val vpnReady = ProxyVpnService.isRunning(context)

    LaunchedEffect(Unit) {
        while (true) {
            shizukuStatus = ShizukuGate.status()
            if (running && shizukuReady) {
                val live = withContext(Dispatchers.IO) { NetworkBroadcastController.status().getOrNull() }
                if (live.isNullOrBlank()) running = false
            }
            delay(2_500)
        }
    }

    fun showFailure(failure: Throwable?) {
        message = failure?.message?.takeIf { it.isNotBlank() }
            ?: "تعذر تشغيل جلسة بث الشبكة. تحقق من Shizuku وADB ثم أعد المحاولة."
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading(
                eyebrow = "Kun cloak",
                title = "بث شبكة",
                subtitle = "اضغط Start لتشغيل نقطة الاتصال وربطها بالبروكسي تلقائيًا عبر جلسة Shizuku المحمية.",
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
                            contentDescription = "بث شبكة",
                            modifier = Modifier.size(86.dp),
                            tint = Color.White,
                        )
                    }
                    Text(
                        if (running) "البث نشط" else "البث متوقف",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        if (running) "نقطة الاتصال مربوطة بالبروكسي عبر Shizuku" else "فعّل المتطلبات ثم اضغط Start",
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
                    Text("جاهزية البث", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    StatusRow("Shizuku", shizukuReady, shizukuStatusLabel(shizukuStatus))
                    StatusRow("اتصال VPN", vpnReady, if (vpnReady) "نشط" else "شغّل VPN أولًا")
                    if (!shizukuReady) {
                        BroadcastInfoNote("شغّل Shizuku عبر USB أو Root ثم امنح Kun cloak الصلاحية. لا يحتاج بث الشبكة إلى Wireless ADB.", warning = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            KUNSecondaryButton("فتح Shizuku", { ShizukuGate.openDownloadPage(context) }, Modifier.weight(1f))
                            if (shizukuStatus is ShizukuStatus.PermissionRequired) {
                                KUNSecondaryButton("منح الصلاحية", { ShizukuGate.requestPermission() }, Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
        item {
            KUNPrimaryButton(
                text = if (running) "إيقاف البث" else "Start",
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (running) NetworkBroadcastController.stop() else NetworkBroadcastController.start()
                        }
                        result.fold(
                            onSuccess = { raw ->
                                val state = runCatching { JSONObject(raw).optString("state") }.getOrDefault("")
                                val detail = runCatching { JSONObject(raw).optString("detail") }
                                    .getOrDefault(raw)
                                    .ifBlank { raw }
                                if (state == "ACTIVE") {
                                    running = true
                                    message = "تم تشغيل جلسة Shizze الأصلية وربط downstream."
                                } else if (!running) {
                                    running = false
                                    message = "لم تبدأ جلسة Shizze: $detail"
                                } else {
                                    running = false
                                    message = "تم إيقاف البث: $detail"
                                }
                            },
                            onFailure = ::showFailure,
                        )
                        busy = false
                    }
                },
                enabled = shizukuReady && vpnReady,
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

private fun shizukuStatusLabel(status: ShizukuStatus): String = when (status) {
    ShizukuStatus.NotRunning -> "غير جاهز"
    ShizukuStatus.PermissionRequired -> "تحتاج صلاحية"
    is ShizukuStatus.Ready -> "جاهز"
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
