package dev.khayin.app.data.remote.api

import dev.khayin.app.data.remote.dto.GitHubContributorDto
import dev.khayin.app.data.remote.dto.GitHubReleaseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubReleaseApi {

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<GitHubReleaseDto>

    @GET("repos/{owner}/{repo}/contributors")
    suspend fun getContributors(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Response<List<GitHubContributorDto>>
}
