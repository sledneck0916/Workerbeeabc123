package com.example.hfsandbox

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class Store(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx, "secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    var backend: Backend
        get() = runCatching { Backend.valueOf(prefs.getString("backend", "HF")!!) }.getOrDefault(Backend.HF)
        set(v) = prefs.edit().putString("backend", v.name).apply()
    fun key(b: Backend): String = prefs.getString("key_${b.name}", "") ?: ""
    fun setKey(b: Backend, k: String) = prefs.edit().putString("key_${b.name}", k).apply()
}
