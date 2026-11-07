package com.proxyplatform.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import com.proxyplatform.app.adb.PairingCodeInput
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

internal data class Product(
    val id: String,
    val name: String,
    val description: String,
    val protocol: String,
    val country: String,
    val city: String,
    val ipType: String,
    val daily: String,
    val weekly: String,
    val monthly: String,
    val featured: Boolean,
)
internal data class MarketplaceProvider(val id: String, val number: Int, val name: String, val description: String)
internal data class ProxyPurchasePlan(val id: String, val name: String, val durationHours: Int, val price: Double, val serviceId: String = "", val providerPlanId: String = "")
internal data class ProxyPurchaseLocation(
    val id: String,
    val countryCode: String,
    val country: String,
    val state: String,
    val provider: String,
    val protocols: Set<String>,
) {
    val label: String get() = listOf(country, state, provider).filter { it.isNotBlank() }.joinToString(" • ")
}
internal data class ProxyPurchaseOptions(val provider: String, val protocol: String, val plans: List<ProxyPurchasePlan>, val locations: List<ProxyPurchaseLocation>, val serviceId: String = "", val proxyType: String = "", val selectedPlanId: String = "")
internal data class RemoteProxy(val id: String, val protocol: String, val host: String, val port: Int, val username: String, val password: String, val status: String, val expiresAt: String, val location: String)
internal data class PurchasedProxy(val protocol: String, val host: String, val port: Int, val username: String, val password: String, val url: String, val expiresAt: String)
internal data class Profile(val email: String, val name: String, val role: String, val verified: Boolean)
internal data class Subscription(val status: String, val expires: String, val product: String, val protocol: String)
internal data class Plan(val id: String, val name: String, val description: String, val monthly: String, val duration: String, val features: Set<String>, val featured: Boolean)
internal data class Wallet(val balance: Double, val currency: String)
internal data class WalletTransaction(val amount: Double, val description: String, val type: String, val createdAt: String)
internal data class TopUpMethod(val code: String, val name: String, val currency: String, val network: String, val address: String, val minAmount: Double, val exchangeRate: Double, val instructions: String)
internal data class TopUpRequest(val id: String, val methodCode: String, val amount: Double, val currency: String, val expectedUsd: Double, val reference: String, val status: String, val createdAt: String)
internal data class FeatureAccess(val advanced: Boolean = false, val sim: Boolean = false, val vpn: Boolean = false, val mockLocation: Boolean = false)
internal enum class Screen { MARKET, SUBSCRIPTIONS, PROXY, SIM, PROFILE, WALLET }

private val SuccessGreen = KunPalette.Success
private val SuccessGreenContainer = KunPalette.SuccessSoft
private val OnSuccessGreenContainer = Color(0xFF14624F)

@Composable private fun ProxyTheme(content: @Composable () -> Unit) = KUNTheme(content)

private fun formatProductPrice(value: Double, period: String): String {
    if (!value.isFinite() || value < 0.0) return ""
    val amount = java.text.NumberFormat.getNumberInstance(java.util.Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(value)
    return "$amount دولار / $period"
}

private class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("proxy_session", Context.MODE_PRIVATE)
    var accessToken: String? get() = prefs.getString("access_token", null); set(value) { prefs.edit().putString("access_token", value).apply() }
    var refreshToken: String? get() = prefs.getString("refresh_token", null); set(value) { prefs.edit().putString("refresh_token", value).apply() }
    fun clear() = prefs.edit().clear().apply()
}

