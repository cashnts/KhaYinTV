package dev.khayin.app.data.mapper

import dev.khayin.app.data.remote.dto.MetaPreviewDto
import dev.khayin.app.domain.model.ContentType
import dev.khayin.app.domain.model.MetaPreview
import dev.khayin.app.domain.model.PosterShape

fun MetaPreviewDto.toDomainOrNull(catalogType: String, sourceAddonBaseUrl: String? = null): MetaPreview? {
    val resolvedId = id?.takeIf { it.isNotBlank() } ?: return null
    val resolvedName = name?.takeIf { it.isNotBlank() } ?: return null
    val resolvedType = type?.takeIf { it.isNotBlank() } ?: catalogType
    return MetaPreview(
        id = resolvedId,
        type = ContentType.fromString(resolvedType),
        rawType = resolvedType,
        name = resolvedName,
        poster = poster,
        posterShape = PosterShape.fromString(posterShape),
        background = background,
        logo = logo,
        description = description,
        releaseInfo = releaseInfo,
        imdbRating = imdbRating?.toFloatOrNull(),
        genres = genres ?: emptyList(),
        runtime = runtime,
        status = status?.trim()?.takeIf { it.isNotBlank() },
        released = released,
        country = country,
        imdbId = imdbId,
        slug = slug,
        landscapePoster = landscapePoster,
        rawPosterUrl = rawPosterUrl,
        director = coerceStringList(director),
        writer = coerceStringList(writer).ifEmpty { coerceStringList(writers) },
        links = links?.mapNotNull { it.toDomain() } ?: emptyList(),
        behaviorHints = mapBehaviorHints(behaviorHints),
        trailers = mapTrailers(trailers, trailerStreams),
        trailerYtIds = collectTrailerYtIds(trailers, trailerStreams),
        sourceAddonBaseUrl = sourceAddonBaseUrl
    )
}
