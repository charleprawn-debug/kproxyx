package com.proxyplatform.app

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun AuthScreen(vm: AppViewModel, onOpenRepair: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) { vm.loadSupportSettings() }
    var mode by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    val register = mode == "register"
    val changePassword = mode == "change"
    val title = when (mode) {
        "register" -> tr("حسابك يبدأ من هنا", "Your account starts here")
        "change" -> tr("تغيير كلمة المرور", "Change password")
        else -> tr("أهلًا بعودتك", "Welcome back")
    }
    val subtitle = when (mode) {
        "register" -> tr("أنشئ حسابًا لإدارة خدماتك وملفك الشخصي.", "Create an account to manage your services and profile.")
        "change" -> tr("أدخل البريد وكلمة المرور الجديدة للتحقق من هذا الهاتف.", "Enter your email and new password to verify this phone.")
        else -> tr("سجّل الدخول للمتابعة إلى Kun cloak.", "Sign in to continue to Kun cloak.")
    }

    Box(Modifier.fillMaxSize().background(KunPalette.Background)) {
        Box(
            Modifier.fillMaxWidth().height(285.dp)
                .background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan)))
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Box(Modifier.fillMaxWidth().widthIn(max = 480.dp).height(72.dp)) {
                Box(Modifier.align(Alignment.Center)) { BrandMark(58.dp) }
                Surface(
                    modifier = Modifier.align(Alignment.CenterEnd).size(38.dp).clip(CircleShape).clickable(onClick = onOpenRepair),
                    color = Color.White.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🔧", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Kun cloak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(tr("اتصالك، بإعدادك.", "Your connection, your way."), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
            Spacer(Modifier.height(30.dp))
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KunPalette.SurfaceSoft).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AuthModeButton(tr("تسجيل الدخول", "Sign in"), mode == "login", Modifier.weight(1f)) { mode = "login"; vm.clearError() }
                        AuthModeButton(tr("حساب جديد", "Create account"), mode == "register", Modifier.weight(1f)) { mode = "register"; vm.clearError() }
                        AuthModeButton(tr("تغيير كلمة المرور", "Change password"), mode == "change", Modifier.weight(1f)) { mode = "change"; vm.clearError() }
                    }
                    AnimatedContent(targetState = register && !changePassword) { showName ->
                        if (showName) OutlinedTextField(
                            value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("الاسم الكامل", "Full name")) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        ) else Spacer(Modifier.height(1.dp))
                    }
                    if (!changePassword) {
                        OutlinedTextField(
                            value = email, onValueChange = { email = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("البريد الإلكتروني", "Email address")) }, singleLine = true, shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        )
                        OutlinedTextField(
                            value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text(tr("كلمة المرور", "Password")) }, singleLine = true,
                            shape = RoundedCornerShape(16.dp), visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                        )
                    } else {
                        Surface(color = KunPalette.Primary.copy(alpha = 0.08f), shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(tr("تحتاج إلى تغيير كلمة المرور؟", "Need to change your password?"), fontWeight = FontWeight.Bold)
                                Text(tr("تواصل مع فريق الدعم للتحقق من ملكية الحساب ومساعدتك بأمان.", "Contact support to verify account ownership and get help securely."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    AnimatedVisibility(visible = vm.error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                        val success = isAuthSuccessMessage(vm.error)
                        Surface(
                            color = if (success) KunPalette.SuccessSoft else KunPalette.ErrorSoft,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(vm.error.orEmpty(), Modifier.fillMaxWidth().padding(12.dp),
                                color = if (success) KunPalette.Success else KunPalette.Error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    KUNPrimaryButton(
                        text = when (mode) { "register" -> tr("إنشاء الحساب", "Create account"); "change" -> tr("التواصل مع الدعم", "Contact support"); else -> tr("تسجيل الدخول", "Sign in") },
                        onClick = {
                            when (mode) {
                                "register" -> vm.register(email, password, name) { mode = "login" }
                                "change" -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/${vm.supportTelegramUsername}")))
                                else -> vm.login(email, password)
                            }
                        },
                        enabled = !vm.loading && (changePassword || (email.isNotBlank() && password.length >= 8 && (!register || name.isNotBlank()))),
                        modifier = Modifier.fillMaxWidth().height(56.dp), loading = vm.loading,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FeaturePill("VPN"); FeaturePill("HTTP"); FeaturePill("SOCKS5")
            }
        }
    }
}

private fun isAuthSuccessMessage(message: String?): Boolean {
    if (message == null) return false
    return message.startsWith("تم") || listOf(
        "Success", "Successfully", "Account created", "Account successfully created",
        "Your account has been created", "Your account was created",
        "Password changed", "Password successfully changed",
        "Your password has been changed", "Your password was changed",
    ).any { message.startsWith(it, ignoreCase = true) }
}

@Composable
private fun AuthModeButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(13.dp),
        color = if (selected) Color.White else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun MainShell(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var screen by remember { mutableStateOf(Screen.SUBSCRIPTIONS) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) vm.refreshAccountIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        vm.loadPlansAndWallet()
        vm.loadNotifications()
        vm.loadSupportMessages(reset = true)
        while (true) {
            vm.updateWirelessState(withContext(Dispatchers.IO) { WirelessDebuggingManager.checkState(context) })
            delay(3_000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.loadNotifications()
            vm.loadSupportMessages(reset = true)
        }
    }
    LaunchedEffect(screen) {
        AdvancedOperationLog.info(context, "App navigation: ${screen.name}.")
        vm.clearError()
        when (screen) {
            Screen.PROFILE -> {
                vm.loadProfile()
                vm.loadSupportMessages(reset = true)
            }
            Screen.SUBSCRIPTIONS -> vm.loadSubscriptions()
            Screen.PROXY -> Unit
            Screen.BROADCAST -> Unit
            Screen.SIM -> Unit
            Screen.ADB -> Unit
            Screen.WALLET -> { vm.loadPlansAndWallet(); vm.loadWalletDetails() }
        }
    }

    val sectionTitle = when (screen) {
        Screen.SUBSCRIPTIONS -> tr("اشتراكاتي", "My subscriptions")
        Screen.PROXY -> tr("اتصال البروكسي", "Proxy connection")
        Screen.BROADCAST -> tr("بث شبكة", "Network sharing")
        Screen.SIM -> "SIM"
        Screen.ADB -> "ADB"
        Screen.PROFILE -> tr("حسابي", "My account")
        Screen.WALLET -> tr("المحفظة", "Wallet")
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BrandMark(42.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Kun cloak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(sectionTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    vm.notifications.firstOrNull { !it.read }?.let { notice ->
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().clickable { vm.markNotificationRead(notice.id) }) {
                            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                                Text(notice.title, fontWeight = FontWeight.Bold, color = KunPalette.PrimaryDeep)
                                Text(notice.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(KunPalette.Border))
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 5.dp) {
                val entries = listOf(
                    Triple(Screen.SUBSCRIPTIONS, tr("الخطط", "Plans"), R.drawable.ic_nav_subscriptions),
                    Triple(Screen.PROXY, tr("الاتصال", "Connect"), R.drawable.ic_nav_proxy),
                    Triple(Screen.BROADCAST, tr("البث", "Sharing"), R.drawable.ic_nav_broadcast),
                    Triple(Screen.ADB, "ADB", R.drawable.ic_nav_adb),
                    Triple(Screen.SIM, "SIM", R.drawable.ic_nav_sim),
                    Triple(Screen.PROFILE, tr("حسابي", "Account"), R.drawable.ic_nav_profile),
                )
                entries.forEach { (destination, label, iconId) ->
                    NavigationBarItem(
                        selected = screen == destination,
                        onClick = { screen = destination },
                        icon = {
                            val unreadCount = if (destination == Screen.PROFILE) vm.unreadSupportMessageCount else 0
                            if (unreadCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = KunPalette.Error, contentColor = Color.White) {
                                            Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                                        }
                                    },
                                ) {
                                    Icon(painterResource(iconId), contentDescription = label, modifier = Modifier.size(22.dp))
                                }
                            } else {
                                Icon(painterResource(iconId), contentDescription = label, modifier = Modifier.size(22.dp))
                            }
                        },
                        label = { Text(label, maxLines = 1) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Crossfade(targetState = screen, modifier = Modifier.fillMaxSize()) { destination ->
            when (destination) {
                Screen.SUBSCRIPTIONS -> Subscriptions(vm, padding)
                Screen.PROXY -> AccountFeatureGate(
                    vm = vm,
                    allowed = vm.access.vpn || vm.access.advanced,
                    onShowPlans = { screen = Screen.SUBSCRIPTIONS },
                ) {
                    ProxyScreen(
                        padding = padding,
                        wirelessReady = vm.wirelessState == WirelessDebuggingManager.DebuggingState.READY,
                        vpnAllowed = vm.access.vpn,
                        advancedAllowed = vm.access.advanced,
                        onGoToAdb = { screen = Screen.ADB },
                        api = vm.apiClient,
                    )
                }
                Screen.BROADCAST -> AccountFeatureGate(
                    vm = vm,
                    allowed = vm.access.vpn,
                    onShowPlans = { screen = Screen.SUBSCRIPTIONS },
                ) {
                    NetworkBroadcastScreen(
                        vm = vm,
                        padding = padding,
                        onGoToAdb = { screen = Screen.ADB },
                    )
                }
                Screen.SIM -> AccountFeatureGate(
                    vm = vm,
                    allowed = vm.access.sim,
                    onShowPlans = { screen = Screen.SUBSCRIPTIONS },
                ) {
                    if (vm.wirelessState != WirelessDebuggingManager.DebuggingState.READY) {
                        AdbRequiredPanel(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            title = tr("إعدادات الشريحة", "SIM settings"),
                            description = tr("فعّل اتصال ADB أولًا للوصول إلى أدوات SIM.", "Enable the ADB connection first to access SIM tools."),
                        ) { screen = Screen.ADB }
                    } else {
                        SimScreen(padding, adbReady = true) { screen = Screen.ADB }
                    }
                }
                Screen.ADB -> AdbScreen(vm, padding)
                Screen.PROFILE -> ProfileScreen(vm, padding, onWallet = { screen = Screen.WALLET }, onPlans = { screen = Screen.SUBSCRIPTIONS })
                Screen.WALLET -> WalletScreen(vm, padding)
            }
        }
    }
}

@Composable
internal fun Subscriptions(vm: AppViewModel, padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PageHeading(tr("حسابك", "Your account"), tr("باقات الاشتراك", "Subscription plans"), tr("اختر الباقة المناسبة وفعّلها من رصيد محفظتك.", "Choose a plan and activate it using your wallet balance."))
        }
        if (vm.plansLoading && vm.plans.isEmpty()) item { StateCard(tr("جارٍ تحميل الباقات", "Loading plans"), tr("نسترجع الباقات المتاحة.", "Retrieving available plans."), loading = true) }
        if (vm.plansError != null && vm.plans.isEmpty()) item { StateCard(tr("تعذّر تحميل الباقات", "Unable to load plans"), vm.plansError.orEmpty(), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadPlansAndWallet(force = true) }) }
        if (!vm.plansLoading && vm.plans.isEmpty() && vm.plansError == null) item { StateCard(tr("لا توجد باقات متاحة", "No plans available"), tr("عد لاحقًا أو حدّث القائمة.", "Check back later or refresh the list."), actionLabel = tr("تحديث", "Refresh"), onAction = { vm.loadPlansAndWallet(force = true) }) }
        if (vm.activationError != null) item { StateCard(tr("تعذّر تفعيل الباقة", "Unable to activate the plan"), vm.activationError.orEmpty(), error = true) }
        items(vm.plans, key = { it.id }) { plan -> PlanCard(plan, vm) }
        if (vm.subscriptions.isNotEmpty()) item { Text(tr("اشتراكاتي النشطة", "My active subscriptions"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        when {
            vm.subscriptionsLoading && vm.subscriptions.isEmpty() -> item {
                StateCard(tr("جارٍ تحميل الاشتراكات", "Loading subscriptions"), tr("نسترجع بيانات حسابك من الخدمة.", "Retrieving your account details from the service."), loading = true)
            }
            vm.subscriptionsError != null && vm.subscriptions.isEmpty() -> item {
                StateCard(tr("تعذّر تحميل الاشتراكات", "Unable to load subscriptions"), vm.subscriptionsError.orEmpty(), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadSubscriptions() })
            }
            vm.subscriptions.isEmpty() -> Unit
            else -> {
                if (vm.subscriptionsError != null) item {
                    StateCard(tr("تعذّر تحديث القائمة", "Unable to refresh the list"), tr("نعرض آخر بيانات الاشتراكات التي تم تحميلها.", "Showing the most recently loaded subscription details."), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadSubscriptions() })
                }
                items(vm.subscriptions) { subscription ->
                    SubscriptionCard(subscription)
                }
            }
        }
    }
}

@Composable
private fun SubscriptionCard(subscription: Subscription) {
    val active = subscription.status.equals("active", ignoreCase = true)
    KUNCard {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(40.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(subscription.product, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(subscription.protocol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FeaturePill(
                    statusLabel(subscription.status),
                    color = if (active) KunPalette.Success else KunPalette.Warning,
                    container = if (active) KunPalette.SuccessSoft else KunPalette.WarningSoft,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(KunPalette.Border))
            KeyValueRow(tr("تنتهي في", "Expires on"), readableDate(subscription.expires))
        }
    }
}

@Composable
internal fun ProfileScreen(vm: AppViewModel, padding: PaddingValues, onWallet: () -> Unit, onPlans: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) vm.loadSupportMessages(reset = true)
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(tr("حسابي", "My account")) })
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(tr("رسائل الدعم", "Support messages"))
                        if (vm.unreadSupportMessageCount > 0) {
                            Badge(containerColor = KunPalette.Error, contentColor = Color.White) {
                                Text(if (vm.unreadSupportMessageCount > 99) "99+" else vm.unreadSupportMessageCount.toString())
                            }
                        }
                    }
                },
            )
        }
        if (selectedTab == 1) {
            SupportInboxContent(vm, Modifier.weight(1f))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
        item { PageHeading(tr("إعدادات الحساب", "Account settings"), tr("ملفي الشخصي", "My profile"), tr("معلومات حسابك وحالة التحقق من البريد الإلكتروني.", "Your account information and email verification status.")) }
        if (vm.profileLoading && vm.profile == null) {
            item { StateCard(tr("جارٍ تحميل الحساب", "Loading account"), tr("نطلب معلومات الملف الشخصي من الخدمة.", "Retrieving profile information from the service."), loading = true) }
        } else if (vm.profileError != null && vm.profile == null) {
            item { StateCard(tr("تعذّر تحميل الحساب", "Unable to load account"), vm.profileError.orEmpty(), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadProfile() }) }
        } else if (vm.profile == null) {
            item { StateCard(tr("معلومات الحساب غير متاحة", "Account information unavailable"), tr("جرّب تحديث بيانات الملف الشخصي.", "Try refreshing your profile information."), actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadProfile() }) }
        } else {
            val profile = vm.profile!!
            item {
                Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = KunPalette.PrimaryDeep)) {
                    Row(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(KunPalette.PrimaryDeep, KunPalette.Primary, KunPalette.Cyan))).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(Modifier.size(60.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            Text(profile.name.take(1).ifBlank { "K" }.uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            Text(profile.email, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
            }
            item {
                KUNCard {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        Text(tr("حالة الحساب", "Account status"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        KeyValueRow(tr("نوع الحساب", "Account type"), roleLabel(profile.role))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("البريد الإلكتروني", "Email address"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FeaturePill(
                                if (profile.verified) tr("تم التحقق", "Verified") else tr("بانتظار التحقق", "Pending verification"),
                                color = if (profile.verified) KunPalette.Success else KunPalette.Warning,
                                container = if (profile.verified) KunPalette.SuccessSoft else KunPalette.WarningSoft,
                            )
                        }
                    }
                }
            }
            if (vm.profileError != null) item {
                StateCard(tr("تعذّر تحديث بيانات الحساب", "Unable to refresh account details"), tr("نعرض آخر بيانات نجح تحميلها.", "Showing the most recently loaded information."), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadProfile() })
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KUNPrimaryButton(tr("المحفظة", "Wallet"), onWallet, modifier = Modifier.weight(1f).height(52.dp))
            KUNSecondaryButton(tr("الخطط", "Plans"), onPlans, modifier = Modifier.weight(1f).height(52.dp))
        } }
        item {
            OutlinedButton(
                onClick = { vm.logout() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = KunPalette.Error),
            ) { Text(tr("تسجيل الخروج", "Sign out"), fontWeight = FontWeight.Bold) }
        }
            }
        }
    }
}

