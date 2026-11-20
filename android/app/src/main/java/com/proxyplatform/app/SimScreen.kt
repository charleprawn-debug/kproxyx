package com.proxyplatform.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SIM_UI_PREFS = "sim_nrfr_ui"
private const val PREF_SELECTED_SUB_ID = "selected_sub_id"
private const val PREF_COUNTRY_CODE = "country_code"
private const val PREF_CUSTOM_COUNTRY_CODE = "custom_country_code"
private const val PREF_IS_CUSTOM_COUNTRY = "is_custom_country"
private const val PREF_CARRIER_ID = "carrier_id"
private const val PREF_CUSTOM_CARRIER = "custom_carrier"

@Composable
internal fun SimScreen(padding: PaddingValues, adbReady: Boolean, onGoToAdb: () -> Unit) {
    val context = LocalContext.current
    val controller = remember(context) { SimOperatorController(context) }
    val uiPrefs = remember(context) { context.getSharedPreferences(SIM_UI_PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    var simCards by remember { mutableStateOf(controller.readSimCards()) }
    var selectedSubId by remember { mutableIntStateOf(uiPrefs.getInt(PREF_SELECTED_SUB_ID, -1)) }
    var selectedCountryCode by remember { mutableStateOf(uiPrefs.getString(PREF_COUNTRY_CODE, "").orEmpty()) }
    var customCountryCode by remember { mutableStateOf(uiPrefs.getString(PREF_CUSTOM_COUNTRY_CODE, "").orEmpty()) }
    var isCustomCountryCode by remember { mutableStateOf(uiPrefs.getBoolean(PREF_IS_CUSTOM_COUNTRY, false)) }
    var selectedCarrierId by remember { mutableStateOf(uiPrefs.getString(PREF_CARRIER_ID, "").orEmpty()) }
    var customCarrierName by remember { mutableStateOf(uiPrefs.getString(PREF_CUSTOM_CARRIER, "").orEmpty()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var carrierMenuExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    val connected = adbReady
    val selectedSim = simCards.firstOrNull { it.subscriptionId == selectedSubId } ?: simCards.firstOrNull()
    val selectedCarrier = SimNrfrPresets.carriers.firstOrNull { it.id == selectedCarrierId }

    fun persistUiState() {
        uiPrefs.edit()
            .putInt(PREF_SELECTED_SUB_ID, selectedSubId)
            .putString(PREF_COUNTRY_CODE, selectedCountryCode)
            .putString(PREF_CUSTOM_COUNTRY_CODE, customCountryCode)
            .putBoolean(PREF_IS_CUSTOM_COUNTRY, isCustomCountryCode)
            .putString(PREF_CARRIER_ID, selectedCarrierId)
            .putString(PREF_CUSTOM_CARRIER, customCarrierName)
            .apply()
    }

    suspend fun queueSimRefresh(): Boolean {
        val result = withContext(Dispatchers.IO) { controller.refreshSimCards() }
        result.onSuccess {
            simCards = controller.readSimCards()
            message = tr("تم التحديث", "Updated")
        }.onFailure {
            message = tr("فشل: ${it.message ?: it.javaClass.simpleName}", "Failed: ${it.message ?: it.javaClass.simpleName}")
        }
        return result.isSuccess
    }

    fun refreshSimData() {
        scope.launch {
            busy = true
            simCards = controller.readSimCards()
            if (adbReady && !controller.hasSimCache && !controller.operationPending) {
                queueSimRefresh()
            }
            controller.consumeOperationMessage()?.let { message = it }
            busy = false
        }
    }

    fun waitForOperationResult() {
        scope.launch {
            controller.consumeOperationMessage()?.let {
                message = it
                simCards = controller.readSimCards()
                return@launch
            }
            repeat(60) {
                delay(500)
                controller.consumeOperationMessage()?.let {
                    message = it
                    simCards = controller.readSimCards()
                    return@launch
                }
            }
        }
    }

    fun clearSelections() {
        selectedCountryCode = ""
        customCountryCode = ""
        isCustomCountryCode = false
        selectedCarrierId = ""
        customCarrierName = ""
        persistUiState()
    }

    LaunchedEffect(Unit) {
        simCards = controller.readSimCards()
        if (selectedSubId !in simCards.map { it.subscriptionId }) {
            selectedSubId = simCards.firstOrNull()?.subscriptionId ?: -1
            persistUiState()
        }
        controller.consumeOperationMessage()?.let { message = it }
        if (controller.operationPending) message = tr("جارٍ التنفيذ…", "In progress…")
        refreshSimData()
    }

    if (!adbReady) {
        AdbRequiredPanel(
            modifier = Modifier.fillMaxSize().padding(padding),
            title = tr("إعدادات الشريحة", "SIM settings"),
            description = tr("تستخدم هذه الصفحة اتصال Wireless ADB المشترك لتنفيذ عمليات SIM بأمان.", "This page uses the shared Wireless ADB connection to perform SIM operations safely."),
            onGoToAdb = onGoToAdb,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tr("إعدادات الشريحة", "SIM settings"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(
                    tr("اختر الدولة واسم المشغّل ثم طبّق الإعدادات بسهولة.", "Choose a country and carrier, then apply the settings."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(tr("اختيار SIM", "Select SIM"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    queueSimRefresh()
                                    busy = false
                                    waitForOperationResult()
                                }
                            },
                            enabled = connected && !busy && !controller.operationPending,
                        ) { Text(tr("تحديث الشرائح", "Refresh SIMs")) }
                    }
                    if (simCards.isEmpty()) {
                        Text(tr("لم يتم العثور على شريحة. حدّث القائمة.", "No SIM found. Refresh the list."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        simCards.forEach { sim ->
                            FilterChip(
                                selected = selectedSubId == sim.subscriptionId,
                                onClick = {
                                    selectedSubId = sim.subscriptionId
                                    persistUiState()
                                },
                                enabled = !busy && !controller.operationPending,
                                label = { Text("SIM ${sim.slot} • ${sim.carrierName.ifBlank { tr("مشغّل غير معروف", "Unknown carrier") }}") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        selectedSim?.let { sim ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(tr("الإعداد الحالي — SIM ${sim.slot}", "Current settings — SIM ${sim.slot}"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (sim.currentConfig.isEmpty()) {
                            Text(tr("لا يوجد override ظاهر حالياً.", "No override is currently shown."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(tr("يوجد إعداد مخصص لهذه الشريحة.", "This SIM has a custom configuration."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ExpandableDetails(tr("عرض تفاصيل الإعداد الحالي", "Show current configuration details")) {
                                sim.currentConfig.forEach { (key, value) -> Text("$key: $value", style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("رمز البلد", "Country code"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { countryMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), enabled = !controller.operationPending) {
                            val countryLabel = if (isCustomCountryCode) tr("مخصص", "Custom") else SimNrfrPresets.countries.firstOrNull { it.code == selectedCountryCode }?.let { "${it.label} (${it.code})" } ?: tr("اختر دولة", "Select a country")
                            Text(countryLabel)
                        }
                        DropdownMenu(
                            expanded = countryMenuExpanded,
                            onDismissRequest = { countryMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 460.dp),
                        ) {
                            SimNrfrPresets.countries.forEach { country ->
                                DropdownMenuItem(
                                    text = { Text("${country.label} (${country.code})") },
                                    onClick = {
                                        selectedCountryCode = country.code
                                        isCustomCountryCode = false
                                        countryMenuExpanded = false
                                        message = null
                                        persistUiState()
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(tr("مخصص", "Custom")) },
                                onClick = {
                                    isCustomCountryCode = true
                                    countryMenuExpanded = false
                                    persistUiState()
                                },
                            )
                        }
                    }
                    if (isCustomCountryCode) {
                        OutlinedTextField(
                            value = customCountryCode,
                            onValueChange = {
                                if (it.length <= 2 && it.all(Char::isLetter)) {
                                    customCountryCode = it.uppercase()
                                    selectedCountryCode = customCountryCode
                                    persistUiState()
                                }
                            },
                            label = { Text(tr("رمز ISO مخصص — حرفان", "Custom ISO code — 2 letters")) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            enabled = !controller.operationPending,
                        )
                    }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("اسم المشغّل", "Carrier name"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { carrierMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), enabled = !controller.operationPending) {
                            Text(if (selectedCarrier?.custom == true) tr("مخصص", "Custom") else selectedCarrier?.label ?: tr("اختر مشغّلاً", "Select a carrier"))
                        }
                        DropdownMenu(
                            expanded = carrierMenuExpanded,
                            onDismissRequest = { carrierMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 460.dp),
                        ) {
                            SimNrfrPresets.carriers.filterNot { it.custom }.groupBy { it.region }.forEach { (region, carriers) ->
                                val regionLabel = SimNrfrPresets.countries.firstOrNull { it.code == region }?.label ?: region
                                Text(regionLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
                                carriers.forEach { carrier ->
                                    DropdownMenuItem(
                                        text = { Text(carrier.label) },
                                        onClick = {
                                            selectedCarrierId = carrier.id
                                            customCarrierName = carrier.displayName
                                            carrierMenuExpanded = false
                                            message = null
                                            persistUiState()
                                        },
                                    )
                                }
                            }
                            DropdownMenuItem(
                                text = { Text(tr("مخصص", "Custom")) },
                                onClick = {
                                    selectedCarrierId = SimNrfrPresets.carriers.first { it.custom }.id
                                    carrierMenuExpanded = false
                                    persistUiState()
                                },
                            )
                        }
                    }
                    if (selectedCarrier?.custom == true) {
                        OutlinedTextField(
                            value = customCarrierName,
                            onValueChange = {
                                customCarrierName = it
                                persistUiState()
                            },
                            label = { Text(tr("اسم المشغّل المخصص", "Custom carrier name")) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !controller.operationPending,
                        )
                    }
                }
            }
        }
        if (controller.operationPending) {
            item {
                NoticeCard(
                    title = tr("جاري التطبيق", "Applying settings"),
                    body = tr("يتم تطبيق الإعدادات الآن. يرجى الانتظار.", "Settings are being applied. Please wait."),
                    color = KunPalette.WarningSoft,
                    textColor = KunPalette.Ink,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                KUNSecondaryButton(
                    text = tr("مسح الإعدادات", "Clear"),
                    onClick = { showResetConfirmation = true },
                    enabled = selectedSim != null && connected && !busy && !controller.operationPending,
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                KUNPrimaryButton(
                    text = tr("تطبيق الإعدادات", "Apply"),
                    loading = busy,
                    onClick = {
                        val sim = selectedSim ?: return@KUNPrimaryButton
                        val country = if (isCustomCountryCode) customCountryCode.takeIf { it.length == 2 } else selectedCountryCode.takeIf { it.isNotEmpty() }
                        val carrierName = when {
                            selectedCarrier?.custom == true -> customCarrierName.takeIf { it.isNotEmpty() }
                            selectedCarrier != null -> selectedCarrier.displayName
                            else -> null
                        }
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { controller.saveCarrierConfig(sim, country, carrierName) }
                            message = if (result.isSuccess) tr("جارٍ التنفيذ…", "In progress…") else controller.consumeOperationMessage() ?: run {
                                val detail = result.exceptionOrNull()?.message ?: tr("تعذر تنفيذ العملية.", "Unable to complete the operation.")
                                tr("فشل: $detail", "Failed: $detail")
                            }
                            busy = false
                            if (result.isSuccess) waitForOperationResult()
                        }
                    },
                    enabled = selectedSim != null && connected && !busy && !controller.operationPending && (
                        (isCustomCountryCode && customCountryCode.length == 2) ||
                            (!isCustomCountryCode && selectedCountryCode.isNotEmpty()) ||
                            (selectedCarrier != null && (!selectedCarrier.custom || customCarrierName.isNotEmpty()))
                        ),
                    modifier = Modifier.weight(1f).height(52.dp),
                )
            }
        }
        message?.let { text -> item { InlineMessage(text) } }
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text(tr("تأكيد مسح الإعدادات", "Confirm clear settings")) },
            text = { Text(tr("سيُحذف إعداد CarrierConfig المخصص لهذه الشريحة. هل تريد المتابعة؟", "The custom CarrierConfig for this SIM will be removed. Continue?")) },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        showResetConfirmation = false
                        val sim = selectedSim ?: return@TextButton
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { controller.resetCarrierConfig(sim) }
                            if (result.isSuccess) clearSelections()
                            message = if (result.isSuccess) tr("جارٍ التنفيذ…", "In progress…") else controller.consumeOperationMessage() ?: run {
                                val detail = result.exceptionOrNull()?.message ?: tr("تعذر تنفيذ العملية.", "Unable to complete the operation.")
                                tr("فشل: $detail", "Failed: $detail")
                            }
                            busy = false
                            if (result.isSuccess) waitForOperationResult()
                        }
                    },
                ) { Text(tr("مسح", "Clear"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
}
@Composable
private fun NoticeCard(title: String, body: String, color: Color, textColor: Color) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = textColor)
            Text(body, style = MaterialTheme.typography.bodySmall, color = textColor)
        }
    }
}

@Composable
private fun InlineMessage(message: String) {
    val success = message.startsWith("نجح") || message.startsWith("تم التحديث") ||
        message.startsWith("Succeeded") || message.startsWith("Updated")
    val failure = message.startsWith("فشل") || message.startsWith("تعذر") || message.startsWith("Failed:")
    val container = when {
        success -> KunPalette.SuccessSoft
        failure -> KunPalette.ErrorSoft
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val foreground = when {
        success -> KunPalette.Success
        failure -> KunPalette.Error
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = container) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start, color = foreground, fontWeight = FontWeight.SemiBold)
    }
}
