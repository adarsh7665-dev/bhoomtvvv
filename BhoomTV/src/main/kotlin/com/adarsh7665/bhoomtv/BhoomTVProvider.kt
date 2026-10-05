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

    private val channelPage = "$mainUrl/channel/malayalam/"

    private data class IndexedTarget(
        val pageId: String,
        val sourceIndex: Int,
        val name: String,
        val poster: String
    )

    /*
     * These are BHOOM's own numbered source positions.
     *
     * Mollywood TV:
     *   #3  Asianet HD - JIO
     *   #6  Asianet Movies HD
     *   #8  Asianet Plus
     *   #10 Zee Keralam HD
     *
     * Mollywood Plus:
     *   #1 Surya TV FHD
     *   #4 Surya Movies
     *   #5 Surya Comedy
     *
     * The extension resolves these by reading the current BHOOM page at
     * playback time, so the stream URL itself is not hard-coded here.
     */
    private val indexedTargets = listOf(
        IndexedTarget(
            pageId = "mollywood-tv",
            sourceIndex = 3,
            name = "Asianet HD - JIO",
            poster = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_HD/images/LOGO_HD/image.png"
        ),
        IndexedTarget(
            pageId = "mollywood-tv",
            sourceIndex = 6,
            name = "Asianet Movies HD",
            poster = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_MOVIES_HD/images/LOGO_HD/image.png"
        ),
        IndexedTarget(
            pageId = "mollywood-tv",
            sourceIndex = 8,
            name = "Asianet Plus",
            poster = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_ASIANET_PLUS/images/LOGO_HD/image.png"
        ),
        IndexedTarget(
            pageId = "mollywood-tv",
            sourceIndex = 10,
            name = "Zee Keralam HD",
            poster = "https://akamaividz2.zee5.com/image/upload/resources/0-9-129/channel_list/1170x658withlogoea00fd123614470c9f82e2fde66280e4.png"
        ),
        IndexedTarget(
            pageId = "mollywood-plus",
            sourceIndex = 1,
            name = "Surya TV FHD",
            poster = "https://sund-images.sunnxt.com/194397/1000x1000_SuryaTVHD_194397_4c99c17b-92d4-49be-a490-b5958067190a.png"
        ),
        IndexedTarget(
            pageId = "mollywood-plus",
            sourceIndex = 4,
            name = "Surya Movies",
            poster = "https://sund-images.sunnxt.com/9019/1000x1000_71ddcc0b-16e7-48e9-9998-aa023200f4bc.jpg"
        ),
        IndexedTarget(
            pageId = "mollywood-plus",
            sourceIndex = 5,
            name = "Surya Comedy",
            poster = "https://sund-images.sunnxt.com/30835/1000x1000_143a4af4-2f02-4c9c-814b-af149e6a5a95.jpg"
        )
    )

    private val pageUrls = mapOf(
        "mollywood-tv" to "$mainUrl/live/mollywood-tv/",
        "mollywood-plus" to "$mainUrl/live/mollywood-plus/"
    )

    private val sourceNamesByPage = mapOf(
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

    private val excludedContainerSlugs = setOf(
        "mollywood-tv",
        "mollywood-plus",
        "mollywood-max"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageNumber = page.coerceAtLeast(1)
        val url = if (pageNumber == 1) {
            channelPage
        } else {
            "$mainUrl/channel/malayalam/page/$pageNumber/"
        }

        val doc = app.get(url, referer = mainUrl).document
        val normalChannels = parseChannelPage(doc).toMutableList()

        if (pageNumber == 1) {
            normalChannels += indexedTargets.map { target ->
                newLiveSearchResponse(target.name, indexedUrl(target)) {
                    posterUrl = target.poster
                }
            }
        }

        val maxPage = doc.select("a[href*='/channel/malayalam/page/']")
            .mapNotNull { link ->
                Regex("""/channel/malayalam/page/(\d+)/?""")
                    .find(link.attr("href"))
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
            }
            .maxOrNull()

        val hasNext = maxPage?.let { pageNumber < it } ?: (normalChannels.isNotEmpty() && pageNumber < 4)

        return newHomePageResponse(
            listOf(
                HomePageList(
                    "Malayalam Live TV",
                    normalChannels.distinctBy { it.url },
                    isHorizontalImages = false
                )
            ),
            hasNext = hasNext
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim()
        val pages = (1..4).map { page ->
            val url = if (page == 1) {
                channelPage
            } else {
                "$mainUrl/channel/malayalam/page/$page/"
            }
            app.get(url, referer = mainUrl).document
        }

        val normal = pages
            .flatMap(::parseChannelPage)
            .filter {
                q.isBlank() || it.name.contains(q, ignoreCase = true)
            }

        val indexed = indexedTargets
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .map { target ->
                newLiveSearchResponse(
                    title = target.name,
                    url = indexedUrl(target),
                    apiName = BhoomTVProvider::class.java
                ) {
                    posterUrl = target.poster
                }
            }

        return (normal + indexed).distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val special = parseIndexedUrl(url)

        if (special != null) {
            val target = indexedTargets.firstOrNull {
                it.pageId == special.first && it.sourceIndex == special.second
            }

            return newLiveStreamLoadResponse(
                name = target?.name ?: "BHOOM TV",
                url = url,
                dataUrl = url
            ) {
                posterUrl = target?.poster
            }
        }

        val response = app.get(url, referer = mainUrl)
        val doc = response.document
        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("meta[property='og:title']")?.attr("content")?.trim()
            ?: "BHOOM TV Channel"

        val poster = doc.selectFirst("meta[property='og:image']")
            ?.attr("content")
            ?.trim()
            ?.ifBlank { null }

        return newLiveStreamLoadResponse(
            name = title,
            url = url,
            dataUrl = url
        ) {
            posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val special = parseIndexedUrl(data)

        return if (special != null) {
            val target = indexedTargets.firstOrNull {
                it.pageId == special.first && it.sourceIndex == special.second
            } ?: return false

            resolveIndexedBhoomSource(
                target = target,
                pageUrl = pageUrls.getValue(target.pageId),
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        } else {
            resolveRegularBhoomPage(
                pageUrl = data,
                subtitleCallback = subtitleCallback,
                callback = callback
            )
        }
    }

    private suspend fun resolveIndexedBhoomSource(
        target: IndexedTarget,
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(
            pageUrl,
            referer = mainUrl,
            headers = mapOf(
                "Accept" to "text/html,application/xhtml+xml"
            )
        )

        val doc = response.document
        val html = response.text
        val labels = sourceLabelElements(doc, target.pageId)
        val selected = labels.getOrNull(target.sourceIndex - 1)

        val candidates = linkedSetOf<String>()

        if (selected != null) {
            candidates += collectCandidatesFromNode(selected, pageUrl)
            candidates += extractUrlsFromText(
                selected.parent()?.outerHtml() ?: selected.outerHtml(),
                pageUrl
            )

            var parent = selected.parent()
            repeat(6) {
                if (parent == null) return@repeat
                candidates += collectCandidatesFromNode(parent, pageUrl)
                parent = parent.parent()
            }
        }

        /*
         * Bounded fallback: if the selected source card does not directly
         * expose its player URL, use the page's own media candidates with the
         * same 1-based ordering as the BHOOM source list.
         */
        if (candidates.none(::isMediaUrl)) {
            val globalMedia = extractMediaUrls(html, pageUrl)
            globalMedia.getOrNull(target.sourceIndex - 1)?.let(candidates::add)
        }

        var links = 0

        for (candidate in candidates.take(10)) {
            if (tryResolveCandidate(candidate, pageUrl, subtitleCallback, callback)) {
                links++
            }
        }

        return links > 0
    }

    private suspend fun resolveRegularBhoomPage(
        pageUrl: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val response = app.get(pageUrl, referer = mainUrl)
        val doc = response.document
        val html = response.text

        val candidates = linkedSetOf<String>()
        candidates += extractMediaUrls(html, pageUrl)

        doc.select(
            "iframe[src], iframe[data-src], video[src], video source[src], source[src], " +
                "[data-src], [data-url], [data-stream], [data-playlist], [data-file], [data-player]"
        ).forEach { element ->
            candidates += collectCandidatesFromNode(element, pageUrl)
        }

        candidates += extractScriptUrlCandidates(html, pageUrl)

        var links = 0
        for (candidate in candidates.take(20)) {
            if (tryResolveCandidate(candidate, pageUrl, subtitleCallback, callback)) {
                links++
            }
        }

        return links > 0
    }

    private suspend fun tryResolveCandidate(
        candidate: String,
        referer: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url = normalizeUrl(candidate, referer) ?: return false

        if (isMediaUrl(url)) {
            val type = if (url.contains(".mpd", ignoreCase = true)) {
                ExtractorLinkType.DASH
            } else {
                ExtractorLinkType.M3U8
            }

            callback(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = url,
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
            return true
        }

        if (!isLikelyPlayerOrPage(url)) return false

        try {
            val response = app.get(url, referer = referer)
            val html = response.text
            val doc = response.document

            val media = linkedSetOf<String>()
            media += extractMediaUrls(html, url)

            doc.select(
                "video[src], video source[src], source[src], iframe[src], " +
                    "[data-src], [data-url], [data-stream], [data-playlist], [data-file]"
            ).forEach { element ->
                media += collectCandidatesFromNode(element, url)
            }

            var directCount = 0
            for (mediaUrl in media.take(10)) {
                if (!isMediaUrl(mediaUrl)) continue

                val type = if (mediaUrl.contains(".mpd", ignoreCase = true)) {
                    ExtractorLinkType.DASH
                } else {
                    ExtractorLinkType.M3U8
                }

                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = mediaUrl,
                        type = type
                    ) {
                        this.referer = url
                        headers = mapOf(
                            "User-Agent" to USER_AGENT,
                            "Accept" to "*/*"
                        )
                        quality = Qualities.Unknown.value
                    }
                )

                directCount++
            }

            if (directCount > 0) return true
        } catch (_: Exception) {
            // Let the extractor registry try the source as a final step.
        }

        return try {
            var extracted = 0
            loadExtractor(
                url = url,
                referer = referer,
                subtitleCallback = subtitleCallback,
                callback = {
                    extracted++
                    callback(it)
                }
            )
            extracted > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun parseChannelPage(doc: Document): List<SearchResponse> {
        return doc.select("a[href*='/live/']")
            .mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (!href.contains("/live/")) return@mapNotNull null

                val slug = href.substringAfter("/live/")
                    .substringBefore("/")
                    .lowercase()

                if (slug in excludedContainerSlugs) return@mapNotNull null

                val title = anchor.selectFirst("h2, h3, .title, .entry-title")?.text()?.trim()
                    ?: anchor.text().trim()

                if (title.isBlank()) return@mapNotNull null

                val poster = findPoster(anchor)

                newLiveSearchResponse(title, href) {
                    posterUrl = poster
                }
            }
            .distinctBy { it.url }
    }

    private fun sourceLabelElements(doc: Document, pageId: String): List<Element> {
        val allowed = sourceNamesByPage.getValue(pageId)
            .map(::normalizeText)
            .toSet()

        return doc.getAllElements()
            .filter { normalizeText(it.ownText()) in allowed }
    }

    private fun collectCandidatesFromNode(node: Element, baseUrl: String): Set<String> {
        val candidates = linkedSetOf<String>()

        val attributes = listOf(
            "href",
            "src",
            "data-src",
            "data-url",
            "data-href",
            "data-stream",
            "data-playlist",
            "data-file",
            "data-player",
            "data-video",
            "data-embed",
            "poster"
        )

        attributes.forEach { attr ->
            val value = node.attr(attr)
            if (value.isNotBlank()) {
                candidates += extractUrlsFromText(value, baseUrl)
            }
        }

        val onclick = node.attr("onclick")
        if (onclick.isNotBlank()) {
            candidates += extractUrlsFromText(onclick, baseUrl)
        }

        val inlineStyle = node.attr("style")
        if (inlineStyle.isNotBlank()) {
            candidates += extractUrlsFromText(inlineStyle, baseUrl)
        }

        candidates += extractUrlsFromText(node.outerHtml(), baseUrl)

        return candidates
    }

    private fun extractScriptUrlCandidates(html: String, baseUrl: String): Set<String> {
        val candidates = linkedSetOf<String>()

        val patterns = listOf(
            Regex("""(?i)["'](?:file|src|source|stream|url|playlist|hls|dash)["']\s*[:=]\s*["']([^"']+)["']"""),
            Regex("""(?i)["'](?:player|embed|iframe|video)["']\s*[:=]\s*["']([^"']+)["']""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                candidates += extractUrlsFromText(
                    match.groupValues.getOrNull(1).orEmpty(),
                    baseUrl
                )
            }
        }

        return candidates
    }

    private fun extractMediaUrls(text: String, baseUrl: String): Set<String> {
        val candidates = linkedSetOf<String>()

        val pattern = Regex(
            """(?i)(?:https?:)?//[^"'\\s<>\\\\]+\.(?:m3u8|mpd)(?:\?[^"'\\s<>\\\\]*)?"""
        )

        pattern.findAll(text).forEach {
            val normalized = normalizeUrl(it.value, baseUrl)
            if (normalized != null) candidates += normalized
        }

        return candidates
    }

    private fun extractUrlsFromText(text: String, baseUrl: String): Set<String> {
        val candidates = linkedSetOf<String>()

        extractMediaUrls(text, baseUrl).forEach { candidates += it }

        val absolute = Regex("""(?i)https?://[^"'\\s<>]+""")
        absolute.findAll(text).forEach {
            val normalized = normalizeUrl(it.value, baseUrl)
            if (normalized != null && isLikelyPlayerOrPage(normalized)) {
                candidates += normalized
            }
        }

        val quotedRelative = Regex("""["'](\\/[^"']+)["']""")
        quotedRelative.findAll(text).forEach {
            val normalized = normalizeUrl(it.groupValues[1], baseUrl)
            if (normalized != null && isLikelyPlayerOrPage(normalized)) {
                candidates += normalized
            }
        }

        return candidates
    }

    private fun normalizeUrl(raw: String?, baseUrl: String): String? {
        if (raw.isNullOrBlank()) return null

        val value = raw.trim()
            .replace("\\/", "/")
            .replace("&amp;", "&")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .trim('\'', '"')

        if (value.startsWith("javascript:", ignoreCase = true)) return null
        if (value.startsWith("data:", ignoreCase = true)) return null
        if (value.startsWith("#")) return null

        return try {
            when {
                value.startsWith("//") -> "https:$value"
                value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true) -> value
                else -> URI(baseUrl).resolve(value).toString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isMediaUrl(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) ||
            url.contains(".mpd", ignoreCase = true)

    private fun isLikelyPlayerOrPage(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.length > 4096) return false

        val lower = url.lowercase()
        val blockedExtensions = listOf(
            ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg",
            ".css", ".js", ".woff", ".woff2", ".ttf", ".ico"
        )

        if (blockedExtensions.any { lower.substringBefore('?').endsWith(it) }) {
            return false
        }

        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    private fun findPoster(anchor: Element): String? {
        val direct = anchor.selectFirst("img")?.let {
            it.absUrl("src").ifBlank { it.attr("src") }
        }

        if (!direct.isNullOrBlank()) return direct

        var parent = anchor.parent()
        repeat(5) {
            if (parent == null) return@repeat

            val poster = parent.selectFirst("img")?.let {
                it.absUrl("src").ifBlank { it.attr("src") }
            }

            if (!poster.isNullOrBlank()) return poster
            parent = parent.parent()
        }

        return null
    }

    private fun indexedUrl(target: IndexedTarget): String =
        "bhoom-index://" + target.pageId + "/" + target.sourceIndex

    private fun parseIndexedUrl(url: String): Pair<String, Int>? {
        val prefix = "bhoom-index://"
        if (!url.startsWith(prefix)) return null

        val rest = url.removePrefix(prefix)
        val pageId = rest.substringBefore("/")
        val index = rest.substringAfter("/", "").toIntOrNull() ?: return null

        if (pageId !in pageUrls) return null
        return pageId to index
    }

    private fun normalizeText(text: String): String =
        text.replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
}
