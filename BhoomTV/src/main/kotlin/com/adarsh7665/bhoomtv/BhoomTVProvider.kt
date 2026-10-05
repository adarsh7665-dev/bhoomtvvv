package com.adarsh7665.bhoomtv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

class BhoomTVProvider : MainAPI() {
    override var mainUrl = "https://bhoomtv.org"
    override var name = "BHOOM TV"
    override var lang = "ml"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    private val malayalamPage = "$mainUrl/channel/malayalam/"

    private data class IndexedSource(
        val pageId: String,
        val pageUrl: String,
        val index: Int,
        val title: String,
        val poster: String
    )

    /*
     * BHOOM source indexes confirmed by the source list:
     *
     * Mollywood TV
     *   #3  Asianet HD - JIO
     *   #6  Asianet Movies HD
     *   #8  Asianet Plus
     *   #10 Zee Keralam HD
     *
     * Mollywood Plus
     *   #1  Surya TV FHD
     *   #4  Surya Movies
     *   #5  Surya Comedy
     *
     * These are BHOOM source positions, not replacement stream URLs.
     */
    private val indexedSources = listOf(
        IndexedSource(
            pageId = "mollywood-tv",
            pageUrl = "$mainUrl/live/mollywood-tv/",
            index = 3,
            title = "Asianet HD - JIO",
            poster = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_HD/images/LOGO_HD/image.png"
        ),
        IndexedSource(
            pageId = "mollywood-tv",
            pageUrl = "$mainUrl/live/mollywood-tv/",
            index = 6,
            title = "Asianet Movies HD",
            poster = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_MOVIES_HD/images/LOGO_HD/image.png"
        ),
        IndexedSource(
            pageId = "mollywood-tv",
            pageUrl = "$mainUrl/live/mollywood-tv/",
            index = 8,
            title = "Asianet Plus",
            poster = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_PLUS/images/LOGO_HD/image.png"
        ),
        IndexedSource(
            pageId = "mollywood-tv",
            pageUrl = "$mainUrl/live/mollywood-tv/",
            index = 10,
            title = "Zee Keralam HD",
            poster = "https://akamaividz2.zee5.com/image/upload/resources/0-9-129/channel_list/1170x658withlogoea00fd123614470c9f82e2fde66280e4.png"
        ),
        IndexedSource(
            pageId = "mollywood-plus",
            pageUrl = "$mainUrl/live/mollywood-plus/",
            index = 1,
            title = "Surya TV FHD",
            poster = "https://sund-images.sunnxt.com/194397/1000x1000_SuryaTVHD_194397_4c99c17b-92d4-49be-a490-b5958067190a.png"
        ),
        IndexedSource(
            pageId = "mollywood-plus",
            pageUrl = "$mainUrl/live/mollywood-plus/",
            index = 4,
            title = "Surya Movies",
            poster = "https://sund-images.sunnxt.com/9019/1000x1000_71ddcc0b-16e7-48e9-9998-aa023200f4bc.jpg"
        ),
        IndexedSource(
            pageId = "mollywood-plus",
            pageUrl = "$mainUrl/live/mollywood-plus/",
            index = 5,
            title = "Surya Comedy",
            poster = "https://sund-images.sunnxt.com/30835/1000x1000_143a4af4-2f02-4c9c-814b-af149e6a5a95.jpg"
        )
    )

    private val knownSourceNames = mapOf(
        "mollywood-tv" to listOf(
            "Asianet HD",
            "Asianet SD",
            "Asianet HD - JIO",
            "Asianet HD USA",
            "Asianet Movies SD",
            "Asianet Movies HD",
            "Asianet Movies HD USA",
            "Asianet Plus",
            "Asianet Plus UK",
            "Zee Keralam HD",
            "Zee Keralam SD"
        ),
        "mollywood-plus" to listOf(
            "Surya TV FHD",
            "Surya TV HD",
            "Surya TV",
            "Surya Movies",
            "Surya Comedy",
            "Surya Music",
            "Kochu TV"
        )
    )

    private val containerSlugs = setOf("mollywood-tv", "mollywood-plus", "mollywood-max")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageNumber = page.coerceAtLeast(1)
        val url = if (pageNumber == 1) {
            malayalamPage
        } else {
            "$mainUrl/channel/malayalam/page/$pageNumber/"
        }

        val doc = app.get(url, referer = mainUrl).document
        val items = parseChannelCards(doc).toMutableList()

        if (pageNumber == 1) {
            items += indexedSources.map { source ->
                newLiveSearchResponse(source.title, indexedDataUrl(source)) {
                    posterUrl = source.poster
                }
            }
        }

