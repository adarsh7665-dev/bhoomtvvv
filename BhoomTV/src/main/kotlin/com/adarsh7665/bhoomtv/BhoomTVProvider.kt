package com.adarsh7665.bhoomtv

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder

@OptIn(Prerelease::class, kotlin.uuid.ExperimentalUuidApi::class)
class BhoomTVProvider : MainAPI() {
    override var mainUrl = "https://bhoomtv.me"
    override var name = "BHOOM TV"
    override var lang = "ml"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    private val malayalamPage = "$mainUrl/channel/malayalam/"
    private val bhoomNetUrl = "https://bhoomtv.net"

    private data class IndexedSource(
        val pageId: String,
        val pageUrl: String,
        val index: Int,
        val title: String,
        val poster: String
    )

    private val indexedSources = listOf(
        IndexedSource(
            "mollywood-tv",
            "$mainUrl/live/mollywood-tv/",
            3,
            "Asianet HD - JIO",
            "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_HD/images/LOGO_HD/image.png"
        ),
        IndexedSource(
            "mollywood-tv",
            "$mainUrl/live/mollywood-tv/",
            6,
            "Asianet Movies HD",
            "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_MOVIES_HD/images/LOGO_HD/image.png"
        ),
        IndexedSource(
            "mollywood-tv",
            "$mainUrl/live/mollywood-tv/",
            8,
            "Asianet Plus",
            "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_PLUS/images/LOGO_HD/image.png"
        ),
        IndexedSource(
            "mollywood-tv",
            "$mainUrl/live/mollywood-tv/",
            10,
            "Zee Keralam HD",
            "https://akamaividz2.zee5.com/image/upload/resources/0-9-129/channel_list/1170x658withlogoea00fd123614470c9f82e2fde66280e4.png"
        ),
        IndexedSource(
            "mollywood-plus",
            "$mainUrl/live/mollywood-plus/",
            1,
            "Surya TV FHD",
            "https://sund-images.sunnxt.com/194397/1000x1000_SuryaTVHD_194397_4c99c17b-92d4-49be-a490-b5958067190a.png"
        ),
        IndexedSource(
            "mollywood-plus",
            "$mainUrl/live/mollywood-plus/",
            4,
            "Surya Movies",
            "https://sund-images.sunnxt.com/9019/1000x1000_71ddcc0b-16e7-48e9-9998-aa023200f4bc.jpg"
        ),
        IndexedSource(
            "mollywood-plus",
            "$mainUrl/live/mollywood-plus/",
            5,
            "Surya Comedy",
            "https://sund-images.sunnxt.com/30835/1000x1000_143a4af4-2f02-4c9c-814b-af149e6a5a95.jpg"
        )
    )

    private val containerSlugs = setOf("mollywood-tv", "mollywood-plus", "mollywood-max")
    private val indexedFallbacks = mapOf(
        "mollywood-tv:3" to FallbackMedia(
            "https://bhoomtv.net/geo/live.m3u8?id=443",
            "https://bhoomtv.net/geo/watch/443"
        ),
        // Asianet Movies is intentionally resolved only from the BHOOM page/API.
        // Do not hard-code a third-party stream here.

        "mollywood-tv:8" to FallbackMedia(
            "https://as-net.keralive.workers.dev/v1/master/a0d007312bfd99c47f76b77ae26b1ccdaae76cb1/asianetplus_live_https/index.m3u8",
            "https://bhoomtv.me/jwplayer/"
        ),
        "mollywood-tv:10" to FallbackMedia(
            "https://bhoomtv.net/geo/live.m3u8?id=1641",
            "https://bhoomtv.net/geo/watch/1641"
        ),
        "mollywood-plus:1" to FallbackDrm(
            "https://livestream6.sunnxt.com/30612a1b269d4a18aa14657641c47515/SuryaTVB_IN_index.mpd",
            "56e1f5b5b72e4e45a98b6f287c265ab9",
            "6dee8663e63cc8f8dda8478b8b2f3b71",
            "https://bhoomtv.me/jwplayer/"
        ),
        "mollywood-plus:4" to FallbackDrm(
            "https://nxliv.com/sunxt/livestream.sunnxt.com/e24ee14c395945bd8ccb065e1bce8b9b/SuryaMoviesB_IN_index.mpd",
            "6b67bccef7024f2da29b42e10dc13f89",
            "2e8460c47d3f01693e193dba5963a5e1",
            "https://nxliv.com/sunxt/Play.php?c=13"
        ),
        "mollywood-plus:5" to FallbackDrm(
            "https://nxliv.com/sunxt/livestream.sunnxt.com/6505e922bf164423ad122f404747356a/SuryaComedyB_IN_index.mpd",
            "11563b00a46b43f2a0f80ecf42a4fb77",
            "9bad28ad6f23dbb917c63ee680f66a1f",
            "https://nxliv.com/sunxt/Play.php?c=15"
        )
    )

