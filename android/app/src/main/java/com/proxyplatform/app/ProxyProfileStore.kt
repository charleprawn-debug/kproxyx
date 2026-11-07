package com.proxyplatform.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A saved, device-local upstream proxy. Credentials are encrypted before reaching SQLite. */
data class SavedProxy(
    val id: String,
    val name: String,
    val protocol: String,
    val host: String,
    val port: Int,
    val authenticationRequired: Boolean,
    val username: String,
    val password: String,
    val selected: Boolean = false,
) {
    val endpoint: String get() = "$host:$port"
}

/**
 * Durable per-device proxy profiles. Nothing is uploaded or synchronized; the
 * SQLite database is private to this app, while usernames and passwords are
 * encrypted using a non-exportable Android Keystore AES-256-GCM key.
 */
class ProxyProfileStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    @Synchronized
    fun loadAll(): List<SavedProxy> {
        val result = mutableListOf<SavedProxy>()
        readableDatabase.query(
            TABLE,
            COLUMNS,
            null,
            null,
            null,
            null,
            "is_selected DESC, updated_at DESC, name COLLATE NOCASE ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += SavedProxy(
                    id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                    name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                    protocol = cursor.getString(cursor.getColumnIndexOrThrow("protocol")),
                    host = cursor.getString(cursor.getColumnIndexOrThrow("host")),
                    port = cursor.getInt(cursor.getColumnIndexOrThrow("port")),
                    authenticationRequired = cursor.getInt(cursor.getColumnIndexOrThrow("auth_required")) != 0,
                    username = ProxyCredentialCipher.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("username_cipher"))),
                    password = ProxyCredentialCipher.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("password_cipher"))),
                    selected = cursor.getInt(cursor.getColumnIndexOrThrow("is_selected")) != 0,
                )
            }
        }
        return result
    }

    @Synchronized
    fun save(profile: SavedProxy): SavedProxy {
        require(profile.name.isNotBlank()) { "أدخل اسمًا لهذا البروكسي." }
        require(profile.host.isNotBlank()) { "أدخل عنوان البروكسي." }
        require(profile.port in 1..65535) { "المنفذ يجب أن يكون بين 1 و65535." }
        require(profile.protocol in SUPPORTED_PROTOCOLS) { "بروتوكول البروكسي غير مدعوم." }
        if (profile.authenticationRequired) {
            require(profile.username.isNotBlank()) { "أدخل اسم المستخدم المطلوب للمصادقة." }
            require(profile.password.isNotEmpty()) { "أدخل كلمة المرور المطلوبة للمصادقة." }
        }

        val database = writableDatabase
        val existing = database.query(TABLE, arrayOf("id"), "id = ?", arrayOf(profile.id), null, null, null).use { it.moveToFirst() }
        val id = if (existing) profile.id else UUID.randomUUID().toString()
        val encryptedUsername = ProxyCredentialCipher.encrypt(if (profile.authenticationRequired) profile.username else "")
        val encryptedPassword = ProxyCredentialCipher.encrypt(if (profile.authenticationRequired) profile.password else "")
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("id", id)
            put("name", profile.name.trim())
            put("protocol", profile.protocol)
            put("host", profile.host.trim())
            put("port", profile.port)
            put("auth_required", if (profile.authenticationRequired) 1 else 0)
            put("username_cipher", encryptedUsername)
            put("password_cipher", encryptedPassword)
            put("updated_at", now)
            put("is_selected", 1)
            if (!existing) put("created_at", now)
        }
        database.beginTransaction()
        try {
            database.execSQL("UPDATE $TABLE SET is_selected = 0")
            if (existing) {
                database.update(TABLE, values, "id = ?", arrayOf(id))
            } else {
                database.insert(TABLE, null, values)
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        return profile.copy(id = id, name = profile.name.trim(), host = profile.host.trim(), selected = true)
    }

    @Synchronized
    fun select(id: String) {
        val database = writableDatabase
        database.beginTransaction()
        try {
            database.execSQL("UPDATE $TABLE SET is_selected = 0")
            val updated = ContentValues().apply { put("is_selected", 1) }
            val count = database.update(TABLE, updated, "id = ?", arrayOf(id))
            require(count == 1) { "تعذر العثور على البروكسي المحفوظ." }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun delete(id: String) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))
        val anySelected = readableDatabase.query(TABLE, arrayOf("id"), "is_selected = 1", null, null, null, null).use { it.moveToFirst() }
        if (!anySelected) {
            val next = readableDatabase.query(TABLE, arrayOf("id"), null, null, null, null, "updated_at DESC", "1").use {
                if (it.moveToFirst()) it.getString(0) else null
            }
            if (next != null) {
                val selected = ContentValues().apply { put("is_selected", 1) }
                writableDatabase.update(TABLE, selected, "id = ?", arrayOf(next))
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                protocol TEXT NOT NULL,
                host TEXT NOT NULL,
                port INTEGER NOT NULL,
                auth_required INTEGER NOT NULL DEFAULT 0,
                username_cipher TEXT NOT NULL,
                password_cipher TEXT NOT NULL,
                is_selected INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_saved_proxy_selected ON $TABLE (is_selected, updated_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Additive schema upgrades are applied here as the profile format evolves.
        // Never drop saved connection data during an application upgrade.
    }

    private companion object {
        const val DATABASE_NAME = "saved_proxy_profiles.db"
        const val DATABASE_VERSION = 1
        const val TABLE = "proxy_profiles"
        val COLUMNS = arrayOf(
            "id", "name", "protocol", "host", "port", "auth_required",
            "username_cipher", "password_cipher", "is_selected", "created_at", "updated_at",
        )
        val SUPPORTED_PROTOCOLS = setOf("http", "socks5")
    }
}

/** AES-GCM field encryption backed by a device-bound Android Keystore key. */
private object ProxyCredentialCipher {
    private const val KEY_ALIAS = "proxy-profile-credentials-aes-v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    @Synchronized
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val payload = cipher.iv + encrypted
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    @Synchronized
    fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > IV_LENGTH_BYTES) { "بيانات اعتماد البروكسي المحفوظة غير صالحة." }
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val encrypted = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }
}