internal class ApiClient(context: Context) {
    private val appContext = context.applicationContext
    private val store = SessionStore(appContext)
    private val client = OkHttpClient()
    private val jsonType = "application/json".toMediaType()
    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        AdvancedOperationLog.info(appContext, "HTTP $method $path — بيانات الاعتماد ومتن الطلب لا تُسجّل.")
        val builder = Request.Builder().url(BuildConfig.API_BASE_URL.trimEnd('/') + path).header("Accept", "application/json")
        store.accessToken?.let { builder.header("Authorization", "Bearer " + it) }
        if (body != null) builder.method(method, body.toString().toRequestBody(jsonType)) else builder.method(method, null)
        return try {
            client.newCall(builder.build()).execute().use { response ->
                AdvancedOperationLog.info(appContext, "HTTP $method $path -> ${response.code}.")
                val raw = response.body?.string().orEmpty(); val result = if (raw.isBlank()) JSONObject() else JSONObject(raw)
                if (!response.isSuccessful) {
                    AdvancedOperationLog.error(appContext, "HTTP request failed with status ${response.code}.")
                    throw IllegalStateException(result.optJSONObject("error")?.optString("message") ?: "فشل الطلب (" + response.code + ")")
                }
                result
            }
        } catch (failure: Exception) {
            AdvancedOperationLog.error(appContext, "HTTP $method $path exception: ${failure.javaClass.simpleName}.")
            throw failure
        }
    }
    fun login(email: String, password: String) { val d = request("/auth/login", "POST", JSONObject().put("email", email.trim()).put("password", password)).getJSONObject("data"); store.accessToken = d.getString("accessToken"); store.refreshToken = d.optString("refreshToken") }
    fun register(email: String, password: String, name: String) { request("/auth/register", "POST", JSONObject().put("email", email.trim()).put("password", password).put("fullName", name.trim())) }
    fun products(): List<Product> {
        val rows = request("/products").optJSONArray("data")
            ?: throw IllegalStateException("استجابة السوق غير صالحة.")
        return (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            val country = listOf(item.optString("country_name"), item.optString("country_code"))
                .map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(" • ")
            Product(
                id = item.getString("id"),
                name = item.getString("name"),
                description = item.optString("description").trim(),
                protocol = item.optString("protocol").uppercase(),
                country = country,
                city = item.optString("city").trim(),
                ipType = item.optString("ip_type").trim(),
                daily = formatProductPrice(item.optDouble("price_daily", Double.NaN), "يوم"),
                weekly = formatProductPrice(item.optDouble("price_weekly", Double.NaN), "أسبوع"),
                monthly = formatProductPrice(item.optDouble("price_monthly", Double.NaN), "شهر"),
                featured = item.optBoolean("is_featured"),
            )
        }
    }
    fun marketplaceProviders(): List<MarketplaceProvider> {
        val a = request("/products/providers").getJSONArray("data")
        return (0 until a.length()).map { i -> val x = a.getJSONObject(i); MarketplaceProvider(x.getString("id"), x.optInt("number"), x.optString("name"), x.optString("description")) }
    }
    fun proxyPurchaseOptions(provider: String = "ezproxy", serviceId: String = "", planId: String = ""): ProxyPurchaseOptions {
        val enc = { v: String -> java.net.URLEncoder.encode(v, "UTF-8") }
        val query = "provider=${enc(provider)}&protocol=socks5" + (if (serviceId.isNotBlank()) "&serviceId=${enc(serviceId)}" else "") + (if (planId.isNotBlank()) "&planId=${enc(planId)}" else "")
        val x = request("/products/purchase-options?$query").getJSONObject("data")
        val plans = x.getJSONArray("plans").let { a -> (0 until a.length()).map { i -> val p = a.getJSONObject(i); val label = p.optJSONObject("name")?.optString("ar").orEmpty().ifBlank { p.optJSONObject("name")?.optString("en") ?: p.optString("name", "خطة") }; ProxyPurchasePlan(p.optString("id", p.optString("code")), label, p.optInt("duration_hours"), p.optDouble("price"), p.optString("serviceId"), p.optString("providerPlanId")) } }
        val locations = x.getJSONArray("locations").let { a -> (0 until a.length()).map { i -> val l = a.getJSONObject(i); val c = l.optJSONObject("country"); val protocols = l.optJSONObject("protocols"); ProxyPurchaseLocation(l.optString("id"), c?.optString("code").orEmpty().ifBlank { l.optString("country") }, c?.optString("name").orEmpty().ifBlank { l.optString("country") }, l.optString("state"), l.optString("isp"), setOf("http", "socks5").filter { protocols?.optBoolean(it, true) != false }.toSet()) } }
        return ProxyPurchaseOptions(x.optString("provider", provider), x.optString("protocol", "socks5"), plans, locations, x.optString("serviceId"), x.optString("proxyType"), x.optString("selectedPlanId"))
    }
    fun purchaseProxy(options: ProxyPurchaseOptions, plan: ProxyPurchasePlan, locationId: String, username: String, password: String): PurchasedProxy {
        val body = JSONObject().put("clientRequestId", UUID.randomUUID().toString()).put("provider", options.provider).put("serviceId", options.serviceId).put("planId", if (plan.providerPlanId.isNotBlank()) plan.providerPlanId else plan.id).put("locationId", locationId).put("country", locationId).put("protocol", options.protocol).put("username", username).put("password", password).put("periodMonths", 1).put("trafficGb", 1).put("rotation", -1)
        val x = request("/products/purchase-proxy", "POST", body).getJSONObject("data").getJSONObject("proxy")
        return PurchasedProxy(x.optString("protocol", options.protocol), x.getString("host"), x.getInt("port"), x.optString("username", username), x.optString("password", password), x.optString("url"), x.optString("expires_at"))
    }
    fun proxyQuote(options: ProxyPurchaseOptions, plan: ProxyPurchasePlan, location: ProxyPurchaseLocation): Double? {
        if (options.provider == "ezproxy") return plan.price
        val x = request("/products/purchase-quote", "POST", JSONObject().put("provider", options.provider).put("serviceId", options.serviceId).put("planId", if (plan.providerPlanId.isNotBlank()) plan.providerPlanId else plan.id).put("locationId", location.id).put("country", location.country)).getJSONObject("data")
        return if (x.isNull("price")) null else x.optDouble("price", Double.NaN).takeIf { it.isFinite() }
    }
    fun proxies(): List<RemoteProxy> {
        val a = request("/me/proxies").getJSONArray("data")
        return (0 until a.length()).map { i -> val x = a.getJSONObject(i); val location = x.optJSONObject("location"); RemoteProxy(x.optString("id", x.optString("order_id")), x.optString("protocol", "socks5"), x.optString("host"), x.optInt("port"), x.optString("username"), x.optString("password"), x.optString("status", "active"), x.optString("expires_at"), listOf(location?.optJSONObject("country")?.optString("name"), location?.optString("state"), location?.optString("isp")).filter { !it.isNullOrBlank() }.joinToString(" • ")) }
    }
    fun profile(): Profile { val x = request("/me").getJSONObject("data"); return Profile(x.optString("email"), x.optString("full_name", "بدون اسم"), x.optString("role", "user"), x.optBoolean("is_email_verified")) }
    fun subscriptions(): List<Subscription> { val a = request("/me/subscriptions").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); val p = x.optJSONObject("subscription_plans") ?: x.optJSONObject("proxy_products"); Subscription(x.optString("status"), x.optString("expires_at"), p?.optString("name", "الاشتراك") ?: "الاشتراك", p?.optString("protocol", "اشتراك") ?: "اشتراك") } }
    fun plans(): List<Plan> { val a = request("/me/subscription-plans").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); val flags = x.optJSONObject("feature_flags"); val features = listOf("advanced", "sim", "vpn", "mock_location").filter { flags?.optBoolean(it, false) == true }.toSet(); Plan(x.getString("id"), x.optString("name"), x.optString("description"), formatProductPrice(x.optDouble("price", Double.NaN), x.optInt("duration_days", 30).toString() + " يوم"), x.optInt("duration_days", 30).toString() + " يوم", features, x.optBoolean("is_featured")) } }
    fun wallet(): Wallet { val x = request("/me/wallet").getJSONObject("data"); return Wallet(x.optDouble("balance", 0.0), x.optString("currency", "USD")) }
    fun walletTransactions(): List<WalletTransaction> { val a = request("/me/wallet/transactions").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); WalletTransaction(x.optDouble("amount", 0.0), x.optString("description"), x.optString("transaction_type"), x.optString("created_at")) } }
    fun topUpMethods(): List<TopUpMethod> { val a = request("/me/wallet/top-up/methods").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); TopUpMethod(x.optString("code"), x.optString("name"), x.optString("currency"), x.optString("network"), x.optString("address"), x.optDouble("min_amount", 0.0), x.optDouble("exchange_rate", 1.0), x.optString("instructions")) } }
    fun topUpRequests(): List<TopUpRequest> { val a = request("/me/wallet/top-up/requests").getJSONArray("data"); return (0 until a.length()).map { val x = a.getJSONObject(it); TopUpRequest(x.optString("id"), x.optString("method_code"), x.optDouble("submitted_amount"), x.optString("submitted_currency"), x.optDouble("expected_usd"), x.optString("transaction_reference"), x.optString("status"), x.optString("created_at")) } }
    fun createTopUp(methodCode: String, amount: Double): TopUpRequest { val x = request("/me/wallet/top-up/requests", "POST", JSONObject().put("methodCode", methodCode).put("amount", amount)).getJSONObject("data"); return TopUpRequest(x.optString("id"), x.optString("method_code"), x.optDouble("submitted_amount"), x.optString("submitted_currency"), x.optDouble("expected_usd"), x.optString("transaction_reference"), x.optString("status"), x.optString("created_at")) }
    fun verifyTopUp(id: String, reference: String): TopUpRequest { val x = request("/me/wallet/top-up/requests/$id/verify", "POST", JSONObject().put("transactionReference", reference)).getJSONObject("data"); return TopUpRequest(x.optString("id"), x.optString("method_code"), x.optDouble("submitted_amount"), x.optString("submitted_currency"), x.optDouble("expected_usd"), x.optString("transaction_reference"), x.optString("status"), x.optString("created_at")) }
    fun featureAccess(): FeatureAccess { val x = request("/me/access").getJSONObject("data"); return FeatureAccess(x.optBoolean("advanced"), x.optBoolean("sim"), x.optBoolean("vpn"), x.optBoolean("mock_location")) }
    fun activatePlan(id: String) { request("/me/subscription-plans/$id/activate", "POST", JSONObject()) }
    fun loggedIn() = store.accessToken != null
    fun logout() = store.clear()
}

