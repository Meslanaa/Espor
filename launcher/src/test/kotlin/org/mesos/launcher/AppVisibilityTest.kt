package org.mesos.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVisibilityTest {

    private val own = "org.mesos.shell"

    private fun visible(pkg: String, system: Boolean, showAndroid: Boolean = false) =
        AppVisibility.isVisible(pkg, system, own, showAndroid)

    @Test
    fun mesosModeShowsMesOSPlayAndUserApps() {
        assertTrue(visible(own, system = false))
        assertTrue(visible("com.android.vending", system = true))
        assertTrue(visible("com.example.userapp", system = false))
    }

    @Test
    fun mesosModeHidesPreinstalledAndroidApps() {
        assertFalse(visible("com.google.android.apps.photos", system = true))
        assertFalse(visible("com.android.settings", system = true))
        assertFalse(visible("com.android.chrome", system = true))
    }

    @Test
    fun showAndroidAppsShowsEverything() {
        assertTrue(visible("com.google.android.apps.photos", system = true, showAndroid = true))
        assertTrue(visible("com.android.settings", system = true, showAndroid = true))
    }
}
