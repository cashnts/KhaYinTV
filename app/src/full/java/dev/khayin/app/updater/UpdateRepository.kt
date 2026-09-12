package dev.khayin.app.updater

import dev.khayin.app.BuildConfig
import dev.khayin.app.data.remote.api.GitHubReleaseApi
import dev.khayin.app.updater.model.AppUpdate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubReleaseApi: GitHubReleaseApi
) {

    suspend fun getLatestUpdate(): Result<AppUpdate> {
        return runCatching {
            val owner = BuildConfig.GITHUB_OWNER
            val repo = BuildConfig.GITHUB_REPO

            // 1. Try /releases/latest first
            val latestResponse = runCatching { gitHubReleaseApi.getLatestRelease(owner = owner, repo = repo) }.getOrNull()
            var dto = if (latestResponse?.isSuccessful == true) latestResponse.body() else null

            // 2. If null, draft, or no APK assets found, fallback to /releases list
            if (dto == null || dto.draft || AbiSelector.chooseBestApkAsset(dto.assets) == null) {
                val releasesResponse = runCatching { gitHubReleaseApi.getReleases(owner = owner, repo = repo) }.getOrNull()
                if (releasesResponse?.isSuccessful == true) {
                    val list = releasesResponse.body().orEmpty()
                    dto = list.firstOrNull { release ->
                        !release.draft && AbiSelector.chooseBestApkAsset(release.assets) != null
                    }
                }
            }

            val finalDto = dto ?: error("No valid release with APK assets found on GitHub ($owner/$repo)")

            val tag = finalDto.tagName?.takeIf { it.isNotBlank() }
                ?: finalDto.name?.takeIf { it.isNotBlank() }
                ?: error("Release has no tag/name")

            val asset = AbiSelector.chooseBestApkAsset(finalDto.assets)
                ?: error("No APK asset found in release")

            AppUpdate(
                tag = tag,
                title = finalDto.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = finalDto.body.orEmpty(),
                releaseUrl = finalDto.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size
            )
        }
    }
}