        val maxPage = doc.select("a[href*='/channel/malayalam/page/']")
            .mapNotNull { Regex("""/page/(\d+)/?""").find(it.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .maxOrNull()

        val hasNext = maxPage?.let { pageNumber < it } ?: (items.isNotEmpty() && pageNumber < 4)

        return newHomePageResponse(
            listOf(HomePageList("Malayalam Live TV", items.distinctBy { it.url }, isHorizontalImages = false)),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()

        val pages = (1..4).map { page ->
            val url = if (page == 1) malayalamPage else "$mainUrl/channel/malayalam/page/$page/"
            app.get(url, referer = mainUrl).document
        }

        val regular = pages
            .flatMap(::parseChannelCards)
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }

        val indexed = indexedSources
            .filter { q.isBlank() || it.title.contains(q, ignoreCase = true) }
            .map { source ->
                newLiveSearchResponse(source.title, indexedDataUrl(source)) {
                    posterUrl = source.poster
                }
            }

        return (regular + indexed).distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val indexed = indexedSources.firstOrNull { indexedDataUrl(it) == url }

        if (indexed != null) {
            return newLiveStreamLoadResponse(indexed.title, url, url) {
                posterUrl = indexed.poster
            }
        }

        val response = app.get(url, referer = mainUrl)
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
            resolveIndexedSource(indexed, subtitleCallback, callback)
        } else {
            resolvePage(data, mainUrl, subtitleCallback, callback)
        }
    }

    private suspend fun resolveIndexedSource(
        source: IndexedSource,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(source.pageUrl, referer = mainUrl)
        val doc = response.document
        val html = response.text

        val labels = findSourceLabels(doc, source.pageId)
        val selected = labels.getOrNull(source.index - 1)

        val candidates = linkedSetOf<String>()

        if (selected != null) {
            candidates += candidatesFromElement(selected, source.pageUrl)

            var parent = selected.parent()
            repeat(7) {
                if (parent == null) return@repeat
                candidates += candidatesFromElement(parent, source.pageUrl)
                parent = parent.parent()
            }
        }

        /*
         * Exact source-name fallback: useful when the DOM order has wrappers
         * around the source item.
         */
        candidates += doc.getAllElements()
            .filter { normalize(it.ownText()) == normalize(source.title) }
            .flatMap { candidatesFromElement(it, source.pageUrl) }

        /*
         * Only after resolving the selected BHOOM source do we scan the
         * complete page for media/player URLs. Never substitute another
         * provider URL.
         */
        if (candidates.none(::isMediaUrl)) {
            val media = extractMediaUrls(html, source.pageUrl).toList()
            media.getOrNull(source.index - 1)?.let { candidates += it }
        }

        return dispatchCandidates(candidates, source.pageUrl, subtitleCallback, callback)
    }

    private suspend fun resolvePage(
        pageUrl: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(pageUrl, referer = referer)
        val doc = response.document

        val candidates = linkedSetOf<String>()
        candidates += extractMediaUrls(response.text, pageUrl)

        doc.select(
            "iframe[src],iframe[data-src],video[src],video source[src],source[src]," +
                "[data-src],[data-url],[data-stream],[data-playlist],[data-file],[data-player]"
        ).forEach {
            candidates += candidatesFromElement(it, pageUrl)
        }

        candidates += extractScriptCandidates(response.text, pageUrl)

        return dispatchCandidates(candidates, pageUrl, subtitleCallback, callback)
    }