@Composable
private fun SupportInboxContent(vm: AppViewModel, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PageHeading(tr("رسائل الإدارة", "Administration messages"), tr("رسائل الدعم", "Support messages"), tr("تُحفظ رسائل الإدارة هنا لتتمكن من الرجوع إليها في أي وقت.", "Administration messages are saved here for future reference."))
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                FeaturePill(
                    tr("${vm.unreadSupportMessageCount} غير مقروءة", "${vm.unreadSupportMessageCount} unread"),
                    color = if (vm.unreadSupportMessageCount > 0) KunPalette.Error else KunPalette.Success,
                    container = if (vm.unreadSupportMessageCount > 0) KunPalette.ErrorSoft else KunPalette.SuccessSoft,
                )
                OutlinedButton(onClick = { vm.loadSupportMessages(reset = true) }, enabled = !vm.supportMessagesLoading) {
                    Text(if (vm.supportMessagesLoading) tr("جارٍ التحديث…", "Refreshing…") else tr("تحديث", "Refresh"))
                }
            }
        }
        if (vm.supportMessagesLoading && vm.supportMessages.isEmpty()) {
            item { StateCard(tr("جارٍ تحميل رسائل الدعم", "Loading support messages"), tr("نحدّث الرسائل المحفوظة لحسابك.", "Refreshing the messages saved to your account."), loading = true) }
        } else if (vm.supportMessagesError != null && vm.supportMessages.isEmpty()) {
            item {
                StateCard(
                    tr("تعذّر تحميل رسائل الدعم", "Unable to load support messages"),
                    vm.supportMessagesError.orEmpty(),
                    error = true,
                    actionLabel = tr("إعادة المحاولة", "Try again"),
                    onAction = { vm.loadSupportMessages(reset = true) },
                )
            }
        } else if (vm.supportMessages.isEmpty()) {
            item { StateCard(tr("لا توجد رسائل بعد", "No messages yet"), tr("ستظهر هنا الرسائل التي يرسلها فريق الدعم أو الإدارة إلى المستخدمين.", "Messages from support or the administration will appear here.")) }
        }
        items(vm.supportMessages, key = { it.id }) { message ->
            KUNCard(modifier = Modifier.clickable(enabled = !message.read) { vm.markNotificationRead(message.id) }) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message.title.ifBlank { tr("رسالة من الدعم", "Message from support") }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        FeaturePill(
                            if (message.read) tr("مقروءة", "Read") else tr("جديدة", "New"),
                            color = if (message.read) MaterialTheme.colorScheme.onSurfaceVariant else KunPalette.Error,
                            container = if (message.read) KunPalette.SurfaceSoft else KunPalette.ErrorSoft,
                        )
                    }
                    Text(readableDate(message.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(KunPalette.Border))
                    Text(message.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    if (!message.read) {
                        Text(tr("اضغط على الرسالة لتحديدها كمقروءة", "Tap the message to mark it as read"), style = MaterialTheme.typography.labelSmall, color = KunPalette.Primary)
                    }
                }
            }
        }
        if (vm.supportMessagesError != null && vm.supportMessages.isNotEmpty()) {
            item { StateCard(tr("تعذّر تحديث الرسائل", "Unable to refresh messages"), vm.supportMessagesError.orEmpty(), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadSupportMessages(reset = true) }) }
        }
        if (vm.supportMessagesHasMore) {
            item {
                OutlinedButton(
                    onClick = { vm.loadSupportMessages() },
                    enabled = !vm.supportMessagesLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.supportMessagesLoading) tr("جارٍ تحميل الرسائل…", "Loading messages…") else tr("تحميل رسائل أقدم", "Load older messages")) }
            }
        }
    }
}

