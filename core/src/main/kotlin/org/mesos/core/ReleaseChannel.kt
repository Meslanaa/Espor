package org.mesos.core

/** Release channels a MesOS build can belong to. Only [DEVELOPER] is used in 0.1. */
enum class ReleaseChannel(val id: String, val displayName: String) {
    DEVELOPER("developer", "Developer"),
    BETA("beta", "Beta"),
    STABLE("stable", "Stable");

    companion object {
        fun fromId(id: String): ReleaseChannel =
            entries.firstOrNull { it.id == id }
                ?: throw IllegalArgumentException("Unknown MesOS release channel: '$id'")
    }
}