    private suspend fun dispatchCandidates(
        candidates: Collection<String>,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var count = 0

        for (candidate in candidates.mapNotNull { normalizeUrl(it, referer) }.distinct().take(20)) {
            if (isMediaUrl(candidate)) {
                val type = if (candidate.contains(".mpd", true)) ExtractorLinkType.DASH else ExtractorLinkType.M3U8

                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = candidate,
                        type = type
                    ) {
                        this.referer = referer
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Accept" to "*/*"
                        )
                        quality = Qualities.Unknown.value
                    }
                )

                count++
                continue
            }

            if (!isWebUrl(candidate)) continue

            /*
             * Inspect the candidate embed/player first. This catches BHOOM
             * source pages that expose the real media only in iframe/script
             * markup.
             */
            try {
                val page = app.get(candidate, referer = referer)
                val media = linkedSetOf<String>()

                media += extractMediaUrls(page.text, candidate)

                page.document.select(
                    "video[src],video source[src],source[src],iframe[src]," +
                        "[data-src],[data-url],[data-stream],[data-playlist],[data-file]"
                ).forEach {
                    media += candidatesFromElement(it, candidate)
                }

                for (mediaUrl in media.mapNotNull { normalizeUrl(it, candidate) }.distinct().take(10)) {
                    if (!isMediaUrl(mediaUrl)) continue

                    val type = if (mediaUrl.contains(".mpd", true)) ExtractorLinkType.DASH else ExtractorLinkType.M3U8

                    callback(
                        newExtractorLink(
                            source = name,
                            name = name,
                            url = mediaUrl,
                            type = type
                        ) {
                            this.referer = candidate
                            headers = mapOf(
                                "User-Agent" to USER_AGENT,
                                "Accept" to "*/*"
                            )
                            quality = Qualities.Unknown.value
                        }
                    )

                    count++
                }

                if (media.isNotEmpty()) continue
            } catch (_: Exception) {
                // Try CloudStream's extractor registry below.
            }

            try {
                var extracted = 0
                loadExtractor(
                    url = candidate,
                    referer = referer,
                    subtitleCallback = subtitleCallback,
                    callback = {
                        extracted++
                        callback(it)
                    }
                )
                count += extracted
            } catch (_: Exception) {
                // Dead source/player: continue with the next BHOOM candidate.
            }
        }

        return count > 0
    }

    private fun parseChannelCards(doc: Document): List<SearchResponse> =
        doc.select("a[href*='/live/']")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (href.isBlank()) return@mapNotNull null

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

    private fun findSourceLabels(doc: Document, pageId: String): List<Element> {
        val allowed = knownSourceNames.getValue(pageId).map(::normalize).toSet()

        return doc.getAllElements()
            .filter { normalize(it.ownText()) in allowed }
            .distinctBy { normalize(it.ownText()) }
    }

    private fun candidatesFromElement(element: Element, baseUrl: String): Set<String> {
        val result = linkedSetOf<String>()

        val attrs = listOf(
            "href", "src", "data-src", "data-url", "data-href",
            "data-stream", "data-playlist", "data-file", "data-player",
            "data-video", "data-embed"
        )

        attrs.forEach { attr ->
            val value = element.attr(attr)
            if (value.isNotBlank()) result += extractUrls(value, baseUrl)
        }

        val onclick = element.attr("onclick")
        if (onclick.isNotBlank()) result += extractUrls(onclick, baseUrl)

        result += extractUrls(element.outerHtml(), baseUrl)
        return result
    }

    private fun extractScriptCandidates(html: String, baseUrl: String): Set<String> {
        val result = linkedSetOf<String>()

        val patterns = listOf(
            Regex("""(?i)["'](?:file|src|source|stream|url|playlist|hls|dash)["']\s*[:=]\s*["']([^"']+)["']"""),
            Regex("""(?i)["'](?:player|embed|iframe|video)["']\s*[:=]\s*["']([^"']+)["']""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(html).forEach {
                result += extractUrls(it.groupValues[1], baseUrl)
            }
        }

        return result
    }

    private fun extractMediaUrls(text: String, baseUrl: String): Set<String> {
        val result = linkedSetOf<String>()

        val patterns = listOf(
            Regex("""(?i)(?:https?:)?//[^"'\s<>\\]+\.(?:m3u8|mpd)(?:\?[^"'\s<>\\]*)?"""),
            Regex("""(?i)https?://[^"'\s<>]+(?:m3u8|mpd)(?:\?[^"'\s<>]*)?""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(text).forEach {
                normalizeUrl(it.value, baseUrl)?.let(result::add)
            }
        }

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

        Regex("""["'](\/[^"']+)["']""").findAll(text).forEach {
            normalizeUrl(it.groupValues[1], baseUrl)?.let { url ->
                if (isWebUrl(url)) result += url
            }
        }

        return result
    }

    private fun normalizeUrl(raw: String?, baseUrl: String): String? {
        if (raw.isNullOrBlank()) return null

        val value = raw.trim()
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("&amp;", "&")
            .trim('"', '\'')

        if (value.startsWith("javascript:", true) || value.startsWith("data:", true)) return null

        return try {
            when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://", true) || value.startsWith("https://", true) -> value
                else -> URI(baseUrl).resolve(value).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isMediaUrl(url: String): Boolean =
        url.contains(".m3u8", true) || url.contains(".mpd", true)

    private fun isWebUrl(url: String): Boolean =
        url.startsWith("http://", true) || url.startsWith("https://", true)

    private fun indexedDataUrl(source: IndexedSource): String =
        "bhoom-index://" + source.pageId + "/" + source.index

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
