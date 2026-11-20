package com.proxyplatform.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

@Composable
internal fun SavedProxyManagerCard(
    profiles: List<SavedProxy>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (SavedProxy) -> Unit,
    onSave: (SavedProxy) -> Unit,
    onDelete: (SavedProxy) -> Unit,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var profileName by remember { mutableStateOf("") }
    var editorProtocol by remember { mutableStateOf("socks5") }
    var editorHost by remember { mutableStateOf("") }
    var editorPort by remember { mutableStateOf("") }
    var editorAuth by remember { mutableStateOf(false) }
    var editorUsername by remember { mutableStateOf("") }
    var editorPassword by remember { mutableStateOf("") }
    var editorError by remember { mutableStateOf<(() -> String)?>(null) }
    var profileToDelete by remember { mutableStateOf<SavedProxy?>(null) }
    val selected = profiles.firstOrNull { it.id == selectedId }

    fun openAddEditor() {
        editingId = null
        profileName = ""
        editorProtocol = "socks5"
        editorHost = ""
        editorPort = ""
        editorAuth = false
        editorUsername = ""
        editorPassword = ""
        editorError = null
        editorOpen = true
    }

    fun openEditEditor(profile: SavedProxy) {
        editingId = profile.id
        profileName = profile.name
        editorProtocol = profile.protocol
        editorHost = profile.host
        editorPort = profile.port.toString()
        editorAuth = profile.authenticationRequired
        editorUsername = profile.username
        editorPassword = profile.password
        editorError = null
        editorOpen = true
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(23.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, KunPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(tr("بروكسياتي", "My proxies"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        tr("اختر اتصالًا محفوظًا ليعمل في وضع VPN أو الوضع المتقدم.", "Choose a saved connection to use in VPN or Advanced mode."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = ::openAddEditor,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text(tr("إضافة +", "+ Add"), fontWeight = FontWeight.SemiBold) }
            }

            if (profiles.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(17.dp),
                    border = BorderStroke(1.dp, KunPalette.Border),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(tr("لا توجد بروكسيات محفوظة", "No saved proxies"), fontWeight = FontWeight.Bold)
                        Text(
                            tr("أضف بيانات اتصال مرة واحدة، ثم اخترها واضغط تشغيل.", "Add connection details once, then select the proxy and press Start."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Button(
                    onClick = ::openAddEditor,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                ) { Text(tr("إضافة أول بروكسي", "Add your first proxy")) }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    profiles.forEach { profile ->
                        val isSelected = profile.id == selectedId
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) { onSelect(profile) },
                            shape = RoundedCornerShape(17.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                                else KunPalette.Border,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            profile.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                        )
                                        if (isSelected) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                    Text(
                                        "${profile.protocol.uppercase()}  •  ${profile.endpoint}",
                                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                    Text(
                                        if (profile.authenticationRequired) tr("مصادقة محفوظة بأمان", "Authentication securely saved") else tr("من دون مصادقة", "No authentication"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(
                                    onClick = { openEditEditor(profile) },
                                    enabled = enabled,
                                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 5.dp),
                                ) {
                                    Text(tr("✎ تعديل", "✎ Edit"), fontWeight = FontWeight.SemiBold, maxLines = 1)
                                }
                                TextButton(
                                    onClick = { profileToDelete = profile },
                                    enabled = enabled,
                                    contentPadding = PaddingValues(horizontal = 5.dp, vertical = 5.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                ) { Text(tr("حذف", "Delete"), maxLines = 1) }
                            }
                        }
                    }
                }
            }

            Text(
                tr("تُزامن ملفات الاتصال مع حسابك لتظهر على أجهزتك الأخرى، مع تشفير بيانات المصادقة محليًا وفي الخادم.", "Connection profiles sync with your account and appear on your other devices. Authentication details are encrypted locally and on the server."),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (editorOpen) {
        AlertDialog(
            onDismissRequest = { if (enabled) editorOpen = false },
            title = { Text(if (editingId == null) tr("إضافة بروكسي", "Add proxy") else tr("تعديل البروكسي", "Edit proxy")) },
            text = {
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(
                        tr("أدخل البيانات مرة واحدة؛ ستظهر بعد ذلك في القائمة دون الحاجة لإعادة كتابتها.", "Enter the details once; they will appear in the list without needing to be re-entered."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it.take(64); editorError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("اسم البروكسي", "Proxy name")) },
                        singleLine = true,
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                    )
                    Text(tr("نوع الاتصال", "Connection type"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = editorProtocol == "socks5",
                            onClick = { editorProtocol = "socks5" },
                            label = { Text("SOCKS5") },
                            enabled = enabled,
                        )
                        FilterChip(
                            selected = editorProtocol == "http",
                            onClick = { editorProtocol = "http" },
                            label = { Text("HTTP") },
                            enabled = enabled,
                        )
                    }
                    OutlinedTextField(
                        value = editorHost,
                        onValueChange = { editorHost = it.trim(); editorError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("عنوان الخادم أو IP", "Server address or IP")) },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        singleLine = true,
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = editorPort,
                        onValueChange = { editorPort = it.filter { digit -> digit in '0'..'9' }.take(5); editorError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("المنفذ", "Port")) },
                        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        singleLine = true,
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = editorAuth, onCheckedChange = { editorAuth = it; editorError = null }, enabled = enabled)
                        Text(tr("يتطلب اسم مستخدم وكلمة مرور", "Requires a username and password"), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (editorAuth) {
                        OutlinedTextField(
                            value = editorUsername,
                            onValueChange = { editorUsername = it; editorError = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("اسم المستخدم", "Username")) },
                            singleLine = true,
                            enabled = enabled,
                            shape = RoundedCornerShape(14.dp),
                        )
                        OutlinedTextField(
                            value = editorPassword,
                            onValueChange = { editorPassword = it; editorError = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("كلمة المرور", "Password")) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = enabled,
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                    if (editorError != null) {
                        Text(editorError?.invoke().orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedPort = editorPort.toIntOrNull()
                        editorError = when {
                            profileName.isBlank() -> { { tr("أدخل اسمًا يميّز هذا البروكسي.", "Enter a name to identify this proxy.") } }
                            editorHost.isBlank() -> { { tr("أدخل عنوان خادم البروكسي.", "Enter the proxy server address.") } }
                            parsedPort == null || parsedPort !in 1..65535 -> { { tr("أدخل منفذًا بين 1 و65535.", "Enter a port between 1 and 65535.") } }
                            editorAuth && editorUsername.isBlank() -> { { tr("أدخل اسم المستخدم المطلوب للمصادقة.", "Enter the username required for authentication.") } }
                            editorAuth && editorPassword.isEmpty() -> { { tr("أدخل كلمة المرور المطلوبة للمصادقة.", "Enter the password required for authentication.") } }
                            else -> null
                        }
                        if (editorError == null) {
                            onSave(
                                SavedProxy(
                                    id = editingId.orEmpty(),
                                    name = profileName.trim(),
                                    protocol = editorProtocol,
                                    host = editorHost.trim(),
                                    port = parsedPort!!,
                                    authenticationRequired = editorAuth,
                                    username = if (editorAuth) editorUsername else "",
                                    password = if (editorAuth) editorPassword else "",
                                ),
                            )
                            editorOpen = false
                        }
                    },
                    enabled = enabled,
                    shape = RoundedCornerShape(13.dp),
                ) { Text(tr("حفظ", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { editorOpen = false }, enabled = enabled) { Text(tr("إلغاء", "Cancel")) }
            },
        )
    }

    profileToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = { Text(tr("حذف البروكسي؟", "Delete proxy?")) },
            text = { Text(tr("سيُحذف «${profile.name}» وبيانات اعتماده المشفّرة من هذا الجهاز.", "“${profile.name}” and its encrypted credentials will be deleted from this device.")) },
            confirmButton = {
                Button(
                    onClick = { onDelete(profile); profileToDelete = null },
                    enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(13.dp),
                ) { Text(tr("حذف", "Delete")) }
            },
            dismissButton = { TextButton(onClick = { profileToDelete = null }) { Text(tr("إلغاء", "Cancel")) } },
        )
    }
}
