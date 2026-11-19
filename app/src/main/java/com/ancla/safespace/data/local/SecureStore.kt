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
import java.util.UUID
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
    val moodEntries: List<MoodEntry> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val supportContacts: List<SupportContact> = emptyList(),
    val completedGoals: List<String> = emptyList(),
)

data class MoodEntry(
    val day: Long,
    val mood: String,
    val distress: Int,
    val anxiety: Int,
    val note: String,
)

data class JournalEntry(val id: String, val createdAt: Long, val prompt: String, val text: String)
data class SupportContact(val name: String, val phone: String, val isFavorite: Boolean = false, val id: String = "")

/** Persiste solo un bloque cifrado con AES-GCM; la clave permanece en Android Keystore. */
class SecureStore(private val context: Context) {
    private val dataKey = stringPreferencesKey("encrypted_user_data")

    suspend fun load(): UserData {
        val encoded = context.secureDataStore.data.first()[dataKey] ?: return UserData()
        val plainText = decrypt(Base64.decode(encoded, Base64.NO_WRAP))
        val json = JSONObject(plainText)
        val sections = json.optJSONArray("plan")
        val plan = List(6) { index -> sections?.optString(index, "") ?: "" }
        val moodArray = json.optJSONArray("moodEntries")
        val moodEntries = buildList {
            if (moodArray != null) {
                for (index in 0 until moodArray.length()) {
                    val item = moodArray.optJSONObject(index) ?: continue
                    val mood = item.optString("mood", "")
                    if (mood !in MOOD_CODES) continue
                    add(
                        MoodEntry(
                            day = item.optLong("day", 0L),
                            mood = mood,
                            distress = item.optInt("distress", 5).coerceIn(1, 10),
                            anxiety = item.optInt("anxiety", 5).coerceIn(1, 10),
                            note = item.optString("note", ""),
                        ),
                    )
                }
            }
        }.distinctBy { it.day }.sortedBy { it.day }
        val journalEntries = buildList {
            val array = json.optJSONArray("journalEntries") ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id", "")
                val text = item.optString("text", "")
                if (id.isNotBlank() && text.isNotBlank()) add(JournalEntry(id, item.optLong("createdAt"), item.optString("prompt", ""), text))
            }
        }
        val supportContacts = buildList {
            val array = json.optJSONArray("supportContacts") ?: return@buildList
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val name = item.optString("name", "").trim()
                val phone = item.optString("phone", "").trim()
                if (name.isNotBlank() && phone.isNotBlank()) {
                    val id = item.optString("id", "").ifBlank {
                        UUID.nameUUIDFromBytes("$name\u0000$phone\u0000$index".toByteArray(Charsets.UTF_8)).toString()
                    }
                    add(SupportContact(name, phone, item.optBoolean("favorite", false), id))
                }
            }
        }
        return UserData(
            countryCode = json.optString("country", ""),
            customNumber = json.optString("customNumber", ""),
            customEmergencyNumber = json.optString("customEmergencyNumber", ""),
            safetyPlan = plan,
            moodEntries = moodEntries,
            journalEntries = journalEntries,
            supportContacts = supportContacts,
            completedGoals = json.optJSONArray("completedGoals")?.let { array -> List(array.length()) { array.optString(it) } }.orEmpty(),
        )
    }

    suspend fun save(data: UserData) {
        val plan = org.json.JSONArray().apply { data.safetyPlan.forEach { put(it) } }
        val moodEntries = org.json.JSONArray().apply {
            data.moodEntries.distinctBy { it.day }.sortedBy { it.day }.forEach { entry ->
                put(
                    JSONObject()
                        .put("day", entry.day)
                        .put("mood", entry.mood)
                        .put("distress", entry.distress.coerceIn(1, 10))
                        .put("anxiety", entry.anxiety.coerceIn(1, 10))
                        .put("note", entry.note),
                )
            }
        }
        val journalEntries = org.json.JSONArray().apply {
            data.journalEntries.forEach { put(JSONObject().put("id", it.id).put("createdAt", it.createdAt).put("prompt", it.prompt).put("text", it.text)) }
        }
        val supportContacts = org.json.JSONArray().apply {
            data.supportContacts.forEach {
                put(JSONObject().put("id", it.id).put("name", it.name).put("phone", it.phone).put("favorite", it.isFavorite))
            }
        }
        val completedGoals = org.json.JSONArray().apply { data.completedGoals.forEach { put(it) } }
        val json = JSONObject()
            .put("country", data.countryCode)
            .put("customNumber", data.customNumber)
            .put("customEmergencyNumber", data.customEmergencyNumber)
            .put("plan", plan)
            .put("moodEntries", moodEntries)
            .put("journalEntries", journalEntries)
            .put("supportContacts", supportContacts)
            .put("completedGoals", completedGoals)
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
        val MOOD_CODES = setOf("well", "okay", "low", "very_low")
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "ancla_local_data_v1"
    }
}