internal class AppViewModel(private val api: ApiClient) : ViewModel() {
    var loggedIn by mutableStateOf(api.loggedIn()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    var products by mutableStateOf<List<Product>>(emptyList()); private set
    var productsLoading by mutableStateOf(false); private set
    var productsError by mutableStateOf<String?>(null); private set
    var marketplaceProviders by mutableStateOf<List<MarketplaceProvider>>(emptyList()); private set
    var marketplaceProvidersLoading by mutableStateOf(false); private set
    var marketplaceProvidersError by mutableStateOf<String?>(null); private set
    var selectedMarketplaceProvider by mutableStateOf("ezproxy"); private set
    var purchaseOptions by mutableStateOf<ProxyPurchaseOptions?>(null); private set
    var purchaseLoading by mutableStateOf(false); private set
    var purchaseError by mutableStateOf<String?>(null); private set
    var purchaseQuote by mutableStateOf<Double?>(null); private set
    var purchasedProxy by mutableStateOf<PurchasedProxy?>(null); private set
    var remoteProxies by mutableStateOf<List<RemoteProxy>>(emptyList()); private set
    var remoteProxiesLoading by mutableStateOf(false); private set
    var remoteProxiesError by mutableStateOf<String?>(null); private set

    var profile by mutableStateOf<Profile?>(null); private set
    var profileLoading by mutableStateOf(false); private set
    var profileError by mutableStateOf<String?>(null); private set

    var subscriptions by mutableStateOf<List<Subscription>>(emptyList()); private set
    var subscriptionsLoading by mutableStateOf(false); private set
    var subscriptionsError by mutableStateOf<String?>(null); private set
    var plans by mutableStateOf<List<Plan>>(emptyList()); private set
    var plansLoading by mutableStateOf(false); private set
    var plansError by mutableStateOf<String?>(null); private set
    var wallet by mutableStateOf<Wallet?>(null); private set
    var walletTransactions by mutableStateOf<List<WalletTransaction>>(emptyList()); private set
    var topUpMethods by mutableStateOf<List<TopUpMethod>>(emptyList()); private set
    var topUpRequests by mutableStateOf<List<TopUpRequest>>(emptyList()); private set
    var topUpLoading by mutableStateOf(false); private set
    var topUpError by mutableStateOf<String?>(null); private set
    var topUpCreated by mutableStateOf<TopUpRequest?>(null); private set
    var access by mutableStateOf(FeatureAccess()); private set

    fun login(email: String, password: String) {
        loading = true
        error = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.login(email, password)
                withContext(Dispatchers.Main) {
                    loggedIn = true
                    loading = false
                    loadMarket()
                }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    error = failure.message ?: "تعذر تسجيل الدخول. تحقق من بياناتك وحاول مجددًا."
                    loading = false
                }
            }
        }
    }

    fun register(email: String, password: String, name: String, done: () -> Unit) {
        loading = true
        error = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                api.register(email, password, name)
                withContext(Dispatchers.Main) {
                    loading = false
                    error = "تم إنشاء الحساب. سجّل الدخول للمتابعة."
                    done()
                }
            } catch (failure: Exception) {
                withContext(Dispatchers.Main) {
                    error = failure.message ?: "تعذر إنشاء الحساب. تحقق من البيانات وحاول مجددًا."
                    loading = false
                }
            }
        }
    }

    fun loadMarket() = viewModelScope.launch {
        loadMarketplaceProviders()
        loadRemoteProxies()
    }

    fun loadMarketplaceProviders() = viewModelScope.launch {
        if (marketplaceProvidersLoading || marketplaceProviders.isNotEmpty()) return@launch
        marketplaceProvidersLoading = true; marketplaceProvidersError = null
        try { marketplaceProviders = withContext(Dispatchers.IO) { api.marketplaceProviders() } }
        catch (failure: Exception) { marketplaceProvidersError = failure.message ?: "تعذر تحميل مزودي السوق." }
        finally { marketplaceProvidersLoading = false }
    }

    fun chooseMarketplaceProvider(provider: String) { selectedMarketplaceProvider = provider; purchaseOptions = null; purchaseError = null }
    // Proxy-Cheap locations are plan specific: reload the catalog for the exact plan the user picked
    // so the country/region/city/ISP choices always belong to the plan that is priced and ordered.
    fun loadPurchaseOptionsFor(provider: String, serviceId: String = "", planId: String = "") = viewModelScope.launch {
        purchaseLoading = true; purchaseError = null; purchaseQuote = null; purchaseOptions = null
        try { purchaseOptions = withContext(Dispatchers.IO) { api.proxyPurchaseOptions(provider, serviceId, planId) } }
        catch (failure: Exception) { purchaseError = failure.message ?: "تعذر تحميل خيارات الشراء." }
        finally { purchaseLoading = false }
    }

    fun loadProxyQuote(options: ProxyPurchaseOptions, plan: ProxyPurchasePlan, location: ProxyPurchaseLocation) = viewModelScope.launch {
        purchaseQuote = null
        try { purchaseQuote = withContext(Dispatchers.IO) { api.proxyQuote(options, plan, location) } }
        catch (failure: Exception) { purchaseError = failure.message ?: "تعذر حساب السعر." }
    }

    fun loadPurchaseOptions() = viewModelScope.launch {
        if (purchaseOptions != null || purchaseLoading) return@launch
        purchaseLoading = true; purchaseError = null
        try { purchaseOptions = withContext(Dispatchers.IO) { api.proxyPurchaseOptions(selectedMarketplaceProvider) } }
        catch (failure: Exception) { purchaseError = failure.message ?: "تعذر تحميل خيارات الشراء." }
        finally { purchaseLoading = false }
    }

    fun loadRemoteProxies() = viewModelScope.launch {
        if (remoteProxiesLoading) return@launch
        remoteProxiesLoading = true; remoteProxiesError = null
        try { remoteProxies = withContext(Dispatchers.IO) { api.proxies() } }
        catch (failure: Exception) { remoteProxiesError = failure.message ?: "تعذر تحميل بروكسياتك." }
        finally { remoteProxiesLoading = false }
    }

    fun loadProducts() = viewModelScope.launch {
        if (productsLoading) return@launch
        productsLoading = true
        productsError = null
        try {
            products = withContext(Dispatchers.IO) { api.products() }
        } catch (failure: Exception) {
            productsError = when {
                failure.message.orEmpty().contains("Could not load products", ignoreCase = true) ->
                    "خدمة السوق غير متاحة مؤقتًا. أعد المحاولة بعد قليل."
                failure.message.orEmpty().contains("استجابة السوق", ignoreCase = true) ->
                    "وصل رد غير مكتمل من خدمة السوق. أعد المحاولة."
                else -> "تعذر الاتصال بخدمة السوق. تحقق من الإنترنت ثم أعد المحاولة."
            }
        } finally {
            productsLoading = false
        }
    }

    fun confirmProxyPurchase(options: ProxyPurchaseOptions, plan: ProxyPurchasePlan, location: ProxyPurchaseLocation, username: String, password: String, onSuccess: (PurchasedProxy) -> Unit) = viewModelScope.launch {
        purchaseLoading = true; purchaseError = null
        try {
            val result = withContext(Dispatchers.IO) { api.purchaseProxy(options, plan, location.id, username, password) }
            purchasedProxy = result; purchaseOptions = null; onSuccess(result)
        } catch (failure: Exception) { purchaseError = failure.message ?: "تعذر إتمام شراء البروكسي." }
        finally { purchaseLoading = false }
    }

    fun clearPurchaseError() { purchaseError = null }
    fun closePurchase() { purchaseOptions = null; purchaseError = null; purchaseQuote = null; purchasedProxy = null }

    fun loadProfile() = viewModelScope.launch {
        if (profileLoading) return@launch
        profileLoading = true
        profileError = null
        try {
            profile = withContext(Dispatchers.IO) { api.profile() }
        } catch (failure: Exception) {
            profileError = failure.message ?: "تعذر تحميل بيانات الحساب. أعد المحاولة."
        } finally {
            profileLoading = false
        }
    }

    fun loadSubscriptions() = viewModelScope.launch {
        if (subscriptionsLoading) return@launch
        subscriptionsLoading = true
        subscriptionsError = null
        try {
            subscriptions = withContext(Dispatchers.IO) { api.subscriptions() }
        } catch (failure: Exception) {
            subscriptionsError = failure.message ?: "تعذر تحميل الاشتراكات. أعد المحاولة."
        } finally {
            subscriptionsLoading = false
        }
    }

    fun loadPlansAndWallet() = viewModelScope.launch {
        plansLoading = true; plansError = null
        try {
            val result = withContext(Dispatchers.IO) { listOf(api.plans(), api.wallet(), api.walletTransactions(), api.topUpMethods(), api.topUpRequests()) }
            @Suppress("UNCHECKED_CAST")
            plans = result[0] as List<Plan>; wallet = result[1] as Wallet; walletTransactions = result[2] as List<WalletTransaction>; topUpMethods = result[3] as List<TopUpMethod>; topUpRequests = result[4] as List<TopUpRequest>
            access = withContext(Dispatchers.IO) { api.featureAccess() }
        } catch (failure: Exception) { plansError = failure.message ?: "تعذر تحميل الخطط والمحفظة." } finally { plansLoading = false }
    }

    fun createTopUp(method: TopUpMethod, amount: Double) = viewModelScope.launch {
        topUpLoading = true; topUpError = null
        try { topUpCreated = withContext(Dispatchers.IO) { api.createTopUp(method.code, amount) }; topUpRequests = withContext(Dispatchers.IO) { api.topUpRequests() } }
        catch (failure: Exception) { topUpError = failure.message ?: "تعذر إنشاء طلب الشحن." }
        finally { topUpLoading = false }
    }

    fun verifyTopUp(request: TopUpRequest, reference: String, done: () -> Unit) = viewModelScope.launch {
        topUpLoading = true; topUpError = null
        try { withContext(Dispatchers.IO) { api.verifyTopUp(request.id, reference) }; topUpCreated = null; topUpRequests = withContext(Dispatchers.IO) { api.topUpRequests() }; done() }
        catch (failure: Exception) { topUpError = failure.message ?: "تعذر إرسال عملية التحقق." }
        finally { topUpLoading = false }
    }

    fun clearTopUp() { topUpCreated = null; topUpError = null }

    fun activatePlan(plan: Plan) = viewModelScope.launch {
        try { withContext(Dispatchers.IO) { api.activatePlan(plan.id) }; loadPlansAndWallet(); loadSubscriptions() }
        catch (failure: Exception) { plansError = failure.message ?: "تعذر تفعيل الخطة. تحقق من رصيد المحفظة." }
    }

    fun logout() {
        api.logout()
        loggedIn = false
        products = emptyList()
        productsError = null
        remoteProxies = emptyList(); remoteProxiesError = null
        profile = null
        profileError = null
        subscriptions = emptyList()
        subscriptionsError = null
        plans = emptyList(); wallet = null; walletTransactions = emptyList(); topUpMethods = emptyList(); topUpRequests = emptyList(); topUpCreated = null; access = FeatureAccess(); plansError = null; topUpError = null
        error = null
    }

    fun clearError() { error = null }
}

private class AppViewModelFactory(private val api: ApiClient) : androidx.lifecycle.ViewModelProvider.Factory { override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(api) as T }

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val api = ApiClient(this)
        setContent { ProxyTheme { val vm: AppViewModel = viewModel(factory = AppViewModelFactory(api)); ProxyPlatformApp(vm) } }
    }
}

