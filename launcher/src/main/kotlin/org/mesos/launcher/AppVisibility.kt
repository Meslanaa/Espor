package org.mesos.launcher

import org.mesos.core.MesOSApps

/**
 * Which apps MesOS Home lists.
 *
 * MesOS mode (default): MesOS apps, Google Play and apps the user installed.
 * Preinstalled Android/Google apps are hidden unless "Show Android apps" is on.
 */
internal object AppVisibility {

    fun isVisible(
        packageName: String,
        isSystemApp: Boolean,
        ownPackage: String,
        showAndroidApps: Boolean,
    ): Boolean =
        showAndroidApps ||
            packageName == ownPackage ||
            packageName == MesOSApps.PLAY_STORE_PACKAGE ||
            !isSystemApp
}
