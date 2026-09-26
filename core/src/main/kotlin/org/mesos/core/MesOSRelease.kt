package org.mesos.core

/**
 * Identity of a MesOS release.
 *
 * In the prototype the values are compiled in from `mesos.properties` via BuildConfig.
 * Once MesOS ships as an AOSP-based ROM, the same identity will come from read-only
 * system properties (`ro.mesos.*`) set by the MesOS product configuration; callers
 * only depend on this type, so that switch stays local to [current].
 */
data class MesOSRelease(
    val versionName: String,
    val versionCode: Int,
    val label: String,
    val channel: ReleaseChannel,
    val buildNumber: String,
) {
    /** Version as shown to users, e.g. "0.1 Developer Preview". */
    val displayVersion: String
        get() = if (label.isBlank()) versionName else "$versionName $label"

    /** Full release name, e.g. "MesOS 0.1 Developer Preview". */
    val displayName: String
        get() = "$OS_NAME $displayVersion"

    companion object {
        const val OS_NAME = "MesOS"

        /** The release this build was compiled as. */
        val current: MesOSRelease = MesOSRelease(
            versionName = BuildConfig.MESOS_VERSION_NAME,
            versionCode = BuildConfig.MESOS_VERSION_CODE,
            label = BuildConfig.MESOS_VERSION_LABEL,
            channel = ReleaseChannel.fromId(BuildConfig.MESOS_BUILD_CHANNEL),
            buildNumber = BuildConfig.MESOS_BUILD_NUMBER,
        )
    }
}
