package com.proxyplatform.app

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import io.github.muntashirakon.adb.android.AdbMdns
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.math.BigInteger
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Embedded TLS ADB client. It does not require adb, Shizuku, or a companion app. */
class EmbeddedAdbManager private constructor(private val context: Context) {
    private val connection = ClientConnection(context)

    fun pair(timeoutMs: Long, pairingCode: String): Result<Unit> = runCatching {
        require(pairingCode.length == 6 && pairingCode.all { it in '0'..'9' }) {
            "رمز الاقتران يجب أن يتكون من 6 أرقام"
        }
        AdvancedOperationLog.info(context, "اكتشاف خدمة ADB Pairing عبر mDNS ($timeoutMs ms كحد أقصى).")
        val endpoint = discover(AdbMdns.SERVICE_TYPE_TLS_PAIRING, timeoutMs)
            ?: error("لم يتم العثور على خدمة الاقتران. افتح شاشة إقران الجهاز باستخدام رمز")
        AdvancedOperationLog.info(context, "عُثر على خدمة الاقتران على المنفذ ${endpoint.second}؛ عنوان الشبكة مخفي.")
        AdvancedOperationLog.info(context, "إرسال طلب الاقتران إلى Android؛ الرمز لا يُسجّل.")
        connection.pair(endpoint.first.hostAddress ?: error("عنوان الجهاز غير متاح"), endpoint.second, pairingCode)
        context.getSharedPreferences(PAIRING_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PAIRING_RECORDED, true).apply()
        AdvancedOperationLog.output(context, "اكتمل تبادل مفاتيح Wireless ADB وحُفظت حالة الاقتران.")
    }

    fun connect(timeoutMs: Long): Result<Unit> = runCatching {
        AdvancedOperationLog.info(context, "بدء تفاوض اتصال ADB عبر TLS (مهلة $timeoutMs ms).")
        check(connection.connectTls(context, timeoutMs)) { "تعذر الاتصال بخدمة ADB" }
        check(connection.isConnected()) { "اتصال ADB غير جاهز" }
        AdvancedOperationLog.output(context, "تم الاتصال بخدمة ADB بنجاح.")
    }

