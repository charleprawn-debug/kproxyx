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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.proxyplatform.app.adb.PairingCodeInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AdbScreen(vm: AppViewModel, padding: PaddingValues, recoveryMode: Boolean = false, onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pairingCode by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showPairingNotificationOnGrant by remember { mutableStateOf(false) }
    var resetBusy by remember { mutableStateOf(false) }

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
                message = tr("إشعارات التطبيق متوقفة؛ يمكنك إدخال رمز الاقتران يدويًا هنا.", "App notifications are disabled. You can enter the pairing code manually here.")
            } else {
                message = tr("افتح إشعار Kun cloak لإدخال رمز الاقتران عند ظهوره.", "Open the Kun cloak notification to enter the pairing code when it appears.")
            }
        } else if (showPairingNotificationOnGrant) {
            message = tr("لم يُمنح إذن الإشعارات؛ أدخل رمز الاقتران يدويًا في الحقل أدناه.", "Notification permission was not granted. Enter the pairing code manually below.")
        }
        showPairingNotificationOnGrant = false
    }

    fun showPairingNotification() {
        val hasPermission = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                message = tr("إشعارات التطبيق متوقفة من إعدادات Android؛ استخدم إدخال الرمز اليدوي.", "App notifications are disabled in Android settings. Enter the code manually.")
            } else if (WirelessDebuggingManager.showPairingNotification(context)) {
                message = tr("افتح إشعار Kun cloak لإدخال رمز الاقتران عند ظهوره.", "Open the Kun cloak notification to enter the pairing code when it appears.")
            } else {
                message = tr("تعذر عرض إشعار الاقتران؛ أدخل الرمز يدويًا في الحقل أدناه.", "Unable to show the pairing notification. Enter the code manually below.")
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
            .onFailure { message = tr("تعذر فتح إعدادات المطوّر: ${it.message ?: it.javaClass.simpleName}", "Unable to open developer settings: ${it.message ?: it.javaClass.simpleName}") }
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
        if (recoveryMode) item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(tr("إصلاح اتصال الهاتف", "Fix phone connection"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onBack) { Text(tr("العودة", "Back")) }
            }
        }
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
                            Text(tr("إعداد الاتصال", "Connection setup"), color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelLarge)
                            Text("Wireless ADB", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Text(
                        tr("إعداد اتصال ADB اللاسلكي للأدوات التي تحتاجه.", "Set up a wireless ADB connection for tools that require it."),
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
                        Text(tr("حالة الاتصال", "Connection status"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stateTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = stateColor)
                    }
                    TextButton(onClick = ::refreshState, enabled = !busy) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                        else Text(tr("تحديث", "Refresh"))
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
                    Text(if (ready) tr("الاتصال جاهز", "Connection ready") else tr("خطوات الإعداد", "Setup steps"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    when (state) {
                        WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> {
                            AdbInfoNote(tr("يتطلب Wireless ADB نظام Android 11 أو أحدث. يمكنك استخدام وضع VPN بدلًا منه.", "Wireless ADB requires Android 11 or later. You can use VPN mode instead."), warning = true)
                        }
                        WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> {
                            AdbStep("1", tr("فعّل خيارات المطوّر", "Enable Developer options"), tr("من الإعدادات > حول الهاتف، اضغط رقم الإصدار عدة مرات حتى تظهر رسالة تفعيل خيارات المطوّر.", "In Settings > About phone, tap Build number several times until Developer options are enabled."))
                            KUNSecondaryButton(tr("فتح إعدادات الهاتف", "Open phone settings"), ::openDeveloperSettings)
                        }
                        WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> {
                            AdbStep("1", tr("فعّل التصحيح اللاسلكي", "Enable wireless debugging"), tr("من خيارات المطوّر، فعّل Wireless debugging وأبقِ الهاتف على شبكة Wi‑Fi.", "In Developer options, enable Wireless debugging and keep the phone connected to Wi‑Fi."))
                            KUNSecondaryButton(tr("فتح إعدادات التصحيح اللاسلكي", "Open wireless debugging settings"), ::openDeveloperSettings)
                        }
                        WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> {
                            AdbStep("1", tr("افتح شاشة الاقتران", "Open the pairing screen"), tr("من Wireless debugging اختر Pair device with pairing code.", "In Wireless debugging, select Pair device with pairing code."))
                            KUNSecondaryButton(tr("فتح إعدادات التصحيح اللاسلكي", "Open wireless debugging settings"), ::openDeveloperSettings)
                            KUNSecondaryButton(tr("إظهار إشعار إدخال الرمز", "Show pairing code notification"), ::showPairingNotification)
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { pairingCode = PairingCodeInput.digitsOnly(it).take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(tr("رمز الاقتران — 6 أرقام", "Pairing code — 6 digits")) },
                                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                                supportingText = { Text(tr("الرمز مؤقت؛ لا تشاركه مع أي شخص.", "The code is temporary. Do not share it with anyone.")) },
                                singleLine = true,
                                enabled = !busy,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(15.dp),
                            )
                            KUNPrimaryButton(
                                text = tr("اقتران واتصال", "Pair and connect"),
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
                                            { if (latest == WirelessDebuggingManager.DebuggingState.READY) tr("نجح الاقتران", "Pairing successful") else tr("فشل: لم يكتمل اتصال ADB بعد.", "Failed: The ADB connection has not been established yet.") },
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
                            AdbStep("1", tr("الاقتران محفوظ", "Pairing saved"), tr("يبدو أن الاتصال انقطع. تأكد من بقاء Wireless debugging مفعّلًا وعلى الشبكة نفسها.", "The connection appears to have been lost. Make sure Wireless debugging remains enabled and the phone is on the same network."))
                            KUNPrimaryButton(
                                text = tr("إعادة الاتصال", "Reconnect"),
                                onClick = {
                                    busy = true
                                    message = null
                                    scope.launch {
                                        val connected = withContext(Dispatchers.IO) { WirelessDebuggingManager.connect(context, 0) }
                                        val latest = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
                                        vm.updateWirelessState(latest)
                                        message = if (connected && latest == WirelessDebuggingManager.DebuggingState.READY) tr("نجح الاتصال", "Connected successfully") else tr("فشل: تحقق من اتصال Wi‑Fi وإعداد التصحيح اللاسلكي.", "Failed: Check your Wi‑Fi connection and wireless debugging settings.")
                                        busy = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                enabled = !busy,
                                loading = busy,
                            )
                            KUNSecondaryButton(tr("فتح إعدادات التصحيح اللاسلكي", "Open wireless debugging settings"), ::openDeveloperSettings, enabled = !busy)
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { pairingCode = PairingCodeInput.digitsOnly(it).take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(tr("رمز جديد لإعادة الاقتران (اختياري)", "New pairing code (optional)")) },
                                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                                singleLine = true,
                                enabled = !busy,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(15.dp),
                            )
                            if (PairingCodeInput.normalize(pairingCode) != null) {
                                KUNSecondaryButton(
                                    text = tr("إعادة الاقتران بهذا الرمز", "Pair again with this code"),
                                    onClick = {
                                        val code = PairingCodeInput.normalize(pairingCode) ?: return@KUNSecondaryButton
                                        busy = true
                                        scope.launch {
                                            pairingCode = ""
                                            val result = withContext(Dispatchers.IO) { WirelessDebuggingManager.pair(context, code) }
                                            val latest = withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) }
                                            vm.updateWirelessState(latest)
                                            message = result.fold({ tr("تم إنشاء الاقتران والاتصال.", "Pairing and connection established.") }, { ProxyFailureMessages.pairing(it.message) })
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                )
                            }
                        }
                        WirelessDebuggingManager.DebuggingState.READY -> {
                            AdbInfoNote(tr("الاتصال جاهز. لا حاجة إلى إدخال عنوان IP أو منفذ.", "The connection is ready. No IP address or port is required."))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = KunPalette.SurfaceSoft,
                            ) {
                                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(tr("ميزات ADB المتاحة", "Available ADB features"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                    Text(
                                        tr("الأوامر القصيرة لـSIM والوضع المتقدم تعمل كما هي، وتتوفر أيضاً جلسات ADB مستمرة للميزات التي تحتاج خدمة تعمل في الخلفية مثل بث الشبكة.", "Short commands for SIM and Advanced mode work as before. Persistent ADB sessions are also available for features that need a background service, such as network broadcast."),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    message?.let { text ->
                        val success = text.startsWith("نجح") || text.startsWith("تمت") || text.startsWith("تم ") ||
                            text.startsWith("Pairing successful") || text.startsWith("Connected successfully") ||
                            text.startsWith("Pairing and connection established") || text.startsWith("Proxy settings cleared")
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (success) KunPalette.SuccessSoft else KunPalette.ErrorSoft,
                        ) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(text.lineSequence().firstOrNull().orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                                if (text.contains('\n') || text.length > 100) {
                                    ExpandableDetails(tr("عرض التفاصيل", "Show details")) {
                                        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (recoveryMode) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = KunPalette.WarningSoft),
                border = androidx.compose.foundation.BorderStroke(1.dp, KunPalette.Warning.copy(alpha = 0.35f)),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("إعادة ضبط إعدادات البروكسي", "Reset proxy settings"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (ready) tr("يزيل هذا الإجراء إعدادات بروكسي Android فقط ويعيد الاتصال المباشر. لا يحذف التطبيق ولا يلغي اقتران ADB ولا يغيّر اللغة أو المنطقة الزمنية.", "This only clears Android proxy settings and restores a direct connection. It does not uninstall the app, unpair ADB, or change the language or time zone.")
                        else tr("بعد إتمام اقتران ADB، يمكنك تنظيف إعدادات البروكسي العالقة من هنا بدون تسجيل دخول.", "After pairing ADB, you can clear stuck proxy settings here without signing in."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    KUNPrimaryButton(
                        text = tr("تنظيف إعدادات البروكسي", "Clear proxy settings"),
                        onClick = {
                            resetBusy = true
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) { AdvancedSystemProxy.clearAll(context) }
                                if (result.isSuccess) ProxyLocalService.stop(context)
                                message = result.fold(
                                    { tr("تم تنظيف إعدادات البروكسي وإعادة الاتصال المباشر.", "Proxy settings cleared and direct connection restored.") },
                                    {
                                        val detail = it.message ?: tr("أعد المحاولة بعد التأكد من اتصال ADB.", "Check the ADB connection and try again.")
                                        tr("تعذر التنظيف: $detail", "Unable to clear settings: $detail")
                                    },
                                )
                                resetBusy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = ready && !busy && !resetBusy,
                        loading = resetBusy,
                    )
                }
            }
        }
        item {
            ExpandableDetails(tr("معلومات الأمان واستخدام الاتصال", "Security and connection information")) {
                Text(
                    tr("رمز الاقتران مؤقت ولا يُحفَظ في سجل التطبيق. الاتصال نفسه متاح للبروكسي المتقدم وSIM. ألغِ اقتران التطبيق من إعدادات Android عند التوقف عن استخدامه.", "The pairing code is temporary and is not saved in the app log. The connection is available for Advanced proxy and SIM. Unpair the app in Android settings when you stop using it."),
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
        PageHeading(tr("يلزم الاتصال", "Connection required"), title, description)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, KunPalette.Border),
        ) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FeaturePill(tr("ADB غير جاهز", "ADB not ready"), color = KunPalette.Warning, container = KunPalette.WarningSoft)
                Text(tr("فعّل Wireless ADB أولًا", "Enable Wireless ADB first"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(tr("أكمل الإعداد أو أعد الاتصال من صفحة ADB.", "Complete setup or reconnect from the ADB page."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                KUNPrimaryButton(tr("الانتقال إلى صفحة ADB", "Go to ADB page"), onGoToAdb)
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
    WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> tr("يلزم Android 11 أو أحدث", "Android 11 or later required")
    WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> tr("خيارات المطوّر غير مفعّلة", "Developer options are disabled")
    WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> tr("التصحيح اللاسلكي غير مفعّل", "Wireless debugging is disabled")
    WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> tr("غير مقترن", "Not paired")
    WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> tr("مقترن لكن الاتصال غير نشط", "Paired but not connected")
    WirelessDebuggingManager.DebuggingState.READY -> tr("متصل وجاهز", "Connected and ready")
}