@Composable private fun ProxyPlatformApp(vm: AppViewModel) { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { if (vm.loggedIn) MainShell(vm) else AuthScreen(vm) } } }
// ─────────────────────────────────────────────────────────────────────────────
// Proxy Screen — local proxy + embedded Wireless ADB flow
// ─────────────────────────────────────────────────────────────────────────────

@Composable internal fun ProxyScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val locationController = remember { ProxyLocationController(context) }
    DisposableEffect(Unit) { onDispose { locationController.close() } }

    // "vpn" = recommended one-tap mode (single system dialog, no Shizuku).
    // "advanced" = original local-proxy mode for users who want no VPN icon.
    var mode by remember {
        mutableStateOf(
            if (ProxyLocalService.isRunning(context) || AdvancedSystemProxy.hasPendingRestore(context))
                "advanced" else "vpn"
        )
    }

    var protocol by remember { mutableStateOf("socks5") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var auth by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val profileStore = remember(context) { ProxyProfileStore(context.applicationContext) }
    var savedProxies by remember { mutableStateOf(emptyList<SavedProxy>()) }
    var selectedSavedProxyId by remember { mutableStateOf<String?>(null) }
    var running by remember {
        mutableStateOf(ProxyLocalService.isRunning(context) || ProxyVpnService.isRunning(context))
    }
    var starting by remember { mutableStateOf(false) }
    var checkingEndpoint by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var mockLocation by remember {
        mutableStateOf(
            context.getSharedPreferences("mock_location_options", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
        )
    }
    var locationMode by remember { mutableStateOf("auto") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var mockLocationAuthorized by remember { mutableStateOf<Boolean?>(null) }
    var mockLocationStatus by remember {
        mutableStateOf(
            if (mockLocation) "اضغط التحقق لاختيار التطبيق قبل الاتصال." else "غير مفعّل"
        )
    }
    var resumeStartAfterCoarsePermission by remember { mutableStateOf(false) }
    var matchProxyTimezone by remember {
        mutableStateOf(
            context.getSharedPreferences("advanced_proxy_timezone_options", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
        )
    }
    var matchProxyLanguage by remember {
        mutableStateOf(
            context.getSharedPreferences("advanced_proxy_language_options", Context.MODE_PRIVATE)
                .getBoolean("enabled", false)
        )
    }
    var wirelessState by remember {
        mutableStateOf(WirelessDebuggingManager.DebuggingState.NOT_PAIRED)
    }
    var showPairingNotificationOnGrant by remember { mutableStateOf(false) }

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (showPairingNotificationOnGrant && granted) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled() ||
                !WirelessDebuggingManager.showPairingNotification(context)
            ) {
                error = "الإشعارات متوقفة لهذا التطبيق؛ أدخل رمز الاقتران يدويًا من التطبيق."
            }
        } else if (showPairingNotificationOnGrant) {
            error = "لم يُمنح إذن الإشعارات؛ أدخل رمز الاقتران يدويًا من التطبيق."
        }
        showPairingNotificationOnGrant = false
    }

    val coarseLocationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            mockLocationStatus = "تم منح إذن الموقع التقريبي اللازم لخدمات Google؛ جارٍ استكمال الاتصال…"
            error = null
            resumeStartAfterCoarsePermission = true
        } else {
            resumeStartAfterCoarsePermission = false
            mockLocationStatus = "يلزم إذن الموقع التقريبي كي تستقبل خدمات Google إحداثيات الموقع الوهمي."
            error = "لم يُمنح إذن الموقع التقريبي؛ لم يبدأ الاتصال ولم نقرأ موقعك الحقيقي."
        }
    }

    // Best-effort request used by the plain VPN/local-proxy status notification.
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun ensurePairingNotification() {
        if (hasNotificationPermission()) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                error = "إشعارات التطبيق متوقفة من إعدادات Android؛ استخدم إدخال الرمز اليدوي أو فعّل الإشعارات."
            } else if (!WirelessDebuggingManager.showPairingNotification(context)) {
                error = "تعذر عرض إشعار الاقتران؛ استخدم إدخال الرمز اليدوي."
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            showPairingNotificationOnGrant = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            error = "تعذر عرض الإشعار؛ استخدم إدخال رمز الاقتران اليدوي."
        }
    }

    fun openDeveloperOptions() {
        runCatching {
            context.startActivity(WirelessDebuggingManager.getDebuggingSettingsIntent(context))
        }.onFailure {
            error = ProxyFailureMessages.settingsPage("خيارات المطوّر")
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val mockLocationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (mockLocation) {
            mockLocationStatus = "جارٍ التحقق من صلاحية الموقع الوهمي…"
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    locationController.checkMockLocationAccess()
                }
                mockLocationAuthorized = result.isSuccess
                val cause = result.exceptionOrNull()
                mockLocationStatus = when {
                    result.isSuccess -> "تم اختيار التطبيق؛ صلاحية الموقع الوهمي جاهزة."
                    cause is SecurityException -> "لم يتم اختيار التطبيق بعد. اختر Proxy Platform ثم ارجع إلى التطبيق."
                    else -> "تعذر التحقق من إعداد الموقع الوهمي؛ راجع سبب المشكلة أدناه."
                }
                if (result.isFailure) {
                    error = ProxyFailureMessages.mockLocation(
                        if (cause is SecurityException) "mock location app not selected: ${cause.message.orEmpty()}"
                        else cause?.message
                    )
                    AdvancedOperationLog.exception(
                        context,
                        "فشل التحقق من اختيار Proxy Platform كتطبيق موقع وهمي",
                        cause ?: IllegalStateException("App-op غير متاح"),
                    )
                }
            }
        }
    }

    fun openMockLocationSettings() {
        error = null
        runCatching {
            mockLocationSettingsLauncher.launch(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            )
        }.onFailure {
            error = ProxyFailureMessages.settingsPage("خيارات المطوّر ثم اختيار تطبيق الموقع الوهمي")
        }
    }

    fun selectProxyProfile(profile: SavedProxy) {
        if (running || starting || stopping) return
        selectedSavedProxyId = profile.id
        protocol = profile.protocol
        host = profile.host
        port = profile.port.toString()
        auth = profile.authenticationRequired
        username = profile.username
        password = profile.password
        coroutineScope.launch {
            runCatching { withContext(Dispatchers.IO) { profileStore.select(profile.id) } }
                .onFailure { error = ProxyFailureMessages.savedProxy(it.message, "حفظ اختيار البروكسي") }
        }
    }

    fun saveProxyProfile(candidate: SavedProxy) {
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val saved = profileStore.save(candidate)
                    profileStore.loadAll() to saved
                }
            }.onSuccess { (profiles, saved) ->
                savedProxies = profiles
                selectedSavedProxyId = saved.id
                protocol = saved.protocol
                host = saved.host
                port = saved.port.toString()
                auth = saved.authenticationRequired
                username = saved.username
                password = saved.password
                error = null
                AdvancedOperationLog.output(context, "حُفظ إعداد بروكسي محليًا في قاعدة البيانات المشفرة.")
            }.onFailure { error = ProxyFailureMessages.savedProxy(it.message, "حفظ البروكسي") }
        }
    }

    fun deleteProxyProfile(profile: SavedProxy) {
        if (running || starting || stopping) return
        coroutineScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    profileStore.delete(profile.id)
                    profileStore.loadAll()
                }
            }.onSuccess { profiles ->
                savedProxies = profiles
                val selectedProfile = profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()
                selectedSavedProxyId = selectedProfile?.id
                if (selectedProfile != null) selectProxyProfile(selectedProfile)
                else {
                    selectedSavedProxyId = null
                    host = ""; port = ""; username = ""; password = ""; auth = false
                }
            }.onFailure { error = ProxyFailureMessages.savedProxy(it.message, "حذف البروكسي المحفوظ") }
        }
    }

    LaunchedEffect(Unit) {
        if (!ProxyVpnService.isRunning(context) && !ProxyLocalService.isRunning(context)) {
            ProxyLocationController.recoverStaleFusedMockMode(context)
        }
        runCatching { withContext(Dispatchers.IO) { profileStore.loadAll() } }
            .onSuccess { profiles ->
                savedProxies = profiles
                val selectedProfile = profiles.firstOrNull { it.selected } ?: profiles.firstOrNull()
                if (selectedProfile != null) {
                    selectedSavedProxyId = selectedProfile.id
                    protocol = selectedProfile.protocol
                    host = selectedProfile.host
                    port = selectedProfile.port.toString()
                    auth = selectedProfile.authenticationRequired
                    username = selectedProfile.username
                    password = selectedProfile.password
                }
            }
            .onFailure {
                error = ProxyFailureMessages.savedProxy(it.message, "فتح قاعدة بيانات البروكسيات المحفوظة")
                AdvancedOperationLog.error(context, "تعذر تحميل بيانات اعتماد البروكسي المشفرة.")
            }
        if (!ProxyLocalService.isRunning(context) &&
            (AdvancedSystemProxy.hasPendingRestore(context) ||
                AdvancedProxyTimezoneController.hasPendingRestore(context) ||
                AdvancedProxyLanguageController.hasPendingRestore(context))
        ) {
            AdvancedOperationLog.info(context, "اكتشاف جلسة متقدمة سابقة؛ محاولة استعادة اللغة والمنطقة الزمنية والبروكسي.")
            val languageRestore = withContext(Dispatchers.IO) {
                AdvancedProxyLanguageController.restore(context)
            }
            val timezoneRestore = withContext(Dispatchers.IO) {
                AdvancedProxyTimezoneController.restore(context)
            }
            val proxyRestore = withContext(Dispatchers.IO) {
                AdvancedSystemProxy.restore(
                    context,
                    "127.0.0.1:${ProxyLocalService.localPort(context)}"
                )
            }
            if (languageRestore.isFailure || timezoneRestore.isFailure || proxyRestore.isFailure) {
                AdvancedOperationLog.error(context, "تعذرت استعادة الجلسة السابقة تلقائيًا؛ يلزم اتصال ADB.")
                val failure = languageRestore.exceptionOrNull() ?: timezoneRestore.exceptionOrNull() ?: proxyRestore.exceptionOrNull()
                error = ProxyFailureMessages.advancedRestore(failure?.message)
            } else {
                AdvancedOperationLog.info(context, "اكتملت معالجة استعادة الجلسة السابقة.")
                mode = "vpn"
            }
        }
    }

    LaunchedEffect(mode) {
        if (mode == "advanced") {
            while (true) {
                val newWirelessState = withContext(Dispatchers.IO) {
                    WirelessDebuggingManager.checkState(context)
                }
                if (newWirelessState != wirelessState) {
                    AdvancedOperationLog.info(context, "حالة Wireless ADB: ${newWirelessState.name}.")
                    wirelessState = newWirelessState
                }
                delay(1_000)
            }
        }
    }

    fun startLocation() {
        if (!mockLocation) {
            mockLocationStatus = "غير مفعّل"
            return
        }
        mockLocationStatus = "جارٍ تحديد الموقع وتفعيله…"
        val onStatus: (Result<Pair<Double, Double>>) -> Unit = { result ->
            coroutineScope.launch(Dispatchers.Main.immediate) {
                result.fold(
                    onSuccess = { coordinates ->
                        mockLocationAuthorized = true
                        mockLocationStatus = "موقع الخرائط الوهمي فعّال (Fused) — ${"%.4f".format(coordinates.first)}, ${"%.4f".format(coordinates.second)}."
                    },
                    onFailure = { failure ->
                        if (failure is SecurityException) mockLocationAuthorized = false
                        mockLocationStatus = if (failure is SecurityException) {
                            "Android لم يعتمد اختيار التطبيق؛ افتح خيارات المطوّر واختر Proxy Platform."
                        } else {
                            "تعذر إرسال الإحداثيات؛ راجع سبب المشكلة وخطوات الإصلاح أدناه."
                        }
                        AdvancedOperationLog.exception(context, "تعذر تشغيل/تحديث الموقع الوهمي", failure)
                        error = ProxyFailureMessages.mockLocation(
                            if (failure is SecurityException) "mock location app not selected: ${failure.message.orEmpty()}"
                            else "${failure.javaClass.simpleName}: ${failure.message.orEmpty()}"
                        )
                    },
                )
            }
        }
        runCatching {
            if (locationMode == "auto") {
                val localPort = if (mode == "advanced") {
                    ProxyLocalService.localPort(context)
                } else {
                    ProxyVpnService.MOCK_LOCATION_PROXY_PORT
                }
                locationController.startAutoThroughLocalProxy(localPort, onStatus)
            } else {
                locationController.startManual(latitude.toDouble(), longitude.toDouble(), onStatus)
            }
        }.onFailure { onStatus(Result.failure(it)) }
    }

    // ── Advanced mode: local proxy + embedded Wireless ADB ─────────────
    fun launchAdvancedTunnel() {
        error = null
        starting = true
        running = false
        AdvancedOperationLog.info(context, "========== بدء تشغيل البروكسي المتقدم ==========")
        AdvancedOperationLog.info(
            context,
            "الخيارات: protocol=${protocol.uppercase()}, port=${port.toIntOrNull() ?: "invalid"}, authentication=$auth؛ بيانات الاعتماد لا تُسجّل."
        )
        ProxyLocalService.clearLastError(context)
        runCatching {
            AdvancedOperationLog.info(context, "إرسال طلب تشغيل خدمة البروكسي المحلي إلى Android.")
            startLocalProxyService(context, protocol, host, port, username, password)
        }.onFailure {
            AdvancedOperationLog.exception(context, "تعذر إرسال طلب تشغيل خدمة البروكسي", it)
            starting = false
            error = ProxyFailureMessages.fromThrowable(advanced = true, failure = it)
            return
        }

        // Keep the UI in "starting" until both the local inbound and Android's
        // system proxy have been verified. A service-running flag alone is not
        // enough to tell the user that device traffic is actually routed.
        coroutineScope.launch {
            val setupResult = withContext(Dispatchers.IO) {
                AdvancedOperationLog.info(context, "انتظار بدء الخدمة واستجابة المنفذ المحلي (مهلة 10 ثوانٍ).")
                var ready = false
                for (attempt in 0 until 40) {
                    if (ProxyLocalService.isRunning(context) && ProxyLocalService.isListenerReady(context)) {
                        ready = true
                        break
                    }
                    delay(250)
                }
                if (!ready || !ProxyLocalService.isRunning(context) || !ProxyLocalService.isListenerReady(context)) {
                    AdvancedOperationLog.error(context, "لم تصبح خدمة البروكسي والمنفذ المحلي جاهزين خلال المهلة.")
                    Result.failure(
                        IllegalStateException(
                            ProxyLocalService.lastError(context) ?: "لم تبدأ خدمة البروكسي المحلي."
                        )
                    )
                } else {
                    val expectedProxy = "127.0.0.1:${ProxyLocalService.localPort(context)}"
                    val proxyTimezone = if (matchProxyTimezone) {
                        runCatching {
                            locationController.fetchProxyTimezone(ProxyLocalService.localPort(context))
                        }.getOrElse {
                            AdvancedOperationLog.exception(context, "فشل اكتشاف المنطقة الزمنية عبر مخرج sing-box المحلي", it)
                            return@withContext Result.failure(it)
                        }
                    } else {
                        null
                    }
                    val proxyLanguage = if (matchProxyLanguage) {
                        runCatching {
                            locationController.fetchProxyLanguage(ProxyLocalService.localPort(context))
                        }.getOrElse {
                            AdvancedOperationLog.exception(context, "فشل اكتشاف لغة الهاتف عبر مخرج sing-box المحلي", it)
                            return@withContext Result.failure(it)
                        }
                    } else {
                        null
                    }
                    AdvancedOperationLog.info(context, "أصبح المنفذ المحلي جاهزًا؛ بدء ضبط بروكسي نظام Android.")
                    val proxyResult = AdvancedSystemProxy.apply(context, expectedProxy)
                    val result = if (proxyResult.isFailure) {
                        proxyResult
                    } else {
                        val timezoneResult = if (proxyTimezone != null) AdvancedProxyTimezoneController.apply(context, proxyTimezone) else Result.success(Unit)
                        if (timezoneResult.isFailure) timezoneResult else if (proxyLanguage != null) AdvancedProxyLanguageController.apply(context, proxyLanguage) else Result.success(Unit)
                    }
                    if (result.isSuccess &&
                        (!ProxyLocalService.isRunning(context) || !ProxyLocalService.isListenerReady(context))
                    ) {
                        AdvancedOperationLog.error(context, "توقفت الخدمة أو المنفذ أثناء تطبيق إعداد النظام.")
                        val languageRestore = AdvancedProxyLanguageController.restore(context)
                        val timezoneRestore = AdvancedProxyTimezoneController.restore(context)
                        val proxyRestore = AdvancedSystemProxy.restore(context, expectedProxy)
                        val failure = languageRestore.exceptionOrNull() ?: timezoneRestore.exceptionOrNull() ?: proxyRestore.exceptionOrNull()
                        Result.failure(
                            IllegalStateException(
                                failure?.message ?: "توقفت خدمة البروكسي أثناء إعداد النظام."
                            )
                        )
                    } else {
                        result
                    }
                }
            }

            if (setupResult.isSuccess) {
                AdvancedOperationLog.output(context, "اكتمل تشغيل الوضع المتقدم بنجاح.")
                running = true
                starting = false
                startLocation()
            } else {
                setupResult.exceptionOrNull()?.let {
                    AdvancedOperationLog.exception(context, "فشل تشغيل الوضع المتقدم", it)
                }
                val restoreResult = withContext(Dispatchers.IO) {
                    val languageRestore = AdvancedProxyLanguageController.restore(context)
                    val timezoneRestore = AdvancedProxyTimezoneController.restore(context)
                    val proxyRestore = if (AdvancedSystemProxy.hasPendingRestore(context)) {
                        AdvancedSystemProxy.restore(context, "127.0.0.1:${ProxyLocalService.localPort(context)}")
                    } else Result.success(Unit)
                    if (languageRestore.isFailure) languageRestore else if (timezoneRestore.isFailure) timezoneRestore else proxyRestore
                }
                if (restoreResult.isSuccess) {
                    AdvancedOperationLog.info(context, "اكتملت معالجة الاستعادة؛ إيقاف الخدمة المحلية.")
                    ProxyLocalService.stop(context)
                    running = false
                } else {
                    // Keep the loopback listener alive if Android may still
                    // point at it; stopping it could strand all device traffic.
                    running = ProxyLocalService.isRunning(context)
                }
                starting = false
                val cause = setupResult.exceptionOrNull()
                error = ProxyFailureMessages.connection(advanced = true, details = cause?.message)
                if (restoreResult.isFailure) {
                    AdvancedOperationLog.error(context, "فشلت الاستعادة؛ أُبقيت الخدمة المحلية لتفادي انقطاع المرور.")
                    error += "\nتنبيه: لم تكتمل الاستعادة، لذلك أبقينا البروكسي نشطًا لتجنّب قطع المرور. أعد اتصال ADB ثم حاول الاستعادة مجددًا."
                }
            }
        }
    }

    fun requestStartAdvanced() {
        if (wirelessState != WirelessDebuggingManager.DebuggingState.READY) {
            AdvancedOperationLog.error(context, "رُفض بدء الوضع المتقدم: اتصال Wireless ADB غير جاهز.")
            error = ProxyFailureMessages.connection(
                advanced = true,
                details = "Wireless debugging is not paired or connected",
            )
            return
        }
        launchAdvancedTunnel()
    }

    fun stopAdvanced() {
        if (stopping) return
        stopping = true
        AdvancedOperationLog.info(context, "========== طلب إيقاف البروكسي المتقدم ==========")
        val expectedProxy = "127.0.0.1:${ProxyLocalService.localPort(context)}"
        coroutineScope.launch(Dispatchers.IO) {
            val languageRestore = AdvancedProxyLanguageController.restore(context)
            val timezoneRestore = AdvancedProxyTimezoneController.restore(context)
            val restoreResult = if (languageRestore.isSuccess && timezoneRestore.isSuccess) {
                AdvancedSystemProxy.restore(context, expectedProxy)
            } else if (languageRestore.isFailure) languageRestore else timezoneRestore
            withContext(Dispatchers.Main) {
                stopping = false
                if (restoreResult.isSuccess) {
                    AdvancedOperationLog.output(context, "استُعيدت لغة الهاتف والمنطقة الزمنية والبروكسي؛ جارٍ إيقاف خدمة البروكسي المحلي.")
                    locationController.stop()
                    if (mockLocation) mockLocationStatus = "تم إيقاف الموقع الوهمي."
                    ProxyLocalService.stop(context)
                    running = false
                } else {
                    AdvancedOperationLog.error(context, "تعذر إيقاف آمن: فشلت استعادة إعدادات البروكسي أو المنطقة الزمنية.")
                    error = ProxyFailureMessages.advancedRestore(restoreResult.exceptionOrNull()?.message)
                }
            }
        }
    }

    // ── Recommended mode: one-tap VpnService tunnel ─────────────────────────
    fun launchVpnTunnel() {
        error = null
        starting = true
        ProxyVpnService.clearLastError(context)
        ensureNotificationPermission()
        runCatching {
            ProxyVpnService.start(
                context,
                protocol,
                host,
                port,
                username,
                password,
                includeMockLocationInbound = mockLocation && locationMode == "auto",
            )
        }.onFailure {
            starting = false
            error = ProxyFailureMessages.fromThrowable(advanced = false, failure = it)
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) launchVpnTunnel()
        else {
            starting = false
            error = ProxyFailureMessages.connection(false, "VPN permission was denied")
        }
    }

    fun requestStartVpn() {
        // Don't flip `starting` yet: VpnService.prepare() may show an async,
        // user-driven system dialog first. `starting` (and its 1.5s polling
        // window below) only starts once launchVpnTunnel() actually runs.
        val consent = VpnService.prepare(context)
        if (consent != null) vpnPermissionLauncher.launch(consent) else launchVpnTunnel()
    }

    fun stopVpn() {
        locationController.stop()
        if (mockLocation) mockLocationStatus = "تم إيقاف الموقع الوهمي."
        ProxyVpnService.stop(context)
        running = false
    }

    fun requestStart() {
        val parsedPort = port.toIntOrNull()
        if (selectedSavedProxyId == null) {
            error = "أضف بروكسيًا واحفظه ثم اختره قبل بدء الاتصال."
            return
        }
        if (host.isBlank() || parsedPort !in 1..65535 || (auth && username.isBlank())) {
            error = "أدخل المضيف والمنفذ وبيانات المصادقة بشكل صحيح."
            return
        }
        if (auth && password.isBlank()) {
            error = "أدخل كلمة مرور البروكسي أو أوقف خيار المصادقة."
            return
        }
        if (mockLocation && locationMode == "manual") {
            val parsedLatitude = latitude.toDoubleOrNull()
            val parsedLongitude = longitude.toDoubleOrNull()
            if (parsedLatitude == null || !parsedLatitude.isFinite() || parsedLatitude !in -90.0..90.0) {
                error = "أدخل خط عرض صحيحًا بين -90 و90."
                return
            }
            if (parsedLongitude == null || !parsedLongitude.isFinite() || parsedLongitude !in -180.0..180.0) {
                error = "أدخل خط طول صحيحًا بين -180 و180."
                return
            }
        }
        if (mockLocation && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            mockLocationStatus = "إذن الموقع التقريبي مطلوب فقط لتسليم الإحداثيات الوهمية إلى Google Maps."
            error = "سيطلب Android إذن الموقع التقريبي؛ التطبيق لا يقرأ موقعك الحقيقي. لن يبدأ الاتصال قبل موافقتك."
            coarseLocationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            return
        }
        error = null
        checkingEndpoint = true
        AdvancedOperationLog.info(
            context,
            "فحص البروكسي قبل بدء الوضع=${mode.uppercase()}، endpoint=$host:$parsedPort، protocol=${protocol.uppercase()}، auth=$auth، target=1.1.1.1:443، TCP/read timeout=${ProxyEndpointProbe.DEFAULT_TIMEOUT_MS}ms؛ لا تُسجّل كلمات المرور."
        )
        coroutineScope.launch {
            if (mockLocation) {
                mockLocationStatus = "جارٍ التحقق من اختيار التطبيق للموقع الوهمي…"
                val mockAccess = withContext(Dispatchers.IO) {
                    locationController.checkMockLocationAccess()
                }
                mockLocationAuthorized = mockAccess.isSuccess
                if (mockAccess.isFailure) {
                    checkingEndpoint = false
                    val cause = mockAccess.exceptionOrNull()
                    val permissionDenied = cause is SecurityException
                    mockLocationStatus = if (permissionDenied) {
                        "اختر Proxy Platform من إعداد اختيار تطبيق الموقع الوهمي."
                    } else {
                        "تعذر التحقق من إعداد الموقع الوهمي؛ راجع سبب المشكلة وخطوة الإصلاح أدناه."
                    }
                    if (cause != null) {
                        AdvancedOperationLog.exception(context, "لم يُسمح للتطبيق بإنشاء test location provider", cause)
                    }
                    error = ProxyFailureMessages.mockLocation(
                        if (permissionDenied) "mock location app not selected: ${cause?.message.orEmpty()}"
                        else cause?.message
                    )
                    return@launch
                }
                mockLocationStatus = "تم التحقق من صلاحية الموقع الوهمي."
            }
            val probe = withContext(Dispatchers.IO) {
                ProxyEndpointProbe.check(
                    host,
                    parsedPort!!,
                    protocol,
                    if (auth) username else "",
                    if (auth) password else "",
                    trace = { stage -> AdvancedOperationLog.info(context, "فحص البروكسي: $stage") },
                )
            }
            if (probe.isFailure) {
                checkingEndpoint = false
                val failure = probe.exceptionOrNull()
                AdvancedOperationLog.exception(context, "فشل فحص البروكسي قبل إنشاء النفق؛ لم تتغير إعدادات Android", failure ?: IllegalStateException("سبب غير معروف"))
                error = ProxyFailureMessages.connection(mode == "advanced", failure?.message)
                return@launch
            }

            if (mode == "vpn" && protocol == "socks5") {
                AdvancedOperationLog.info(
                    context,
                    "فحص UDP relay في SOCKS5 قبل طلب إذن VPN؛ WebRTC يعتمد على مرور UDP، ولن يبدأ النفق إذا لم يمنح الخادم UDP ASSOCIATE."
                )
                val udpProbe = withContext(Dispatchers.IO) {
                    ProxyEndpointProbe.checkSocks5UdpAssociation(
                        host,
                        parsedPort!!,
                        if (auth) username else "",
                        if (auth) password else "",
                        trace = { stage -> AdvancedOperationLog.info(context, "فحص SOCKS5 UDP: $stage") },
                    )
                }
                if (udpProbe.isFailure) {
                    checkingEndpoint = false
                    val failure = udpProbe.exceptionOrNull()
                    if (failure != null) AdvancedOperationLog.exception(context, "فشل اختبار SOCKS5 UDP relay", failure)
                    error = ProxyFailureMessages.connection(false, failure?.message ?: "SOCKS5 UDP ASSOCIATE was rejected")
                    return@launch
                }
            }

            checkingEndpoint = false
            if (mode == "vpn") requestStartVpn() else requestStartAdvanced()
        }
    }

    LaunchedEffect(resumeStartAfterCoarsePermission) {
        if (resumeStartAfterCoarsePermission) {
            resumeStartAfterCoarsePermission = false
            requestStart()
        }
    }

    fun stopProxy() {
        if (mode == "vpn") stopVpn() else stopAdvanced()
    }

    LaunchedEffect(starting, mode) {
        if (starting && mode == "vpn") {
            delay(1500)
            running = ProxyVpnService.isRunning(context)
            if (!running) {
                error = ProxyFailureMessages.connection(
                    advanced = false,
                    details = ProxyVpnService.lastError(context) ?: "VPN service failed to start",
                )
            } else {
                startLocation()
            }
            starting = false
        }
    }

    Box(Modifier.fillMaxSize().padding(padding)) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 142.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── Connection mode ────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("طريقة الاتصال", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    ModeOptionCard(
                        title = "VPN",
                        subtitle = "إعداد سريع ومُوصى به",
                        selected = mode == "vpn",
                        enabled = !running && !starting && !checkingEndpoint,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = "vpn"; error = null },
                    )
                    ModeOptionCard(
                        title = "متقدم",
                        subtitle = "بروكسي النظام عبر ADB",
                        selected = mode == "advanced",
                        enabled = !running && !starting && !checkingEndpoint,
                        modifier = Modifier.weight(1f),
                        onClick = { mode = "advanced"; error = null },
                    )
                }
                Text(
                    if (mode == "vpn")
                        "اضغط اتصال ووافق على نافذة Android الوحيدة — من دون إعدادات مطوّر أو تطبيقات إضافية."
                    else
                        "يتطلب إعداد التصحيح اللاسلكي مرة واحدة، ويعمل مع التطبيقات التي تحترم بروكسي النظام.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (mode == "vpn") {
                    Text(
                        if (protocol == "socks5")
                            "WebRTC / UDP: يمر عبر SOCKS5 داخل نفق VPN. نتحقق أولًا من UDP ASSOCIATE، ولا نسمح بمسار مباشر بديل."
                        else
                            "بروكسي HTTP لا ينقل UDP الخاص بـ WebRTC؛ اختر SOCKS5 يدعم UDP إذا أردت مكالمات WebRTC عبر البروكسي.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (protocol == "socks5") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        // ── Saved proxy profiles: available in both connection modes ──────────
        item {
            SavedProxyManagerCard(
                profiles = savedProxies,
                selectedId = selectedSavedProxyId,
                enabled = !running && !starting && !checkingEndpoint && !stopping,
                onSelect = { selectProxyProfile(it) },
                onSave = { saveProxyProfile(it) },
                onDelete = { deleteProxyProfile(it) },
            )
        }

        // ── Advanced proxy setup (embedded Wireless ADB)
        if (mode == "advanced") item {
            var pairingCode by remember { mutableStateOf("") }
            var pairing by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()

            SetupStepCard(
                stepNumber = 2,
                title = "الوضع المتقدم: بروكسي النظام",
                subtitle = "ADB مضمّن؛ يعمل مع التطبيقات التي تحترم بروكسي النظام، وقد تتجاوزه بعض التطبيقات.",
                isComplete = wirelessState == WirelessDebuggingManager.DebuggingState.READY,
                statusText = when (wirelessState) {
                    WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> "Android 11 أو أحدث مطلوب"
                    WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> "وضع المطور معطل"
                    WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> "التصحيح اللاسلكي معطل"
                    WirelessDebuggingManager.DebuggingState.NOT_PAIRED -> "بانتظار رمز الاقتران"
                    WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> "جارٍ الاتصال"
                    WirelessDebuggingManager.DebuggingState.READY -> "جاهز"
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (wirelessState) {
                        WirelessDebuggingManager.DebuggingState.UNSUPPORTED_ANDROID_VERSION -> {
                            Text(
                                "يتطلب الوضع المتقدم Android 11 أو أحدث لدعم التصحيح اللاسلكي المضمّن. استخدم وضع VPN بلمسة واحدة على هذا الجهاز.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        WirelessDebuggingManager.DebuggingState.DEVELOPER_DISABLED -> {
                            Text("الخطوة 1: فعّل خيارات المطور من إعدادات الهاتف.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Button(onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }, Modifier.fillMaxWidth()) { Text("فتح الإعدادات") }
                        }
                        WirelessDebuggingManager.DebuggingState.WIRELESS_DISABLED -> {
                            Text("الخطوة 2: افتح خيارات المطوّر وفعّل Wireless debugging.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            Button(onClick = ::openDeveloperOptions, Modifier.fillMaxWidth()) { Text("فتح إعدادات التصحيح اللاسلكي") }
                        }
                        WirelessDebuggingManager.DebuggingState.NOT_PAIRED,
                        WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED -> {
                            Text(
                                if (wirelessState == WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED)
                                    "الاقتران محفوظ لكن اتصال ADB انقطع. جرّب إعادة الاتصال، أو أعد الاقتران إذا ألغيت الجهاز من إعدادات Android."
                                else
                                    "افتح Wireless debugging ثم Pair device with pairing code. أدخل الرمز من إشعار التطبيق أو اكتبه هنا يدويًا.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = ::openDeveloperOptions,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !pairing,
                            ) { Text("فتح خيارات المطوّر") }
                            OutlinedButton(
                                onClick = ::ensurePairingNotification,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !pairing,
                            ) { Text("إظهار إشعار إدخال رمز الاقتران") }
                            OutlinedTextField(
                                value = pairingCode,
                                onValueChange = { value -> pairingCode = PairingCodeInput.digitsOnly(value).take(6) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("رمز الاقتران (6 أرقام فقط)") },
                                singleLine = true,
                                enabled = !pairing,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            Button(
                                onClick = {
                                    pairing = true
                                    error = null
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) { WirelessDebuggingManager.pair(context, pairingCode) }
                                        pairing = false
                                        result.onSuccess {
                                            pairingCode = ""
                                            wirelessState = WirelessDebuggingManager.DebuggingState.READY
                                            error = null
                                        }.onFailure {
                                            error = ProxyFailureMessages.pairing(it.message)
                                            wirelessState = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.checkState(context)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = pairingCode.length == 6 && !pairing
                            ) {
                                if (pairing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("اقتران واتصال تلقائي")
                            }
                            if (wirelessState == WirelessDebuggingManager.DebuggingState.PAIRED_NOT_CONNECTED) {
                                OutlinedButton(
                                    onClick = {
                                        pairing = true
                                        error = null
                                        scope.launch {
                                            val connected = withContext(Dispatchers.IO) {
                                                WirelessDebuggingManager.connect(context, 0)
                                            }
                                            pairing = false
                                            if (connected) {
                                                wirelessState = WirelessDebuggingManager.DebuggingState.READY
                                            } else {
                                                error = ProxyFailureMessages.pairing("Wireless debugging reconnect failed")
                                                wirelessState = withContext(Dispatchers.IO) {
                                                    WirelessDebuggingManager.checkState(context)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !pairing
                                ) { Text(if (pairing) "جارٍ إعادة الاتصال…" else "إعادة الاتصال عبر ADB") }
                            }
                            Text("لا تدخل IP أو أي منفذ؛ سيتم اكتشافهما تلقائيًا.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        WirelessDebuggingManager.DebuggingState.READY -> {
                            Text("تم الاقتران والاتصال عبر ADB المضمّن بنجاح.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = SuccessGreen)
                            Text("يمكنك الآن تشغيل الوضع المتقدم.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (mode == "advanced") item {
            KUNCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = matchProxyTimezone,
                            onCheckedChange = { enabled ->
                                matchProxyTimezone = enabled
                                context.getSharedPreferences(
                                    "advanced_proxy_timezone_options",
                                    Context.MODE_PRIVATE,
                                ).edit().putBoolean("enabled", enabled).apply()
                                error = null
                            },
                            enabled = !running && !starting && !checkingEndpoint && !stopping,
                        )
                        Text(
                            "تعديل المنطقة الزمنية بما يناسب البروكسي",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        "عند الاتصال، يحدد التطبيق المنطقة الزمنية لبلد خروج البروكسي عبر اتصال البروكسي ويطبقها على الهاتف. تُحفظ منطقتك الزمنية الحالية وإعداد الضبط التلقائي وتُستعاد عند إيقاف البروكسي.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "تُرسل خدمة تحديد الموقع طلب GeoIP إلى ipwho.is عبر البروكسي المختار. عند تفعيل مطابقة اللغة سيغيّر التطبيق لغة الهاتف كاملة عبر ADB ويحفظها لاستعادتها عند الإيقاف.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = matchProxyLanguage,
                            onCheckedChange = { enabled ->
                                matchProxyLanguage = enabled
                                context.getSharedPreferences("advanced_proxy_language_options", Context.MODE_PRIVATE)
                                    .edit().putBoolean("enabled", enabled).apply()
                                error = null
                            },
                            enabled = !running && !starting && !checkingEndpoint && !stopping,
                        )
                        Text("تعديل لغة الهاتف بما يناسب البروكسي", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "يحدد اللغة تلقائيًا من دولة خروج البروكسي، ويطبّقها على الهاتف والتطبيقات عبر Wireless ADB. يجب إبقاء اتصال ADB جاهزًا، وقد تعيد بعض التطبيقات إنشاء واجهتها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── Mock location ────────────────────────────────────────────────────
        item {
            KUNCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("موقع وهمي اختياري", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = mockLocation,
                            onCheckedChange = { enabled ->
                                mockLocation = enabled
                                mockLocationAuthorized = null
                                mockLocationStatus = if (enabled) {
                                    "افتح خطوة الإعداد أدناه واختر هذا التطبيق قبل الاتصال."
                                } else {
                                    "غير مفعّل"
                                }
                                context.getSharedPreferences("mock_location_options", Context.MODE_PRIVATE)
                                    .edit().putBoolean("enabled", enabled).apply()
                                if (!enabled) locationController.stop()
                                error = null
                            },
                            enabled = !running && !starting && !checkingEndpoint && !stopping,
                        )
                        Text("تفعيل الموقع الوهمي")
                    }
                    if (mockLocation) {
                        Text(
                            "خطوة الإعداد: افتح خيارات المطوّر ← اختيار تطبيق الموقع الوهمي ← Proxy Platform. Android يطلب اختيار التطبيق يدويًا ولا يسمح للتطبيق بتجاوز شاشة الاختيار.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            mockLocationStatus,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = when (mockLocationAuthorized) {
                                true -> SuccessGreen
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        OutlinedButton(
                            onClick = ::openMockLocationSettings,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !running && !starting && !checkingEndpoint && !stopping,
                        ) {
                            Text(if (mockLocationAuthorized == true) "إعادة التحقق من اختيار التطبيق" else "اختيار تطبيق الموقع الوهمي")
                        }
                        Text(
                            "عند الاتصال سيطلب Android إذن الموقع التقريبي فقط لإرسال الموقع الوهمي إلى Google Maps؛ التطبيق لا يقرأ موقعك الحقيقي ولا يطلب موقعًا دقيقًا.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = locationMode == "auto",
                            onClick = { locationMode = "auto" },
                            label = { Text("تلقائي") },
                            enabled = !running && !starting && !checkingEndpoint && !stopping,
                        )
                        FilterChip(
                            selected = locationMode == "manual",
                            onClick = { locationMode = "manual" },
                            label = { Text("يدوي") },
                            enabled = !running && !starting && !checkingEndpoint && !stopping,
                        )
                    }
                    if (locationMode == "manual") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = latitude,
                                onValueChange = { latitude = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("خط العرض") },
                                singleLine = true,
                                enabled = !running && !starting && !checkingEndpoint && !stopping,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            OutlinedTextField(
                                value = longitude,
                                onValueChange = { longitude = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("خط الطول") },
                                singleLine = true,
                                enabled = !running && !starting && !checkingEndpoint && !stopping,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
            }
        }

        if (mode == "advanced" && !running && !starting &&
            (AdvancedSystemProxy.hasPendingRestore(context) ||
                AdvancedProxyTimezoneController.hasPendingRestore(context))
        ) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "توجد جلسة متقدمة سابقة لم تكتمل استعادة إعداداتها. أعد اتصال ADB ثم استعد المنطقة الزمنية والبروكسي قبل بدء جلسة جديدة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = ::stopAdvanced,
                    enabled = !stopping,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (stopping) "جارٍ الاستعادة…" else "استعادة إعدادات الجلسة السابقة") }
            }
        }

    }

    val hasDetails = selectedSavedProxyId != null && host.isNotBlank() && port.isNotBlank()
    val canConnect = hasDetails && (mode == "vpn" || wirelessState == WirelessDebuggingManager.DebuggingState.READY)
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(13.dp)) {
                    Text(error.orEmpty(), Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                }
            }
            Button(
                onClick = { if (running) stopProxy() else requestStart() },
                enabled = if (running) !stopping else !starting && !checkingEndpoint && !stopping && canConnect,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = if (running) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors(),
            ) {
                if (starting || checkingEndpoint || stopping) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(
                        when { running && mode == "vpn" -> "قطع اتصال VPN"; running -> "إيقاف البروكسي"; mode == "vpn" -> "اتصال"; else -> "بدء بروكسي الجهاز" },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(8.dp).background(if (running) SuccessGreen else if (starting || checkingEndpoint || stopping) KunPalette.Warning else KunPalette.Muted.copy(alpha = 0.55f), CircleShape))
                Text(
                    when { running -> "متصل"; checkingEndpoint -> "جارٍ فحص إمكانية الوصول إلى الخادم…"; starting -> if (mode == "vpn") "بانتظار إذن Android…" else "جارٍ تشغيل البروكسي المحلي…"; stopping -> "جارٍ استعادة الاتصال المباشر…"; else -> "غير متصل" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (running) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    }
}

@Composable private fun SetupStepCard(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    statusText: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isComplete) KunPalette.Success.copy(alpha = 0.35f) else KunPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.size(34.dp).background(if (isComplete) SuccessGreen else MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (isComplete) "✓" else stepNumber.toString(), color = if (isComplete) Color.White else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                FeaturePill(
                    statusText,
                    color = if (isComplete) KunPalette.Success else KunPalette.Primary,
                    container = if (isComplete) KunPalette.SuccessSoft else MaterialTheme.colorScheme.primaryContainer,
                )
            }
            AnimatedVisibility(visible = !isComplete, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Column { content() }
            }
        }
    }
}

@Composable private fun ModeOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Card(
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else KunPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold)
            }
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun startLocalProxyService(context: Context, protocol: String, host: String, port: String, username: String, password: String) {
    val intent = Intent(context, ProxyLocalService::class.java).apply {
        action = ProxyLocalService.ACTION_START
        putExtra(ProxyLocalService.EXTRA_PROTOCOL, protocol)
        putExtra(ProxyLocalService.EXTRA_HOST, host.trim())
        putExtra(ProxyLocalService.EXTRA_PORT, port.toInt())
        putExtra(ProxyLocalService.EXTRA_USERNAME, username)
        putExtra(ProxyLocalService.EXTRA_PASSWORD, password)
        putExtra(ProxyLocalService.EXTRA_LOCAL_PORT, ProxyLocalService.DEFAULT_LOCAL_PORT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
}
