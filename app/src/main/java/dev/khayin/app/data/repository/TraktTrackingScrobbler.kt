package dev.khayin.app.data.repository

import dev.khayin.app.core.tracking.TrackingMediaKind
import dev.khayin.app.core.tracking.TrackingMediaReference
import dev.khayin.app.core.tracking.TrackingProviderId
import dev.khayin.app.core.tracking.TrackingScrobbleAction
import dev.khayin.app.core.tracking.TrackingScrobbleEvent
import dev.khayin.app.core.tracking.TrackingScrobbler
import dev.khayin.app.core.tracking.TrackingSeekScrobblePolicy
import dev.khayin.app.data.remote.dto.trakt.TraktIdsDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TraktTrackingScrobbler @Inject constructor(
    private val service: TraktScrobbleService,
    private val episodeMappingService: TraktEpisodeMappingService
) : TrackingScrobbler {
    override val providerId = TrackingProviderId.TRAKT
    override val seekScrobblePolicy = TrackingSeekScrobblePolicy.STOP_AND_RESTART

    override suspend fun scrobble(
        action: TrackingScrobbleAction,
        event: TrackingScrobbleEvent
    ) {
        val item = event.media.toTraktScrobbleItem() ?: return
        when (action) {
            TrackingScrobbleAction.START -> service.scrobbleStart(item, event.progressPercent.toFloat())
            TrackingScrobbleAction.PAUSE -> service.scrobbleStop(item, event.progressPercent.toFloat())
            TrackingScrobbleAction.STOP -> service.scrobbleStop(item, event.progressPercent.toFloat())
        }
    }

    private suspend fun TrackingMediaReference.toTraktScrobbleItem(): TraktScrobbleItem? {
        val traktIds = TraktIdsDto(
            trakt = ids.trakt.toIntExactOrNull(),
            imdb = ids.imdb?.takeIf(String::isNotBlank),
            tmdb = ids.tmdb.toIntExactOrNull(),
            tvdb = ids.tvdb?.toIntOrNull()
        )
        if (!traktIds.hasAnyId()) return null
        if (kind == TrackingMediaKind.MOVIE) {
            return TraktScrobbleItem.Movie(title = title, year = year, ids = traktIds)
        }

        val episodeReference = episode ?: return null
        val season = episodeReference.season ?: return null
        val contentId = catalog?.contentId ?: ids.imdb ?: ids.tmdb?.let { "tmdb:$it" }
        val mapped = episodeMappingService.resolveEpisodeMapping(
            contentId = contentId,
            contentType = catalog?.contentType ?: "series",
            videoId = catalog?.videoId,
            season = season,
            episode = episodeReference.number,
            episodeTitle = episodeReference.title
        )
        return TraktScrobbleItem.Episode(
            showTitle = title,
            showYear = year,
            showIds = traktIds,
            season = mapped?.season ?: season,
            number = mapped?.episode ?: episodeReference.number,
            episodeTitle = episodeReference.title
        )
    }
}

private fun Long?.toIntExactOrNull(): Int? = this?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