    fun execute(command: String): Result<String> = runCatching {
        check(connection.isConnected()) { "ADB غير متصل" }
        val stream: AdbStream = connection.openStream("shell:$command")
        stream.use {
            val reader = Executors.newSingleThreadExecutor { task -> Thread(task, "adb-shell-reader").apply { isDaemon = true } }
            val future = reader.submit<String> {
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var count: Int
                try {
                    while (stream.read(buffer, 0, buffer.size).also { count = it } >= 0) {
                        if (count > 0) output.write(buffer, 0, count)
                    }
                } catch (closed: java.io.IOException) {
                    if (closed.message != "Stream closed.") throw closed
                    Log.w("EmbeddedAdbManager", "ADB remote shell stream closed after delivering ${output.size()} bytes.")
                }
                output.toString(Charsets.UTF_8.name())
            }
            try {
                future.get(SHELL_COMMAND_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (timeout: TimeoutException) {
                future.cancel(true)
                runCatching { stream.close() }
                throw IllegalStateException("انتهت مهلة أمر ADB بعد ${SHELL_COMMAND_TIMEOUT_MS / 1000} ثانية: $command", timeout)
            } finally {
                reader.shutdownNow()
            }
        }
    }

    fun isConnected(): Boolean = connection.isConnected()

    fun wasPaired(): Boolean = context.getSharedPreferences(PAIRING_PREFS, Context.MODE_PRIVATE)
        .getBoolean(PAIRING_RECORDED, false)

    fun close() = runCatching { connection.close() }

    private fun discover(serviceType: String, timeoutMs: Long): Pair<InetAddress, Int>? {
        val latch = CountDownLatch(1)
        var result: Pair<InetAddress, Int>? = null
        val mdns = AdbMdns(context, serviceType) { host, port ->
            if (host != null && port > 0) {
                result = host to port
                latch.countDown()
            }
        }
        mdns.start()
        return try {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            result
        } finally {
            mdns.stop()
        }
    }

    private class ClientConnection(context: Context) : AbsAdbConnectionManager() {
        private val material = KeyMaterial.loadOrCreate(context)

        init {
            setApi(Build.VERSION.SDK_INT)
            setTimeout(15, TimeUnit.SECONDS)
            setThrowOnUnauthorised(true)
        }

        override fun getPrivateKey(): PrivateKey = material.privateKey
        override fun getCertificate(): Certificate = material.certificate
        override fun getDeviceName(): String = "Proxy Platform"
    }

    private data class KeyMaterial(
        val privateKey: PrivateKey,
        val certificate: Certificate
    ) {
        companion object {
            private const val PREFS = "embedded_adb_keys"
            private const val PRIVATE_KEY = "private_key"
            private const val CERTIFICATE = "certificate"
            private const val WRAP_ALIAS = "proxy_platform_adb_wrap_key"
            private const val TAG = "EmbeddedAdbManager"

            fun loadOrCreate(context: Context): KeyMaterial {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                runCatching {
                    val privateBytes = prefs.getString(PRIVATE_KEY, null)?.let {
                        decrypt(Base64.decode(it, Base64.DEFAULT))
                    }
                    val certificateBytes = prefs.getString(CERTIFICATE, null)?.let {
                        Base64.decode(it, Base64.DEFAULT)
                    }
                    if (privateBytes != null && certificateBytes != null) {
                        val privateKey = KeyFactory.getInstance("RSA")
                            .generatePrivate(PKCS8EncodedKeySpec(privateBytes))
                        val certificate = CertificateFactory.getInstance("X.509")
                            .generateCertificate(certificateBytes.inputStream())
                        return KeyMaterial(privateKey, certificate)
                    }
                }.onFailure {
                    // A key may become unreadable after a restore, Keystore reset,
                    // or provider change. Do not crash the UI; replace the pair.
                    Log.w(TAG, "Stored ADB identity is invalid; generating a new one", it)
                    prefs.edit().remove(PRIVATE_KEY).remove(CERTIFICATE).apply()
                }

                val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
                keyPairGenerator.initialize(2048)
                val keyPair = keyPairGenerator.generateKeyPair()
                val certificate = createCertificate(keyPair)
                prefs.edit()
                    .putString(PRIVATE_KEY, Base64.encodeToString(encrypt(keyPair.private.encoded), Base64.NO_WRAP))
                    .putString(CERTIFICATE, Base64.encodeToString(certificate.encoded, Base64.NO_WRAP))
                    .apply()
                return KeyMaterial(keyPair.private, certificate)
            }

            private fun createCertificate(keyPair: KeyPair): java.security.cert.X509Certificate {
                val now = Date()
                val expiry = Date(now.time + 3650L * 24 * 60 * 60 * 1000)
                val subject = X500Name("CN=Proxy Platform")
                val builder = JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger.valueOf(System.currentTimeMillis() and Long.MAX_VALUE),
                    now,
                    expiry,
                    subject,
                    keyPair.public
                )
                val signer = JcaContentSignerBuilder("SHA256withRSA")
                    .build(keyPair.private)
                return JcaX509CertificateConverter()
                    .getCertificate(builder.build(signer))
            }

            private fun getWrappingKey(): SecretKey {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (!keyStore.containsAlias(WRAP_ALIAS)) {
                    val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
                    generator.init(
                        android.security.keystore.KeyGenParameterSpec.Builder(
                            WRAP_ALIAS,
                            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                        )
                            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build()
                    )
                    generator.generateKey()
                }
                return (keyStore.getEntry(WRAP_ALIAS, null) as java.security.KeyStore.SecretKeyEntry).secretKey
            }

            private fun encrypt(value: ByteArray): ByteArray {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, getWrappingKey())
                return cipher.iv + cipher.doFinal(value)
            }

            private fun decrypt(value: ByteArray): ByteArray {
                require(value.size > 12) { "مفتاح ADB المخزن غير صالح" }
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getWrappingKey(),
                    GCMParameterSpec(128, value.copyOfRange(0, 12))
                )
                return cipher.doFinal(value.copyOfRange(12, value.size))
            }
        }
    }

    companion object {
        private const val PAIRING_PREFS = "embedded_adb_state"
        private const val PAIRING_RECORDED = "wireless_adb_paired"
        private const val DISCOVERY_TIMEOUT_MS = 15_000L
        @Volatile private var instance: EmbeddedAdbManager? = null

        fun get(context: Context): EmbeddedAdbManager = instance ?: synchronized(this) {
            instance ?: EmbeddedAdbManager(context.applicationContext).also { instance = it }
        }

        const val DEFAULT_TIMEOUT_MS = DISCOVERY_TIMEOUT_MS
        private const val SHELL_COMMAND_TIMEOUT_MS = 15_000L
    }
}

private operator fun ByteArray.plus(other: ByteArray): ByteArray =
    ByteArray(size + other.size).also {
        System.arraycopy(this, 0, it, 0, size)
        System.arraycopy(other, 0, it, size, other.size)
    }
