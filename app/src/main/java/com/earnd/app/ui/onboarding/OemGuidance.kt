package com.earnd.app.ui.onboarding

import com.earnd.app.R
import com.earnd.app.i18n.Str

/** Extra steps some phone makers need so Earnd keeps running. Keyed by Build.MANUFACTURER. */
object OemGuidance {
    data class Guide(val brand: String, val steps: List<String>)

    fun forManufacturer(manufacturer: String?): Guide? {
        val m = manufacturer.orEmpty().lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> Guide("Xiaomi", listOf(
                Str.get(R.string.onboarding_oem_xiaomi_1),
                Str.get(R.string.onboarding_oem_xiaomi_2),
                Str.get(R.string.onboarding_oem_xiaomi_3),
            ))
            m.contains("samsung") -> Guide("Samsung", listOf(
                Str.get(R.string.onboarding_oem_battery_unrestricted),
                Str.get(R.string.onboarding_oem_samsung_2),
            ))
            m.contains("nothing") -> Guide("Nothing", listOf(
                Str.get(R.string.onboarding_oem_battery_unrestricted),
                Str.get(R.string.onboarding_oem_nothing_2),
            ))
            m.contains("oneplus") || m.contains("oppo") || m.contains("realme") -> Guide("OnePlus", listOf(
                Str.get(R.string.onboarding_oem_oneplus_1),
                Str.get(R.string.onboarding_oem_oneplus_2),
                Str.get(R.string.onboarding_oem_oneplus_3),
            ))
            m.contains("huawei") || m.contains("honor") -> Guide("Huawei", listOf(
                Str.get(R.string.onboarding_oem_huawei_1),
                Str.get(R.string.onboarding_oem_huawei_2),
            ))
            else -> null
        }
    }
}
