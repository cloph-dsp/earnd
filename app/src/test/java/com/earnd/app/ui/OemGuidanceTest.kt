package com.earnd.app.ui

import com.earnd.app.i18n.TestStr
import com.earnd.app.ui.onboarding.OemGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class OemGuidanceTest {
    @Before fun english() = TestStr.install()

    @Test fun matchesKnownBrandsCaseInsensitively() {
        assertEquals("Xiaomi", OemGuidance.forManufacturer("Xiaomi")?.brand)
        assertEquals("Xiaomi", OemGuidance.forManufacturer("POCO")?.brand)
        assertEquals("Samsung", OemGuidance.forManufacturer("samsung")?.brand)
        assertEquals("Nothing", OemGuidance.forManufacturer("Nothing")?.brand)
        assertEquals("OnePlus", OemGuidance.forManufacturer("OnePlus")?.brand)
        assertEquals("Huawei", OemGuidance.forManufacturer("HUAWEI")?.brand)
    }

    @Test fun unknownOrMissingHasNoGuide() {
        assertNull(OemGuidance.forManufacturer("Google"))
        assertNull(OemGuidance.forManufacturer(null))
    }
}
