package com.novastore.app.data.repository

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.database.entity.RepositoryEntity
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable source list (P06-T05). The file is versioned so a future schema
 * can be migrated: currently version 1:
 *
 * { "version": 1, "sources": [ { "name", "url", "type", "enabled", "priority", "extra" } ] }
 */
data class SourceBackup(val sources: List<SourceBackupEntry>)

data class SourceBackupEntry(
    val name: String,
    val url: String,
    val providerType: ProviderType,
    val enabled: Boolean,
    val priority: Int,
    val extraJson: String?,
)

fun RepositoryEntity.toBackupEntry(): SourceBackupEntry = SourceBackupEntry(
    name = name,
    url = baseUrl,
    providerType = ProviderType.values().firstOrNull { it.name == providerType } ?: ProviderType.FDROID_INDEX,
    enabled = enabled,
    priority = priority,
    extraJson = extraJson,
)

fun SourceBackup.toJson(): String {
    val sources = JSONArray()
    this.sources.forEach { entry ->
        sources.put(
            JSONObject()
                .put("name", entry.name)
                .put("url", entry.url)
                .put("type", entry.providerType.name)
                .put("enabled", entry.enabled)
                .put("priority", entry.priority)
                .put("extra", entry.extraJson?.let { JSONObject(it) } ?: JSONObject.NULL),
        )
    }
    return JSONObject()
        .put("version", SOURCE_BACKUP_VERSION)
        .put("sources", sources)
        .toString()
}

/**
 * Parses a backup file. Structural problems (bad JSON, wrong version, unknown
 * type, missing name/url) fail the whole import; per-entry URL validation is
 * left to the caller, which rejects non-https references.
 */
fun parseSourceBackup(json: String): AppResult<SourceBackup> {
    return runCatching {
        val root = JSONObject(json)
        val version = root.optInt("version")
        if (version != SOURCE_BACKUP_VERSION) {
            return AppResult.failure(
                NovaError.Repository(userMessage = "Unsupported source backup version: $version"),
            )
        }
        val raw = root.optJSONArray("sources") ?: JSONArray()
        val entries = buildList {
            for (i in 0 until raw.length()) {
                val item = raw.optJSONObject(i) ?: continue
                val name = item.optString("name").trim()
                val url = item.optString("url").trim()
                if (name.isEmpty() || url.isEmpty()) continue
                val type = item.optString("type")
                val providerType = ProviderType.values().firstOrNull { it.name == type } ?: continue
                val extraRaw = item.opt("extra")
                add(
                    SourceBackupEntry(
                        name = name,
                        url = url,
                        providerType = providerType,
                        enabled = item.optBoolean("enabled", true),
                        priority = item.optInt("priority", 10),
                        extraJson = if (extraRaw == JSONObject.NULL || extraRaw == null) null else extraRaw.toString(),
                    ),
                )
            }
        }
        AppResult.success(SourceBackup(entries))
    }.getOrElse { throwable ->
        AppResult.failure(NovaError.Repository(userMessage = "Invalid source backup file."))
    }
}

private const val SOURCE_BACKUP_VERSION = 1