    private val preferredSourceIndexes = mapOf(
        "kairali-tv" to listOf(2, 4, 3, 1),
        "mazhavil-hd" to listOf(2, 3, 4, 5, 1)
    )

    private val preferredLiveFallbacks = mapOf(
        "kairali-tv" to FallbackMedia(
            "https://streams.tangotv.in/KAIRALI/ORIGIN/index.m3u8",
            "https://bhoomtv.me/jwplayer/"
        ),
        "mazhavil-manorama-sd" to FallbackMedia(
            "https://ddozob4sbfsmt.cloudfront.net/out/v1/51aaeddf56854312add90dfa8df07e39/index.m3u8",
            "https://bhoomtv.me/jwplayer/"
        )
    )

    private data class FallbackMedia(
        val url: String,
        val referer: String
    )

    private data class FallbackDrm(
        val url: String,
        val kid: String,
        val key: String,
        val referer: String
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageNumber = page.coerceAtLeast(1)
        val pageUrl = if (pageNumber == 1) malayalamPage
        else "$mainUrl/channel/malayalam/page/$pageNumber/"

        val doc = app.get(pageUrl, referer = mainUrl, headers = browserHeaders()).document
        val items = parseChannelCards(doc).toMutableList()

        if (pageNumber == 1) {
            items += indexedSources.map { source ->
                newLiveSearchResponse(source.title, indexedDataUrl(source)) {
                    posterUrl = source.poster
                }
            }
        }

        val maxPage = doc.select("a[href*='/channel/malayalam/page/']")
            .mapNotNull {
                Regex("""/channel/malayalam/page/(\d+)/?""")
                    .find(it.attr("href"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()

        val hasNext = maxPage?.let { pageNumber < it }
            ?: (items.isNotEmpty() && pageNumber < 4)

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Malayalam Live TV",
                    items.distinctBy { it.url },
                    isHorizontalImages = false
                )
            ),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()

        val normal = (1..4)
            .map { page ->
                val pageUrl = if (page == 1) malayalamPage
                else "$mainUrl/channel/malayalam/page/$page/"
                app.get(pageUrl, referer = mainUrl, headers = browserHeaders()).document
            }
            .flatMap(::parseChannelCards)
            .filter { q.isBlank() || it.name.contains(q, true) }

        val indexed = indexedSources
            .filter { q.isBlank() || it.title.contains(q, true) }
            .map { source ->
                newLiveSearchResponse(source.title, indexedDataUrl(source)) {
                    posterUrl = source.poster
                }
            }

        return (normal + indexed).distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        indexedSources.firstOrNull { indexedDataUrl(it) == url }?.let { source ->
            return newLiveStreamLoadResponse(source.title, url, url) {
                posterUrl = source.poster
            }
        }

        val response = app.get(url, referer = mainUrl, headers = browserHeaders())
        val title = response.document.selectFirst("h1")?.text()?.trim()
            ?: response.document.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: "BHOOM TV Channel"
        val poster = response.document.selectFirst("meta[property='og:image']")?.attr("content")?.trim()

        return newLiveStreamLoadResponse(title, url, url) {
            posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val indexed = indexedSources.firstOrNull { indexedDataUrl(it) == data }
        return if (indexed != null) {
            resolveIndexedSource(indexed, callback)
        } else {
            resolveLivePage(data, callback, subtitleCallback)
        }
    }

    private suspend fun resolveIndexedSource(
        source: IndexedSource,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        when (val fallback = indexedFallbacks[source.pageId + ":" + source.index]) {
            is FallbackMedia -> return emitMedia(fallback.url, fallback.referer, callback)
            is FallbackDrm -> return emitClearKey(
                fallback.url,
                fallback.referer,
                fallback.kid,
                fallback.key,
                callback
            )
            null -> Unit
        }

        val pageResponse = runCatching {
            app.get(source.pageUrl, referer = mainUrl, headers = browserHeaders())
        }.getOrNull()

        val option = pageResponse?.document?.let {
            findSourceOption(it, source.index)
                ?: it.select("li.dooplay_player_option")
                    .firstOrNull { item ->
                        normalize(item.selectFirst(".title")?.text().orEmpty()) == normalize(source.title)
                    }
        }

        if (option != null) {
            // First try the exact source option itself. Some BHOOM entries put
            // the real iframe/stream directly in the option attributes.
            val direct = candidatesFromElement(option, source.pageUrl)
            for (candidate in direct) {
                if (resolveEmbed(candidate, source.pageUrl, callback)) return true
            }

            val post = option.attr("data-post").trim()
            val type = option.attr("data-type").ifBlank { "movie" }
            val nume = option.attr("data-nume").toIntOrNull() ?: source.index

            if (post.isNotBlank() && resolveDooplaySource(source.pageUrl, post, type, nume, callback)) {
                return true
            }
        }

        return when (val fallback = indexedFallbacks[source.pageId + ":" + source.index]) {
            is FallbackMedia -> emitMedia(fallback.url, fallback.referer, callback)
            is FallbackDrm -> emitClearKey(
                fallback.url,
                fallback.referer,
                fallback.kid,
                fallback.key,
                callback
            )
            null -> false
            else -> false
        }
    }

    private suspend fun resolveLivePage(
        pageUrl: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        val response = runCatching {
            app.get(pageUrl, referer = mainUrl, headers = browserHeaders())
        }.getOrNull() ?: return false

        val options = response.document
            .select("li.dooplay_player_option[data-post][data-nume]")
            .distinctBy {
                it.attr("data-post") + ":" + it.attr("data-type") + ":" + it.attr("data-nume")
            }

        val slug = pageUrl.substringAfter("/live/").substringBefore("/").lowercase()

        preferredLiveFallbacks[slug]?.let { fallback ->
            if (emitMedia(fallback.url, fallback.referer, callback)) return true
        }

        val preferred = preferredSourceIndexes[slug].orEmpty()

        val ordered = options.sortedWith(
            compareBy<org.jsoup.nodes.Element> {
                val n = it.attr("data-nume").toIntOrNull() ?: Int.MAX_VALUE
                val p = preferred.indexOf(n)
                if (p < 0) Int.MAX_VALUE else p
            }.thenBy {
                it.attr("data-nume").toIntOrNull() ?: Int.MAX_VALUE
            }
        )

        for (option in ordered.take(12)) {
            val post = option.attr("data-post").trim()
            val type = option.attr("data-type").ifBlank { "movie" }
            val nume = option.attr("data-nume").toIntOrNull() ?: continue
            if (post.isBlank()) continue

            if (resolveDooplaySource(pageUrl, post, type, nume, callback)) return true
        }

        val direct = linkedSetOf<String>()
        direct += extractMediaUrls(response.text, pageUrl)

        response.document.select(
            "iframe[src],iframe[data-src],video[src],video source[src],source[src]," +
                "[data-src],[data-url],[data-stream],[data-playlist],[data-file],[data-player]"
        ).forEach {
            direct += candidatesFromElement(it, pageUrl)
        }

        return dispatchDirectCandidates(direct, pageUrl, callback, subtitleCallback)
    }

    private suspend fun resolveDooplaySource(
        pageUrl: String,
        post: String,
        type: String,
        nume: Int,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val apiUrls = listOf(
            "$mainUrl/wp-json/dooplayer/v2/$post/$type/$nume",
            "https://bhoomtv.me/wp-json/dooplayer/v2/$post/$type/$nume"
        )

        for (apiUrl in apiUrls.distinct()) {
            val response = runCatching {
                app.get(
                    apiUrl,
                    referer = pageUrl,
                    headers = mapOf(
                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                        "X-Requested-With" to "XMLHttpRequest",
                        "Origin" to mainUrl,
                        "User-Agent" to USER_AGENT
                    )
                )
            }.getOrNull() ?: continue

            if (!response.isSuccessful) continue

            val embedUrl = runCatching {
                JSONObject(response.text).optString("embed_url").trim()
            }.getOrNull().orEmpty()

            if (embedUrl.isBlank()) continue

            val normalized = normalizeUrl(embedUrl, pageUrl) ?: continue
            if (resolveEmbed(normalized, pageUrl, callback)) return true
        }

        return false
    }

    private suspend fun resolveEmbed(
        embedUrl: String,
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isMediaUrl(embedUrl)) {
            return emitMedia(embedUrl, pageUrl, callback)
        }

        if (embedUrl.contains("$mainUrl/jwplayer/", true)) {
            extractQueryParameter(embedUrl, "source")?.let { source ->
                val media = normalizeUrl(source, embedUrl)
                if (media != null && isMediaUrl(media) && emitMedia(media, embedUrl, callback)) {
                    return true
                }
            }
        }

        if (embedUrl.contains("b4uplay.com/player.php", true)) {
            val mpd = extractQueryParameter(embedUrl, "mpd")
            val kid = extractQueryParameter(embedUrl, "keyId")
            val key = extractQueryParameter(embedUrl, "key")

            if (!mpd.isNullOrBlank() && !kid.isNullOrBlank() && !key.isNullOrBlank()) {
                if (emitClearKey(mpd, embedUrl, kid, key, callback)) return true
            }
        }

        Regex(
            """^https?://(?:www\.)?bhoomtv\.net/geo/(?:watch/(\d+)|watch\.php\?id=(\d+))""",
            RegexOption.IGNORE_CASE
        ).find(embedUrl)?.let { match ->
            val id = match.groupValues[1].ifBlank { match.groupValues[2] }.toIntOrNull()
            if (id != null && resolveGeoChannel(id, embedUrl, callback)) return true
        }

        if (embedUrl.contains("nxliv.com/", true) && resolveNxliv(embedUrl, pageUrl, callback)) {
            return true
        }

        // BHOOM may return an embed page that is not one of the special
        // providers above. Let CloudStream's extractor handle that BHOOM
        // embed instead of rejecting it as "no links found".
        runCatching {
            loadExtractor(
                url = embedUrl,
                referer = pageUrl,
                subtitleCallback = { },
                callback = {
                    callback(it)
                }
            )
        }.getOrNull()?.let {
            return true
        }

        val page = runCatching {
            app.get(embedUrl, referer = pageUrl, headers = browserHeaders())
        }.getOrNull()

        if (page != null) {
            val directMedia = extractMediaUrls(page.text, embedUrl)
            for (media in directMedia) {
                if (emitMedia(media, embedUrl, callback)) return true
            }

            parsePlayerStream(page.text)?.let { parsed ->
                if (emitParsedPlayer(parsed, embedUrl, callback)) return true
            }
        }

        return false
    }

    private suspend fun resolveGeoChannel(
        id: Int,
        embedUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = runCatching {
            app.post(
                url = "$bhoomNetUrl/geo/rest-api/public",
                data = mapOf("action" to "get_channel_detail", "id" to id.toString()),
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Accept" to "application/json, text/plain, */*",
                    "Origin" to bhoomNetUrl,
                    "User-Agent" to USER_AGENT
                ),
                referer = embedUrl
            )
        }.getOrNull()

        if (response != null && response.isSuccessful) {
            val json = runCatching { JSONObject(response.text) }.getOrNull()
            val playback = json?.optJSONObject("data")
                ?.optJSONObject("playback")
                ?.optJSONObject("data")

            if (playback != null) {
                val abr = playback.optString("abr").trim()
                val dbr = playback.optString("dbr").trim()
                val drm = playback.optBoolean("drm", false)

                if (abr.isNotBlank()) {
                    val kid = firstNonBlank(playback.optString("kid"), playback.optString("keyId"))
                    val key = firstNonBlank(playback.optString("key"), playback.optString("clearkey"))

                    if (drm && kid != null && key != null && emitClearKey(abr, embedUrl, kid, key, callback)) {
                        return true
                    }

                    if (drm && dbr.isNotBlank() && emitWidevine(abr, embedUrl, dbr, callback)) {
                        return true
                    }

                    if (emitMedia(abr, embedUrl, callback)) return true
                }
            }
        }

        return emitMedia(
            "$bhoomNetUrl/geo/live.m3u8?id=$id",
            embedUrl,
            callback
        )
    }

    private suspend fun resolveNxliv(
        embedUrl: String,
        pageUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = runCatching {
            app.get(embedUrl, referer = pageUrl, headers = browserHeaders())
        }.getOrNull() ?: return false

        val html = response.text
        val stream = Regex(
            """(?i)(?:let|var|const)\s+streamSource\s*=\s*["']([^"']+)["']"""
        ).find(html)?.groupValues?.getOrNull(1)
            ?.let { normalizeUrl(it, embedUrl) }
            ?: return false

        val keyId = Regex(
            """(?i)(?:const|let|var)\s+keyId\s*=\s*["']([^"']+)["']"""
        ).find(html)?.groupValues?.getOrNull(1)?.trim()

        val key = Regex(
            """(?i)(?:const|let|var)\s+key\s*=\s*["']([^"']+)["']"""
        ).find(html)?.groupValues?.getOrNull(1)?.trim()

        if (!isMediaUrl(stream)) return false

        return if (!keyId.isNullOrBlank() && !key.isNullOrBlank()) {
            emitClearKey(stream, embedUrl, keyId, key, callback)
        } else {
            emitMedia(stream, embedUrl, callback)
        }
    }

    private suspend fun emitMedia(
        url: String,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val normalized = normalizeUrl(url, referer) ?: return false
        if (!isMediaUrl(normalized)) return false

        val type = if (normalized.contains(".mpd", true)) {
            ExtractorLinkType.DASH
        } else {
            ExtractorLinkType.M3U8
        }

        val headers = mutableMapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "*/*"
        )

        when {
            normalized.contains("streams.tangotv.in", true) ||
                normalized.contains("keralive.workers.dev", true) ||
                normalized.contains("vgcdn.net", true) ||
                normalized.contains("akamaized.net", true) -> {
                headers["Origin"] = "https://bhoomtv.me"
            }
        }

        callback(
            newExtractorLink(
                source = name,
                name = name,
                url = normalized,
                type = type
            ) {
                this.referer = referer
                this.headers = headers
                quality = Qualities.Unknown.value
            }
        )

        return true
    }

    private suspend fun emitClearKey(
        url: String,
        referer: String,
        kid: String,
        key: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val normalized = normalizeUrl(url, referer) ?: return false
        if (!normalized.contains(".mpd", true)) return false

        val origin = runCatching {
            val uri = URI(referer)
            uri.scheme.orEmpty() + "://" + uri.host.orEmpty()
        }.getOrDefault("https://nxliv.com")

        callback(
            newDrmExtractorLink(
                source = name,
                name = name,
                url = normalized,
                type = ExtractorLinkType.DASH,
                uuid = CLEARKEY_DRM_UUID
            ) {
                this.kid = hexToBase64(kid)
                this.key = hexToBase64(key)
                this.kty = "oct"
                this.referer = referer
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "*/*",
                    "Origin" to origin
                )
                quality = Qualities.Unknown.value
            }
        )

        return true
    }

    private suspend fun emitWidevine(
        url: String,
        referer: String,
        licenseUrl: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val normalized = normalizeUrl(url, referer) ?: return false
        if (!normalized.contains(".mpd", true)) return false

        callback(
            newDrmExtractorLink(
                source = name,
                name = name,
                url = normalized,
                type = ExtractorLinkType.DASH,
                uuid = WIDEVINE_DRM_UUID
            ) {
                this.licenseUrl = licenseUrl
                this.referer = referer
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "*/*"
                )
                quality = Qualities.Unknown.value
            }
        )

        return true
    }

