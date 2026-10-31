package com.earnd.app.ui.onboarding

/** Extra steps some phone makers need so Earnd keeps running. Keyed by Build.MANUFACTURER. */
object OemGuidance {
    data class Guide(val brand: String, val steps: List<String>)

    fun forManufacturer(manufacturer: String?): Guide? {
        val m = manufacturer.orEmpty().lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> Guide("Xiaomi", listOf(
                "Settings > Apps > Manage apps > Earnd > Autostart: on.",
                "Same page > Battery saver: No restrictions.",
                "In Recents, pull down on Earnd and tap the lock so it isn't cleared.",
            ))
            m.contains("samsung") -> Guide("Samsung", listOf(
                "Settings > Apps > Earnd > Battery: Unrestricted.",
                "Settings > Battery > Background usage limits: make sure Earnd is not in Sleeping or Deep sleeping apps.",
            ))
            m.contains("nothing") -> Guide("Nothing", listOf(
                "Settings > Apps > Earnd > Battery: Unrestricted.",
                "In Recents, long-press Earnd and lock it.",
            ))
            m.contains("oneplus") || m.contains("oppo") || m.contains("realme") -> Guide("OnePlus", listOf(
                "Settings > Apps > Earnd > Battery: Unrestricted (or Don't optimise).",
                "Same page: allow Auto launch and background activity.",
                "In Recents, lock Earnd so it isn't cleared.",
            ))
            m.contains("huawei") || m.contains("honor") -> Guide("Huawei", listOf(
                "Settings > Battery > App launch > Earnd: switch to Manage manually and turn on all three options.",
                "In Recents, pull down on Earnd to lock it.",
            ))
            else -> null
        }
    }
}
