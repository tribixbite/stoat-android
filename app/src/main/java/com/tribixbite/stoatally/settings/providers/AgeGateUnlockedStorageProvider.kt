package com.tribixbite.stoatally.settings.providers

import com.tribixbite.stoatally.StoatApplication
import com.tribixbite.stoatally.persistence.KVStorage

object AgeGateUnlockedStorageProvider {
    private val kv = KVStorage(StoatApplication.instance)

    suspend fun setAgeGateUnlocked(unlocked: Boolean) {
        kv.set("ageGateUnlocked", unlocked)
    }

    suspend fun getAgeGateUnlocked(): Boolean {
        return kv.getBoolean("ageGateUnlocked") ?: false
    }
}