    private data class ParsedPlayer(
        val url: String,
        val kid: String? = null,
        val key: String? = null
    )

    private fun parsePlayerStream(html: String): ParsedPlayer? {
        val stream = Regex(
            """(?i)(?:let|var|const)\s+streamSource\s*=\s*["']([^"']+)["']"""
        ).find(html)?.groupValues?.getOrNull(1) ?: return null

        val keyId = Regex(
            """(?i)(?:const|let|var)\s+keyId\s*=\s*["']([^"']+)["']"""
        ).find(html)?.groupValues?.getOrNull(1)

        val key = Regex(
            """(?i)(?:const|let|var)\s+key\s*=\s*["']([^"']+)["']"""
        ).find(html)?.groupValues?.getOrNull(1)

        return ParsedPlayer(stream, keyId, key)
    }

    private suspend fun emitParsedPlayer(
        parsed: ParsedPlayer,
        referer: String,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return if (!parsed.kid.isNullOrBlank() && !parsed.key.isNullOrBlank()) {
            emitClearKey(parsed.url, referer, parsed.kid, parsed.key, callback)
        } else {
            emitMedia(parsed.url, referer, callback)
        }
    }

    private suspend fun dispatchDirectCandidates(
        candidates: Collection<String>,
        referer: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Boolean {
        var found = false

        for (candidate in candidates.mapNotNull { normalizeUrl(it, referer) }.distinct().take(12)) {
            if (isMediaUrl(candidate)) {
                emitMedia(candidate, referer, callback)
                found = true
                continue
            }

            val page = runCatching {
                app.get(candidate, referer = referer, headers = browserHeaders())
            }.getOrNull() ?: continue

            extractMediaUrls(page.text, candidate).forEach {
                emitMedia(it, candidate, callback)
                found = true
            }

            runCatching {
                loadExtractor(
                    url = candidate,
                    referer = referer,
                    subtitleCallback = subtitleCallback,
                    callback = {
                        callback(it)
                        found = true
                    }
                )
            }
        }

        return found
    }

    private fun parseChannelCards(doc: Document): List<SearchResponse> =
        doc.select("a[href*='/live/']")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (!href.contains("/live/")) return@mapNotNull null

                val slug = href.substringAfter("/live/").substringBefore("/").lowercase()
                if (slug in containerSlugs) return@mapNotNull null

                val title = anchor.selectFirst("h2,h3,.title,.entry-title")?.text()?.trim()
                    ?: anchor.text().trim()

                if (title.isBlank()) return@mapNotNull null

                newLiveSearchResponse(title, href) {
                    posterUrl = findPoster(anchor)
                }
            }
            .distinctBy { it.url }

