package com.proxyplatform.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.proxyplatform.app.adb.PairingCodeInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AdbScreen(vm: AppViewModel, padding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pairingCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPairingNotificationOnGrant by remember { mutableStateOf(false) }

    fun refreshState() {
        scope.launch {
            busy = true
            vm.updateWirelessState(withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) })
            busy = false
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (showPairingNotificationOnGrant && granted) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                !WirelessDebuggingManager.showPairingNotification(context)
            ) {
                message = "إشعارات التطبيق متوقفة؛ يمكنك إدخال رمز الاقتران يدويًا هنا."
            } else {
                message = "افتح إشعار KUN Proxy لإدخال رمز الاقتران عند ظهوره."
            }
        } else if (showPairingNotificationOnGrant) {
            message = "لم يُمنح إذن الإشعارات؛ أدخل رمز الاقتران يدويًا في الحقل أدناه."
        }
        showPairingNotificationOnGrant = false
    }

    fun showPairingNotification() {
        val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                message = "إشعارات التطبيق متوقفة من إعدادات Android؛ استخدم إدخال الرمز اليدوي."
            } else if (WirelessDebuggingManager.showPairingNotification(context)) {
                message = "افتح إشعار KUN Proxy لإدخال رمز الاقتران عند ظهوره."
            } else {
                message = "تعذر عرض إشعار الاقتران؛ أدخل الرمز يدويًا في الحقل أدناه."
            }
        } else {
            showPairingNotificationOnGrant = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun openDeveloperSettings() {
        val intent = if (vm.wirelessState == WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED) {
            Intent(Settings.ACTION_SETTINGS)
        } else {
            WirelessDebuggingManager.getDebuggingSettingsIntent(context)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { message = "تعذر فتح إعدادات المطوّر: ${it.message ?: it.javaClass.simpleName}" }
    }

    val state = vm.wirelessState
    val ready = state == WirelessDebuggingManager.DebuggingState.READY
    val stateTitle = adbStateLabel(state)
    val stateColor = if (ready) KunPalette.Success else KunPalette.Warning

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = KunPalette.PrimaryDeep),
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan)))
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) { Text("ADB", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("إعداد الاتصال", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelLarge)
                            Text("Wireless ADB", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Text(
                        "إعداد اتصال ADB اللاسلكي للأدوات التي تحتاجه.",
                        color = Color.White.copy(alpha = 0.92f),
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
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(12.dp).background(stateColor, CircleShape))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("حالة الاتصال", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stateTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = stateColor)
                    }
                    TextButton(onClick = ::refreshState, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        else Text("تحديث")
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, KunPalette.Border),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (ready) "الاتصال جاهز" else "خطوات الإعداد", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    when (state) {
                        WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> {
                            AdbInfoNote("يتطلب Wireless ADB نظام Android 11 أو أحدث. يمكنك استخدام وضع VPN بدلًا منه.", warning = true)
                        }
                        WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> {
                            AdbStep("1", "فعّل خيارات المطوّر", "من الإعدادات > حول الهاتف، اضغط رقم الإصدار عدة مرات حتى تظهر رسالة تفعيل خيارات المطوّر.")
                            KUNSecondaryButton("فتح إعدادات الهاتف", ::openDeveloperSettings)
                        }
                        WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> {
                            AdbStep("1", "فعّل التصحيح اللاسلكي", "من خيارات المطوّر، فعّل Wireless debugging وأبقِ الهاتف على شبكة Wi‑Fi.")
                            KUNSecondaryButton("فتح إعدادات التصحيح اللاسلكي", ::openDeveloperSettings)
                        }
                        WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> {
                            AdbStep("1", "افتح شاشة الاقتران", "من Wireless debugging اختر Pair device with pairing code.")
                            KUNSecondaryButton("فتح إعدادات التصحيح اللاسلكي", ::openDeveloperSettings)
                            KUNSecondaryButton("إظهار إشعار إدخال الرمز", ::showPairingNotification)
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { pairingCode = PairingCodeInput.digitsOnly(it).take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("رمز الاقتران — 6 أرقام") },
                                supportingText = { Text("الرمز مؤقت؛ لا تشاركه مع أي شخص.") },
                                singleLine = true,
                                enabled = !busy,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(15.dp),
                            )
                            KUNPrimaryButton(
                                text = "اقتران واتصال",
                                onClick = {
                                    val code = PairingCodeInput.normalize(pairingCode) ?: return@KUNPrimaryButton
                                    busy = true
                                    message = null
                                    scope.launch {
                                        pairingCode = ""
                                        val result = withContext(Dispatchers.IO) { WirelessDebuggingManager.pair(context, code) }
                                        val latest = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
                                        vm.updateWirelessState(latest)
                                        message = result.fold(
                                            { if (latest == WirelessDebuggingManager.DebuggingState.READY) "نجح الاقتران" else "فشل: لم يكتمل اتصال ADB بعد." },
                                            { ProxyFailureMessages.pairing(it.message) },
                                        )
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !busy && PairingCodeInput.normalize(pairingCode) != null,
                                loading = busy,
                            )
                        }
                        WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> {
                            AdbStep("1", "الاقتران محفوظ", "يبدو أن الاتصال انقطع. تأكد من بقاء Wireless debugging مفعّلًا وعلى الشبكة نفسها.")
                            KUNPrimaryButton(
                                text = "إعادة الاتصال",
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val connected = withContext(Dispatchers.IO) { WirelessDebuggingManager.connect(context, 0) }
                                        val latest = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
                                        vm.updateWirelessState(latest)
                                        message = if (connected && latest == WirelessDebuggingManager.DebuggingState.READY) "نجح الاتصال" else "فشل: تحقق من اتصال Wi‑Fi وإعداد التصحيح اللاسلكي."
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !busy,
                                loading = busy,
                            )
                            KUNSecondaryButton("فتح إعدادات التصحيح اللاسلكي", ::openDeveloperSettings, enabled = !busy)
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { pairingCode = PairingCodeInput.digitsOnly(it).take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("رمز جديد لإعادة الاقتران (اختياري)") },
                                singleLine = true,
                                enabled = !busy,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(15.dp),
                            )
                            if (PairingCodeInput.normalize(pairingCode) != null) {
                                KUNSecondaryButton(
                                    text = "إعادة الاقتران بهذا الرمز",
                                    onClick = {
                                        val code = PairingCodeInput.normalize(pairingCode) ?: return@KUNSecondaryButton
                                        busy = true
                                        scope.launch {
                                            pairingCode = ""
                                            val result = withContext(Dispatchers.IO) { WirelessDebuggingManager.pair(context, code) }
                                            val latest = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
                                            vm.updateWirelessState(latest)
                                            message = result.fold({ "تم إنشاء الاقتران والاتصال." }, { ProxyFailureMessages.pairing(it.message) })
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                )
                            }
                        }
                        WirelessDebuggingManager.DebuggingState.READY -> {
                            AdbInfoNote("الاتصال جاهز. لا حاجة إلى إدخال عنوان IP أو منفذ.")
                        }
                    }
                    message?.let { text ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (text.startsWith("نجح") || text.startsWith("تمت") || text.startsWith("تم ")) KunPalette.SuccessSoft else KunPalette.ErrorSoft,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(text.lineSequence().firstOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                                if (text.contains('\n') || text.length > 100) {
                                    ExpandableDetails("عرض التفاصيل") {
                                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            ExpandableDetails("معلومات الأمان واستخدام الاتصال") {
                Text(
                    "رمز الاقتران مؤقت ولا يُحفَظ في سجل التطبيق. الاتصال نفسه متاح للبروكسي المتقدم وSIM. ألغِ اقتران التطبيق من إعدادات Android عند التوقف عن استخدامه.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
}

@Composable
internal fun AdbRequiredPanel(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    onGoToAdb: () -> Unit,
) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PageHeading("يلزم الاتصال", title, description)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, KunPalette.Border),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeaturePill("ADB غير جاهز", color = KunPalette.Warning, container = KunPalette.WarningSoft)
                Text("فعّل Wireless ADB أولًا", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("أكمل الإعداد أو أعد الاتصال من صفحة ADB.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KUNPrimaryButton("الانتقال إلى صفحة ADB", onGoToAdb)
            }
        }
    }
}

@Composable
private fun AdbStep(number: String, title: String, description: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(number, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdbInfoNote(text: String, warning: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = if (warning) KunPalette.WarningSoft else KunPalette.SuccessSoft,
    ) {
        Text(
            text,
            Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = if (warning) KunPalette.Ink else KunPalette.Success,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

internal fun adbStateLabel(state: WirelessDebuggingManager.DebuggingState): String = when (state) {
    WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> "يلزم Android 11 أو أحدث"
    WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> "خيارات المطوّر غير مفعّلة"
    WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> "التصحيح اللاسلكي غير مفعّل"
    WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> "غير مقترن"
    WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> "مقترن لكن الاتصال غير نشط"
    WirelessDebuggingManager.DebuggingState.READY -> "متصل وجاهز"
}