@Composable
private fun WalletCard(vm: AppViewModel, onTopUp: () -> Unit) {
    KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column { Text(tr("المحفظة", "Wallet"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(tr("الرصيد المتاح", "Available balance"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(vm.wallet?.let { "%.2f ${it.currency}".format(Locale.US, it.balance) } ?: "—", style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Ltr), fontWeight = FontWeight.ExtraBold, color = KunPalette.Primary)
        }
        KUNPrimaryButton(tr("شحن الرصيد", "Add funds"), onTopUp)
    } }
}

@Composable
private fun WalletScreen(vm: AppViewModel, padding: PaddingValues) {
    var topUpOpen by remember { mutableStateOf(false) }
    var selectedMethod by remember { mutableStateOf<TopUpMethod?>(null) }
    var amountText by remember { mutableStateOf("") }
    var referenceText by remember { mutableStateOf("") }
    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { PageHeading(tr("حسابك", "Your account"), tr("المحفظة", "Wallet"), tr("إدارة رصيدك ومراجعة جميع العمليات المالية.", "Manage your balance and review all financial transactions.")) }
        if (vm.walletLoading && vm.wallet == null) item { StateCard(tr("جارٍ تحميل الرصيد", "Loading balance"), tr("لحظة من فضلك.", "Please wait."), loading = true) }
        if (vm.walletError != null && vm.wallet == null) item { StateCard(tr("تعذر تحميل الرصيد", "Unable to load balance"), vm.walletError.orEmpty(), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadPlansAndWallet(force = true) }) }
        item { WalletCard(vm) { topUpOpen = true; selectedMethod = null; amountText = ""; referenceText = "" } }
        if (vm.walletDetailsLoading && vm.walletTransactions.isEmpty() && vm.topUpRequests.isEmpty()) item { StateCard(tr("جارٍ تحميل السجل", "Loading history"), tr("لحظة من فضلك.", "Please wait."), loading = true) }
        if (vm.walletDetailsError != null) item { StateCard(tr("تعذر تحميل بعض التفاصيل", "Unable to load some details"), vm.walletDetailsError.orEmpty(), error = true, actionLabel = tr("إعادة المحاولة", "Try again"), onAction = { vm.loadWalletDetails(force = true) }) }
        if (topUpOpen) item {
            KUNCard { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("شحن المحفظة", "Add funds to wallet"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    TextButton(onClick = { topUpOpen = false; vm.clearTopUp() }) { Text(tr("إغلاق", "Close")) }
                }
                if (vm.topUpError != null) StateCard(tr("تعذر تنفيذ طلب الشحن", "Unable to process the top-up request"), vm.topUpError.orEmpty(), error = true)
                val created = vm.topUpCreated
                if (created == null) {
                    Text(tr("اختر طريقة الشحن المناسبة لك.", "Choose a top-up method."), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    vm.topUpMethods.forEach { method ->
                        FilterChip(selected = selectedMethod?.code == method.code, onClick = { selectedMethod = method; amountText = "" }, label = { Text(method.name) }, modifier = Modifier.fillMaxWidth(), enabled = method.address.isNotBlank())
                    }
                    if (vm.topUpMethods.isEmpty() && vm.walletDetailsLoading) {
                        StateCard(tr("جارٍ تحميل طرق الشحن", "Loading top-up methods"), tr("لحظة من فضلك.", "Please wait."), loading = true)
                    } else if (vm.topUpMethods.isEmpty() && vm.walletDetailsError == null) {
                        Text(tr("لا توجد طرق شحن متاحة حاليًا.", "No top-up methods are currently available."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    selectedMethod?.let { method ->
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(tr("أرسل المبلغ إلى العنوان التالي", "Send the amount to the following address"), fontWeight = FontWeight.Bold)
                                Text(method.address, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), fontWeight = FontWeight.ExtraBold, color = KunPalette.Primary)
                                if (method.network.isNotBlank()) Text(tr("الشبكة: ${method.network}", "Network: ${method.network}"))
                                Text(tr("الحد الأدنى: ${method.minAmount} ${method.currency}", "Minimum: ${method.minAmount} ${method.currency}"))
                                if (method.currency == "SYP") Text(tr("سعر الصرف: 1 دولار = ${method.exchangeRate} ليرة سورية", "Exchange rate: 1 USD = ${method.exchangeRate} SYP"))
                                if (method.instructions.isNotBlank()) ExpandableDetails(tr("تعليمات إضافية", "Additional instructions")) {
                                    Text(method.instructions, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        OutlinedTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = { Text(tr("المبلغ الذي سترسله (${method.currency})", "Amount to send (${method.currency})")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(15.dp), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                        val amount = amountText.toDoubleOrNull()
                        if (amount != null && amount < method.minAmount) {
                            Text(tr("الحد الأدنى ${method.minAmount} ${method.currency}.", "Minimum: ${method.minAmount} ${method.currency}."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (method.currency == "SYP" && amount != null && method.exchangeRate > 0) Text(tr("سيضاف تقريبًا: ${"%.2f".format(Locale.US, amount / method.exchangeRate)} USD", "Approximate credit: ${"%.2f".format(Locale.US, amount / method.exchangeRate)} USD"), color = KunPalette.Primary, fontWeight = FontWeight.Bold)
                        KUNPrimaryButton(tr("إنشاء طلب الشحن", "Create top-up request"), onClick = { if (amount != null) vm.createTopUp(method, amount) }, enabled = amount != null && amount >= method.minAmount, loading = vm.topUpLoading)
                    }
                } else {
                    Text(tr("أدخل رقم العملية بعد إتمام التحويل.", "Enter the transaction ID after completing the transfer."), style = MaterialTheme.typography.bodyMedium)
                    Text(tr("المبلغ المحتسب: ${"%.2f".format(Locale.US, created.expectedUsd)} USD", "Calculated amount: ${"%.2f".format(Locale.US, created.expectedUsd)} USD"), fontWeight = FontWeight.Bold, color = KunPalette.Primary)
                    OutlinedTextField(referenceText, { referenceText = it }, Modifier.fillMaxWidth(), label = { Text(tr("رقم العملية / Tx ID", "Transaction ID / Tx ID")) }, singleLine = true, shape = RoundedCornerShape(15.dp), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                    KUNPrimaryButton(tr("إرسال للتحقق", "Submit for verification"), onClick = { vm.verifyTopUp(created, referenceText.trim()) { } }, enabled = referenceText.trim().length >= 3, loading = vm.topUpLoading)
                }
            } }
        }
        item {
            ExpandableDetails(
                if (vm.walletDetailsLoading) tr("سجل العمليات", "Transaction history") else tr("سجل العمليات والطلبات (${vm.walletTransactions.size + vm.topUpRequests.size})", "Transactions and requests (${vm.walletTransactions.size + vm.topUpRequests.size})"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (vm.walletDetailsLoading) {
                        Text(tr("جارٍ تحميل السجل…", "Loading history…"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (vm.walletTransactions.isEmpty() && vm.topUpRequests.isEmpty()) {
                        Text(tr("لا توجد عمليات بعد.", "No transactions yet."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vm.walletTransactions.forEach { tx ->
                        KUNCard {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tx.description, fontWeight = FontWeight.Bold)
                                    Text(readableDate(tx.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("${if (tx.amount >= 0) "+" else ""}%.2f".format(Locale.US, tx.amount), style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr), fontWeight = FontWeight.ExtraBold, color = if (tx.amount >= 0) KunPalette.Success else KunPalette.Error)
                            }
                        }
                    }
                    vm.topUpRequests.forEach { request ->
                        KUNCard {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(request.methodCode, fontWeight = FontWeight.Bold)
                                        Text("${request.amount} ${request.currency} • ${"%.2f".format(Locale.US, request.expectedUsd)} USD", style = MaterialTheme.typography.bodySmall)
                                    }
                                    FeaturePill(statusLabel(request.status), color = if (request.status == "verified") KunPalette.Success else if (request.status == "rejected") KunPalette.Error else KunPalette.Warning, container = if (request.status == "verified") KunPalette.SuccessSoft else if (request.status == "rejected") KunPalette.ErrorSoft else KunPalette.WarningSoft)
                                }
                                ExpandableDetails(tr("تفاصيل الطلب", "Request details")) {
                                    val reference = request.reference.ifBlank { tr("غير متوفر", "Unavailable") }
                                    Text(tr("رقم العملية: $reference", "Transaction ID: $reference"), style = MaterialTheme.typography.bodySmall)
                                    Text(tr("تاريخ الطلب: ${readableDate(request.createdAt)}", "Request date: ${readableDate(request.createdAt)}"), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanCard(plan: Plan, vm: AppViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), border = BorderStroke(if (plan.featured) 2.dp else 1.dp, if (plan.featured) KunPalette.Primary else KunPalette.Border), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(plan.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (plan.featured) FeaturePill(tr("مميز", "Featured"), color = KunPalette.Primary, container = MaterialTheme.colorScheme.primaryContainer) }
            Text(if (plan.freeClaimOnce) tr("مجاني لأول مرة", "Free on first use") else plan.monthly.ifBlank { tr("السعر غير محدد", "Price not specified") }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = if (plan.freeClaimOnce) KunPalette.Success else KunPalette.Primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { val labels = mapOf("advanced" to tr("الوضع المتقدم", "Advanced mode"), "sim" to "SIM", "vpn" to "VPN", "mock_location" to tr("محاكاة الموقع", "Location simulation"), "broadcast" to tr("بث الشبكة", "Network sharing")); plan.features.forEach { labels[it]?.let { label -> FeaturePill(label, color = KunPalette.Success, container = KunPalette.SuccessSoft) } } }
            if (plan.description.isNotBlank() || plan.duration.isNotBlank()) ExpandableDetails(tr("تفاصيل الخطة", "Plan details")) {
                if (plan.description.isNotBlank()) Text(plan.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan.duration.isNotBlank()) Text(tr("المدة المدفوعة: ${plan.duration}", "Paid duration: ${plan.duration}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (plan.freeClaimOnce) {
                    val trialDuration = if (plan.freeDurationMinutes > 0) tr("${plan.freeDurationMinutes} دقيقة", "${plan.freeDurationMinutes} ${if (plan.freeDurationMinutes == 1) "minute" else "minutes"}") else plan.duration
                    Text(tr("التجربة المجانية: $trialDuration", "Free trial: $trialDuration"), style = MaterialTheme.typography.bodySmall, color = KunPalette.Success)
                }
            }
            KUNPrimaryButton(
                text = if (plan.freeClaimOnce) tr("تفعيل التجربة المجانية", "Activate free trial") else tr("تفعيل من رصيد المحفظة", "Activate using wallet balance"),
                onClick = { vm.activatePlan(plan) },
                loading = vm.activatingPlanId == plan.id,
                enabled = vm.activatingPlanId == null,
            )
        }
    }
}

@Composable
private fun AccountFeatureGate(
    vm: AppViewModel,
    allowed: Boolean,
    onShowPlans: () -> Unit,
    content: @Composable () -> Unit,
) {
    when {
        vm.accessLoaded && allowed -> content()
        vm.accessLoaded -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            StateCard(
                title = tr("الاشتراك مطلوب", "Subscription required"),
                message = tr("هذه الميزة غير متاحة في باقتك الحالية.", "This feature is not available in your current plan."),
                actionLabel = tr("عرض الخطط", "View plans"),
                onAction = onShowPlans,
            )
        }
        vm.accessLoading -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            StateCard(tr("جارٍ التحقق من الاشتراك", "Checking subscription"), tr("لحظة من فضلك.", "Please wait."), loading = true)
        }
        else -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            StateCard(
                title = tr("تعذر التحقق من الاشتراك", "Unable to verify subscription"),
                message = vm.accessError ?: tr("تحقق من اتصال الإنترنت ثم أعد المحاولة.", "Check your internet connection and try again."),
                error = true,
                actionLabel = tr("إعادة المحاولة", "Try again"),
                onAction = { vm.refreshAccess() },
            )
        }
    }
}

private fun statusLabel(status: String): String = when (status.lowercase(Locale.ROOT)) {
    "active" -> tr("نشط", "Active")
    "pending" -> tr("قيد الانتظار", "Pending")
    "expired" -> tr("منتهي", "Expired")
    "cancelled", "canceled" -> tr("ملغي", "Canceled")
    else -> status.ifBlank { tr("غير محدد", "Not specified") }
}

private fun roleLabel(role: String): String = when (role.lowercase(Locale.ROOT)) {
    "user" -> tr("مستخدم", "User")
    "admin" -> tr("مدير", "Administrator")
    else -> role.ifBlank { tr("مستخدم", "User") }
}

private fun readableDate(value: String): String {
    if (value.isBlank()) return tr("غير محدد", "Not specified")
    return runCatching {
        val date = Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate()
        val arabicDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("ar")).format(date)
        val englishDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(date)
        tr("$arabicDate", "$englishDate")
    }.getOrDefault(value)
}

@Composable
private fun RecoveryScreen(vm: AppViewModel, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(KunPalette.Background)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            BrandMark(62.dp)
            Text(tr("استعادة الحساب", "Recover account"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(tr("أدخل البريد وكلمة مرور جديدة للمتابعة", "Enter your email address and a new password to continue"), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Card(Modifier.fillMaxWidth().widthIn(max = 480.dp), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(7.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(tr("البريد الإلكتروني", "Email address")) }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                    OutlinedTextField(newPassword, { newPassword = it }, Modifier.fillMaxWidth(), label = { Text(tr("كلمة المرور الجديدة", "New password")) }, singleLine = true, shape = RoundedCornerShape(16.dp), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done), textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
                    AnimatedVisibility(vm.error != null) {
                        Surface(color = KunPalette.ErrorSoft, shape = RoundedCornerShape(14.dp)) { Text(vm.error.orEmpty(), Modifier.fillMaxWidth().padding(12.dp), color = KunPalette.Error, style = MaterialTheme.typography.bodySmall) }
                    }
                    KUNPrimaryButton(
                        text = tr("تأكيد الاستعادة", "Confirm recovery"),
                        onClick = { vm.deviceResetPassword(email, newPassword) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        enabled = !vm.loading && email.isNotBlank() && newPassword.length >= 8,
                        loading = vm.loading,
                    )
                    Text(tr("العودة لتسجيل الدخول", "Back to sign in"), Modifier.fillMaxWidth().clickable(onClick = onBack).padding(8.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
