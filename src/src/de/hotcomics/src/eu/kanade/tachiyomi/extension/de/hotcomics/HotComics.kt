package eu.kanade.tachiyomi.extension.de.hotcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
class HotComics : HttpSource() {

    override val name = "HotComics.io"
    override val baseUrl = "https://hotcomics.io"
    override val lang = "de"
    override val supportsLatest = true

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/en/genres/All?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        document.select("a[href]").forEach { element ->
            val href = element.attr("abs:href")
            if (href.matches(Regex(".*/[a-zA-Z0-9]{8}\\.html$")) && href.contains("/en/")) {
                val title = element.select("strong").text().ifEmpty { element.ownText().take(60) }
                if (title.isNotBlank()) {
                    val manga = SManga.create()
                    manga.title = title.trim()
                    manga.url = href.removePrefix(baseUrl)
                    manga.thumbnail_url = element.select("img").first()?.absUrl("src")
                    if (manga.url.isNotBlank()) mangas.add(manga)
                }
            }
        }
        return MangasPage(mangas.distinctBy { it.url }, false)
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/en/weekly?page=$page", headers)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/en/genres/All", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select("h1").text().ifEmpty {
            document.select("title").text().split(",")[0].trim()
        }
        manga.description = document.select("p").text().ifEmpty {
            document.select("meta[name=description]").attr("content")
        }
        manga.thumbnail_url = document.select("img[src*=/upload/thumbnail/]").first()?.absUrl("src")
        val text = document.text()
        manga.status = when {
            text.contains("End", true) || text.contains("Completed", true) -> SManga.COMPLETED
            text.contains("Up", true) || text.contains("Ongoing", true) -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun chapterListParse(response: Response): MutableList<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        document.select("a[href*=/episode-]").forEach { element ->
            val href = element.attr("abs:href")
            if (href.isNotBlank()) {
                val chapter = SChapter.create()
                chapter.url = href.removePrefix(baseUrl)
                chapter.name = element.text().trim().ifEmpty { "Episode ${chapters.size + 1}" }
                val num = Regex("episode-(\\d+)").find(href)
                chapter.chapter_number = num?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
                chapters.add(chapter)
            }
        }
        return chapters.distinctBy { it.url }.sortedByDescending { it.chapter_number }.toMutableList()
    }

    override fun pageListParse(document: Document): MutableList<Page> {
        val pages = mutableListOf<Page>()
        document.select("img[src*=/upload/], img.reader-img, #reader img, .viewer img").forEach { img ->
            val url = img.absUrl("src")
            if (url.isNotBlank() && !url.contains("thumbnail")) {
                pages.add(Page(pages.size, imageUrl = url))
            }
        }
        document.select("img[data-src], img[data-lazy-src]").forEach { img ->
            val url = img.absUrl("data-src").ifEmpty { img.absUrl("data-lazy-src") }
            if (url.isNotBlank()) pages.add(Page(pages.size, imageUrl = url))
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = ""

    override val headers: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Android 14; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0")
        .add("Referer", baseUrl)
        .build()
}
