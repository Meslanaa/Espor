import java.util.Properties

plugins {
    // Declared here (not applied) so every module shares one plugin classloader.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// MesOS release identity, parsed once from mesos.properties (the single source of
// truth) and exposed to modules through `rootProject.extra`.
val mesosProperties = Properties().apply {
    file("mesos.properties").reader(Charsets.UTF_8).use(::load)
}

fun mesosProperty(key: String): String =
    mesosProperties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("mesos.properties is missing a value for '$key'")

val mesosChannel = mesosProperty("mesos.channel")
check(mesosChannel in setOf("developer", "beta", "stable")) {
    "mesos.channel must be one of developer, beta, stable (was '$mesosChannel')"
}

val mesosManifestUrl = mesosProperty("mesos.update.manifestUrl")
check(mesosManifestUrl.startsWith("https://")) {
    "mesos.update.manifestUrl must use https:// (was '$mesosManifestUrl')"
}

extra["mesosVersionName"] = mesosProperty("mesos.version.name")
extra["mesosVersionCode"] = mesosProperty("mesos.version.code").toInt()
extra["mesosVersionLabel"] = mesosProperty("mesos.version.label")
extra["mesosChannel"] = mesosChannel
extra["mesosManifestUrl"] = mesosManifestUrl
extra["mesosBuildNumber"] = providers.environmentVariable("MESOS_BUILD_NUMBER").getOrElse("local")
