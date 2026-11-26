package com.drliki.MusicLi.data.update

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test

class AppReleaseSourceTest {

    @Test
    fun `newest stable GitHub release is picked by semantic version`() {
        val releases = JSONArray(
            listOf(
                release("v1.0.0", prerelease = false),
                release("v1.2.0", prerelease = false),
                release("v1.1.0", prerelease = false),
                release("v2.0.0-alpha.1", prerelease = true),
            ).joinToString(prefix = "[", postfix = "]")
        )

        val latest = AppReleaseSource.parseLatestStable(releases, listOf("arm64-v8a"))!!

        assertThat(latest.channel).isEqualTo(UpdateChannel.STABLE)
        assertThat(latest.versionName).isEqualTo("1.2.0")
        assertThat(latest.apkUrl).endsWith("/MusicLi-1.2.0-arm64-v8a.apk")
    }

    @Test
    fun `newest alpha is picked by version even when the API lists it later`() {
        val releases = JSONArray(
            listOf(
                release("v1.0.0-alpha.9"),
                release("v1.0.0-alpha.16"),
                release("v1.0.0-alpha.15"),
            ).joinToString(prefix = "[", postfix = "]")
        )

        val latest = AppReleaseSource.parseLatestAlpha(releases, listOf("arm64-v8a"))!!

        assertThat(latest.versionName).isEqualTo("1.0.0-alpha.16")
        assertThat(latest.apkUrl).endsWith("/MusicLi-1.0.0-alpha.16-arm64-v8a.apk")
    }

    @Test
    fun `alpha asset follows the device ABI preference order`() {
        val releases = JSONArray("[${release("v1.0.0-alpha.16")}]")

        val latest = AppReleaseSource.parseLatestAlpha(releases, listOf("armeabi-v7a", "arm64-v8a"))!!

        assertThat(latest.apkUrl).endsWith("-armeabi-v7a.apk")
    }

    @Test
    fun `drafts, stable tags and releases without a matching APK are not offered`() {
        val releases = JSONArray(
            listOf(
                release("v1.0.0-alpha.20", draft = true),
                release("v1.1.0", prerelease = false),
                release("v1.0.0-alpha.19", abis = listOf("x86_64")),
                release("v1.0.0-alpha.18"),
            ).joinToString(prefix = "[", postfix = "]")
        )

        assertThat(AppReleaseSource.parseLatestAlpha(releases, listOf("arm64-v8a"))!!.versionName)
            .isEqualTo("1.0.0-alpha.18")
        assertThat(AppReleaseSource.parseLatestAlpha(releases, listOf("x86"))).isNull()
    }

    @Test
    fun `alpha and stable versions compare semantically`() {
        val olderBaseHigherBuild = alphaRelease("1.0.0-alpha.99")
        val newerBase = alphaRelease("1.1.0-alpha.1")

        assertThat(newerBase.isNewerThan("1.0.0-alpha.99", installedVersionCode = 100099)).isTrue()
        assertThat(olderBaseHigherBuild.isNewerThan("1.1.0-alpha.1", installedVersionCode = 200001)).isFalse()
        assertThat(alphaRelease("1.0.0-alpha.16").isNewerThan("1.0.0-alpha.16", 100016)).isFalse()
        assertThat(stableRelease("1.1.0").isNewerThan("1.0.0", installedVersionCode = 1)).isTrue()
        assertThat(stableRelease("1.0.0").isNewerThan("1.0.0", installedVersionCode = 1)).isFalse()
        assertThat(stableRelease("1.0.0").isNewerThan("1.0.0-alpha.5", installedVersionCode = 100005)).isTrue()
    }

    @Test
    fun `PR builds on the alpha key are offered any published alpha`() {
        assertThat(alphaRelease("1.0.0-alpha.16").isNewerThan("1.0.0-pr.137.ba94188", 1)).isTrue()
    }

    @Test
    fun `installed channel follows the version name published by each pipeline`() {
        assertThat(UpdateChannel.ofVersionName("1.0.0")).isEqualTo(UpdateChannel.STABLE)
        assertThat(UpdateChannel.ofVersionName("1.0.0-alpha.16")).isEqualTo(UpdateChannel.ALPHA)
        assertThat(UpdateChannel.ofVersionName("1.0.0-pr.137.ba94188")).isEqualTo(UpdateChannel.ALPHA)
    }

    private fun stableRelease(versionName: String) = AppRelease(
        channel = UpdateChannel.STABLE,
        versionName = versionName,
        versionCode = null,
        apkUrl = "https://example.invalid/app.apk",
        apkSizeBytes = null,
        pageUrl = "https://example.invalid",
    )

    private fun alphaRelease(versionName: String) = AppRelease(
        channel = UpdateChannel.ALPHA,
        versionName = versionName,
        versionCode = null,
        apkUrl = "https://example.invalid/app.apk",
        apkSizeBytes = null,
        pageUrl = "https://example.invalid",
    )

    private fun release(
        tag: String,
        prerelease: Boolean = true,
        draft: Boolean = false,
        abis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
    ): String {
        val name = tag.removePrefix("v")
        val assets = abis.joinToString(prefix = "[", postfix = "]") { abi ->
            """{"name":"MusicLi-$name-$abi.apk","size":37082259,
               "browser_download_url":"https://github.com/dr-liki/MusicLi/releases/download/$tag/MusicLi-$name-$abi.apk"}"""
        }
        return """{"tag_name":"$tag","prerelease":$prerelease,"draft":$draft,
                   "html_url":"https://github.com/dr-liki/MusicLi/releases/tag/$tag","assets":$assets}"""
    }
}
