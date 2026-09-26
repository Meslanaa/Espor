package org.mesos.updater

import org.json.JSONException
import org.json.JSONObject

/** An update announcement published by MesOS (schema v1, see docs/UPDATES.md). */
data class UpdateManifest(
    val schemaVersion: Int,
    val packageName: String,
    val channel: String,
    val versionName: String,
    val versionCode: Int,
    val minimumSupportedVersion: Int,
    val releaseNotes: String,
    val packageUrl: String,
    val sha256: String,
    val packageSize: Long,
)

object UpdateManifestParser {

    fun parse(json: String): UpdateManifest {
        val obj = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw UpdateException(FailureReason.INVALID_MANIFEST, e.message)
        }
        return try {
            UpdateManifest(
                schemaVersion = obj.getInt("schemaVersion"),
                packageName = obj.requireString("packageName"),
                channel = obj.requireString("channel"),
                versionName = obj.requireString("versionName"),
                versionCode = obj.getInt("versionCode"),
                minimumSupportedVersion = obj.getInt("minimumSupportedVersion"),
                releaseNotes = obj.optString("releaseNotes", ""),
                packageUrl = obj.requireString("packageUrl"),
                sha256 = obj.requireString("sha256").lowercase(),
                packageSize = obj.getLong("packageSize"),
            )
        } catch (e: JSONException) {
            throw UpdateException(FailureReason.INVALID_MANIFEST, e.message)
        }
    }

    private fun JSONObject.requireString(key: String): String {
        val value = getString(key).trim()
        if (value.isEmpty()) throw JSONException("'$key' is empty")
        return value
    }
}
