package com.ancla.safespace.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.secureDataStore by preferencesDataStore(name = "ancla_secure")

data class UserData(
    val countryCode: String = "",
    val customNumber: String = "",
    val customEmergencyNumber: String = "",
    val safetyPlan: List<String> = List(6) { "" },
)

/** Persiste solo un bloque cifrado con AES-GCM; la clave permanece en Android Keystore. */
class SecureStore(private val context: Context) {
    private val dataKey = stringPreferencesKey("encrypted_user_data")

    suspend fun load(): UserData {
        val encoded = context.secureDataStore.data.first()[dataKey] ?: return UserData()
        val plainText = decrypt(Base64.decode(encoded, Base64.NO_WRAP))
        val json = JSONObject(plainText)
        val sections = json.optJSONArray("plan")
        val plan = List(6) { index -> sections?.optString(index, "") ?: "" }
        return UserData(
            countryCode = json.optString("country", ""),
            customNumber = json.optString("customNumber", ""),
            customEmergencyNumber = json.optString("customEmergencyNumber", ""),
            safetyPlan = plan,
        )
    }

    suspend fun save(data: UserData) {
        val plan = org.json.JSONArray().apply { data.safetyPlan.forEach { put(it) } }
        val json = JSONObject()
            .put("country", data.countryCode)
            .put("customNumber", data.customNumber)
            .put("customEmergencyNumber", data.customEmergencyNumber)
            .put("plan", plan)
            .toString()
        val encrypted = encrypt(json)
        context.secureDataStore.edit { preferences ->
            preferences[dataKey] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        }
    }

    private fun encrypt(value: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return byteArrayOf(iv.size.toByte()) + iv + ciphertext
    }

    private fun decrypt(value: ByteArray): String {
        require(value.isNotEmpty()) { "El contenido cifrado está vacío." }
        val ivSize = value[0].toInt() and 0xff
        require(ivSize in 12..16 && value.size > ivSize + 1) { "El contenido cifrado no es válido." }
        val iv = value.copyOfRange(1, ivSize + 1)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(value.copyOfRange(ivSize + 1, value.size)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ancla_local_data_v1"
    }
}
