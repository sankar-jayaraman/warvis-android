package com.warvis.android.taskratchet

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal sealed interface TaskRatchetCredentialState {
    data object Missing : TaskRatchetCredentialState
    data class Available(val token: String) : TaskRatchetCredentialState
    data object Unusable : TaskRatchetCredentialState
}

internal class TaskRatchetCredentialStore(context: Context) {
    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(token: String): Boolean = synchronized(TaskRatchetProcessLock.monitor) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            }
            val ciphertext = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            preferences.edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        } catch (_: Exception) {
            clearInternal(deleteKey = true)
            false
        }
    }

    fun load(): TaskRatchetCredentialState =
        synchronized(TaskRatchetProcessLock.monitor) {
            try {
                val encodedCiphertext = preferences.getString(KEY_CIPHERTEXT, null)
                    ?: return TaskRatchetCredentialState.Missing
                val encodedIv = preferences.getString(KEY_IV, null)
                    ?: return unusable()
                val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return unusable()
                val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                    init(
                        Cipher.DECRYPT_MODE,
                        key,
                        GCMParameterSpec(
                            GCM_TAG_LENGTH_BITS,
                            Base64.decode(encodedIv, Base64.NO_WRAP),
                        ),
                    )
                }
                val plaintext = cipher.doFinal(
                    Base64.decode(encodedCiphertext, Base64.NO_WRAP),
                ).toString(Charsets.UTF_8)
                if (plaintext.isBlank()) {
                    unusable()
                } else {
                    TaskRatchetCredentialState.Available(plaintext)
                }
            } catch (_: Exception) {
                unusable()
            }
        }

    fun clear(): Boolean = synchronized(TaskRatchetProcessLock.monitor) {
        clearInternal(deleteKey = true)
    }

    private fun unusable(): TaskRatchetCredentialState {
        clearInternal(deleteKey = true)
        return TaskRatchetCredentialState.Unusable
    }

    private fun clearInternal(deleteKey: Boolean): Boolean {
        val preferencesCleared = preferences.edit().clear().commit()
        if (deleteKey) {
            try {
                keyStore().deleteEntry(KEY_ALIAS)
            } catch (_: Exception) {
                return false
            }
        }
        return preferencesCleared
    }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }
            .generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val PREFERENCES_NAME = "task_ratchet_credentials"
        const val KEY_ALIAS = "warvis_task_ratchet_api_key"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