    private fun findSourceOption(doc: Document, index: Int): Element? =
        doc.selectFirst("li.dooplay_player_option[data-nume='$index']")

    private fun candidatesFromElement(element: Element, baseUrl: String): Set<String> {
        val result = linkedSetOf<String>()

        listOf(
            "href", "src", "data-src", "data-url", "data-href",
            "data-stream", "data-playlist", "data-file", "data-player",
            "data-video", "data-embed"
        ).forEach { attr ->
            val value = element.attr(attr)
            if (value.isNotBlank()) result += extractUrls(value, baseUrl)
        }

        val onclick = element.attr("onclick")
        if (onclick.isNotBlank()) result += extractUrls(onclick, baseUrl)
        result += extractUrls(element.outerHtml(), baseUrl)

        return result
    }

    private fun extractUrls(text: String, baseUrl: String): Set<String> {
        val result = linkedSetOf<String>()
        result += extractMediaUrls(text, baseUrl)

        Regex("""(?i)https?://[^"'\s<>]+""").findAll(text).forEach {
            normalizeUrl(it.value, baseUrl)?.let { url ->
                if (isWebUrl(url)) result += url
            }
        }

        return result
    }

    private fun extractMediaUrls(text: String, baseUrl: String): Set<String> {
        val result = linkedSetOf<String>()
        Regex("""(?i)(?:https?:)?//[^"'\s<>\\]+\.(?:m3u8|mpd)(?:\?[^"'\s<>\\]*)?""")
            .findAll(text)
            .forEach {
                normalizeUrl(it.value, baseUrl)?.let(result::add)
            }
        return result
    }

