package io.github.hdlee73.fsshelper.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 사용자가 직접 넣은 API 인증키를 이 기기의 Android 키 저장소(Keystore) 키로 암호화한다.
 * 키는 기기 밖으로 꺼낼 수 없어서 앱 데이터를 복사해 가도 풀 수 없다.
 */
object Secrets {
    private const val ALIAS = "fss_helper_secrets"
    private const val PREFIX = "aks:"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun seal(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
            PREFIX + Base64.encodeToString(c.iv + enc, Base64.NO_WRAP)
        }.getOrDefault("")
    }

    fun open(sealed: String): String {
        if (sealed.isBlank() || !sealed.startsWith(PREFIX)) return ""
        return runCatching {
            val raw = Base64.decode(sealed.removePrefix(PREFIX), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12)) }
            String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
        }.getOrDefault("")
    }
}
