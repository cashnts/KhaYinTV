package dev.khayin.app.data.remote.api

import dev.khayin.app.data.remote.dto.AddonManifestDto
import dev.khayin.app.data.remote.dto.CatalogResponseDto
import dev.khayin.app.data.remote.dto.MetaResponseDto
import dev.khayin.app.data.remote.dto.StreamResponseDto
import dev.khayin.app.data.remote.dto.SubtitleResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface AddonApi {

    @GET
    suspend fun getManifest(@Url manifestUrl: String): Response<AddonManifestDto>

    @GET
    suspend fun getCatalog(@Url catalogUrl: String): Response<CatalogResponseDto>

    @GET
    suspend fun getMeta(@Url metaUrl: String): Response<MetaResponseDto>

    @GET
    suspend fun getStreams(@Url streamUrl: String): Response<StreamResponseDto>

    @GET
    suspend fun getSubtitles(@Url subtitleUrl: String): Response<SubtitleResponseDto>
}