    private fun extractQueryParameter(url: String, name: String): String? {
        val query = runCatching { URI(url).rawQuery }.getOrNull() ?: return null

        return query.split("&")
            .firstOrNull { it.substringBefore("=") == name }
            ?.substringAfter("=", "")
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
    }

    private fun normalizeUrl(raw: String?, baseUrl: String): String? {
        if (raw.isNullOrBlank()) return null

        val value = raw.trim()
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")
            .trim('"', '\'')

        if (value.startsWith("javascript:", true)) return null
        if (value.startsWith("data:", true)) return null

        return runCatching {
            when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://", true) || value.startsWith("https://", true) -> value
                else -> URI(baseUrl).resolve(value).toString()
            }
        }.getOrNull()
    }

    private fun isMediaUrl(url: String): Boolean =
        url.contains(".m3u8", true) || url.contains(".mpd", true)

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", true) || url.startsWith("https://", true)

    private fun indexedDataUrl(source: IndexedSource): String =
        "bhoom-index://" + source.pageId + "/" + source.index

    private fun browserHeaders(): Map<String, String> =
        mapOf(
            "User-Agent" to USER_AGENT,
            "Accept" to "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8"
        )

    private fun hexToBase64(value: String): String {
        val cleaned = value.trim().removePrefix("0x")

        if (
            cleaned.isEmpty() ||
            cleaned.length % 2 != 0 ||
            !cleaned.matches(Regex("[0-9a-fA-F]+"))
        ) {
            return value
        }

        val bytes = ByteArray(cleaned.length / 2)

        for (i in bytes.indices) {
            bytes[i] = cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun firstNonBlank(vararg values: String): String? =
        values.firstOrNull { it.isNotBlank() }

    private fun findPoster(anchor: Element): String? {
        anchor.selectFirst("img")?.let {
            val src = it.absUrl("src").ifBlank { it.attr("src") }
            if (src.isNotBlank()) return src
        }

        var parent = anchor.parent()

        repeat(5) {
            if (parent == null) return@repeat

            parent.selectFirst("img")?.let {
                val src = it.absUrl("src").ifBlank { it.attr("src") }
                if (src.isNotBlank()) return src
            }

            parent = parent.parent()
        }

        return null
    }

    private fun normalize(value: String): String =
        value.replace('\u00A0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
}
