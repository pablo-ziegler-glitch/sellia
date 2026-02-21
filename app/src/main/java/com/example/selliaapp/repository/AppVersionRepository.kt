package com.example.selliaapp.repository

import android.content.Context
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.example.selliaapp.BuildConfig
import com.example.selliaapp.auth.TenantProvider
import com.example.selliaapp.di.IoDispatcher
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class InstalledAppVersion(
    val versionName: String,
    val versionCode: Long
)

data class AppVersionHistoryEntry(
    val versionName: String,
    val versionCode: Long,
    val isCurrentInstalled: Boolean,
    val mergedPrs: List<String>,
    val changelog: VersionChangelog
)

data class VersionChangelog(
    val majorChanges: List<String> = emptyList(),
    val minorChanges: List<String> = emptyList(),
    val fixes: List<String> = emptyList()
)

@Singleton
class AppVersionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestore: FirebaseFirestore,
    private val tenantProvider: TenantProvider,
    @IoDispatcher private val io: CoroutineDispatcher
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun installedVersion(): InstalledAppVersion {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        return InstalledAppVersion(
            versionName = packageInfo.versionName.orEmpty().ifBlank { "unknown" },
            versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
        )
    }

    suspend fun getVersionHistory(limit: Long = 20): Result<List<AppVersionHistoryEntry>> = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.currentTenantId().orEmpty().trim()
            val currentVersion = installedVersion()
            if (tenantId.isBlank()) {
                return@runCatching listOf(
                    AppVersionHistoryEntry(
                        versionName = currentVersion.versionName,
                        versionCode = currentVersion.versionCode,
                        isCurrentInstalled = true,
                        mergedPrs = mergedPrs(),
                        changelog = changelogFrom(mergedPrs = mergedPrs())
                    )
                )
            }

            val docs = firestore.collection("tenants")
                .document(tenantId)
                .collection(COLLECTION_INTERNAL_APP_VERSIONS)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
                .documents

            val mapped = docs.map { doc ->
                val versionName = doc.getString("versionName").orEmpty().ifBlank { "unknown" }
                val versionCode = doc.getLong("versionCode") ?: 0L
                val prs = (doc.get("mergedPrs") as? List<*>)
                    .orEmpty()
                    .mapNotNull { it?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                val changelog = changelogFrom(
                    mergedPrs = prs,
                    majorChanges = stringListFromAny(doc.get("majorChanges")),
                    minorChanges = stringListFromAny(doc.get("minorChanges")),
                    fixes = stringListFromAny(doc.get("fixes"))
                )
                AppVersionHistoryEntry(
                    versionName = versionName,
                    versionCode = versionCode,
                    isCurrentInstalled = versionName == currentVersion.versionName && versionCode == currentVersion.versionCode,
                    mergedPrs = prs,
                    changelog = changelog
                )
            }

            if (mapped.any { it.isCurrentInstalled }) mapped else {
                listOf(
                    AppVersionHistoryEntry(
                        versionName = currentVersion.versionName,
                        versionCode = currentVersion.versionCode,
                        isCurrentInstalled = true,
                        mergedPrs = mergedPrs(),
                        changelog = changelogFrom(mergedPrs = mergedPrs())
                    )
                ) + mapped
            }
        }
    }

    suspend fun trackInstalledVersionIfNeeded(): Result<Unit> = withContext(io) {
        runCatching {
            val tenantId = tenantProvider.currentTenantId().orEmpty().trim()
            if (tenantId.isBlank()) return@runCatching

            val currentVersion = installedVersion()
            val currentKey = versionKey(currentVersion)
            val trackedKey = prefs.getString(KEY_LAST_TRACKED_VERSION, null)
            if (trackedKey == currentKey) return@runCatching

            val installationId = resolveInstallationId()
            val mergedPrs = mergedPrs()
            val nowPayload = mapOf(
                "versionName" to currentVersion.versionName,
                "versionCode" to currentVersion.versionCode,
                "mergedPrs" to mergedPrs,
                "majorChanges" to emptyList<String>(),
                "minorChanges" to mergedPrs.map { "Detalle PR: $it" },
                "fixes" to emptyList<String>(),
                "buildType" to BuildConfig.BUILD_TYPE,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to "android_app"
            )

            val versionDoc = firestore.collection("tenants")
                .document(tenantId)
                .collection(COLLECTION_INTERNAL_APP_VERSIONS)
                .document(currentKey)
            versionDoc.set(nowPayload, SetOptions.merge()).await()

            versionDoc.collection("installs")
                .document(installationId)
                .set(
                    mapOf(
                        "versionName" to currentVersion.versionName,
                        "versionCode" to currentVersion.versionCode,
                        "mergedPrs" to mergedPrs,
                        "buildType" to BuildConfig.BUILD_TYPE,
                        "sdkInt" to Build.VERSION.SDK_INT,
                        "manufacturer" to Build.MANUFACTURER,
                        "model" to Build.MODEL,
                        "installedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()

            prefs.edit().putString(KEY_LAST_TRACKED_VERSION, currentKey).apply()
        }
    }

    private fun mergedPrs(): List<String> = BuildConfig.MERGED_PRS
        .split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun changelogFrom(
        mergedPrs: List<String>,
        majorChanges: List<String> = emptyList(),
        minorChanges: List<String> = emptyList(),
        fixes: List<String> = emptyList()
    ): VersionChangelog {
        val normalizedMajor = sanitizeChangelogItems(majorChanges)
        val normalizedMinor = sanitizeChangelogItems(minorChanges)
        val normalizedFixes = sanitizeChangelogItems(fixes)

        if (normalizedMajor.isNotEmpty() || normalizedMinor.isNotEmpty() || normalizedFixes.isNotEmpty()) {
            return VersionChangelog(
                majorChanges = normalizedMajor,
                minorChanges = normalizedMinor,
                fixes = normalizedFixes
            )
        }

        return VersionChangelog(
            minorChanges = sanitizeChangelogItems(
                mergedPrs.map { "Detalle PR: $it" }
            )
        )
    }

    private fun stringListFromAny(value: Any?): List<String> =
        (value as? List<*>)
            .orEmpty()
            .mapNotNull { it?.toString() }

    private fun sanitizeChangelogItems(items: List<String>): List<String> = items
        .map { it.replace(Regex("(?i)\\b(codex|ai|ia)\\b"), "") }
        .map { it.replace(Regex("\\s+"), " ").trim(' ', '-', '•', ':', ';') }
        .filter { it.isNotBlank() }

    private fun resolveInstallationId(): String {
        val existing = prefs.getString(KEY_INSTALLATION_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALLATION_ID, generated).apply()
        return generated
    }

    private fun versionKey(version: InstalledAppVersion): String =
        "${version.versionName}_${version.versionCode}"

    private companion object {
        const val PREFS_NAME = "app_version_tracking"
        const val KEY_INSTALLATION_ID = "installation_id"
        const val KEY_LAST_TRACKED_VERSION = "last_tracked_version"
        const val COLLECTION_INTERNAL_APP_VERSIONS = "internal_app_versions"
    }
}
