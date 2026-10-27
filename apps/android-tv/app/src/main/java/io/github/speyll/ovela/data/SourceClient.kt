package io.github.speyll.ovela.data

import io.github.speyll.ovela.model.CatalogItem
import io.github.speyll.ovela.model.Category
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.EpgProgram
import io.github.speyll.ovela.model.ProviderSummary
import io.github.speyll.ovela.model.StreamFormat

interface SourceClient {
    suspend fun authenticate(): ProviderSummary
    suspend fun categories(type: ContentType): List<Category>
    suspend fun items(type: ContentType, categoryId: String): List<CatalogItem>
    suspend fun movieInfo(movie: CatalogItem): CatalogItem
    suspend fun seriesDetail(series: CatalogItem): Pair<CatalogItem, List<CatalogItem>>
    suspend fun shortEpg(streamId: String, limit: Int = 10): List<EpgProgram>
    fun streamCandidates(item: CatalogItem, preferredFormat: StreamFormat = StreamFormat.AUTO): List<String>
}
