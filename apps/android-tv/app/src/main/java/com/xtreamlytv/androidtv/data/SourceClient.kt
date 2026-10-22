package com.xtreamlytv.androidtv.data

import com.xtreamlytv.androidtv.model.CatalogItem
import com.xtreamlytv.androidtv.model.Category
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.EpgProgram
import com.xtreamlytv.androidtv.model.ProviderSummary
import com.xtreamlytv.androidtv.model.StreamFormat

interface SourceClient {
    suspend fun authenticate(): ProviderSummary
    suspend fun categories(type: ContentType): List<Category>
    suspend fun items(type: ContentType, categoryId: String): List<CatalogItem>
    suspend fun movieInfo(movie: CatalogItem): CatalogItem
    suspend fun seriesDetail(series: CatalogItem): Pair<CatalogItem, List<CatalogItem>>
    suspend fun shortEpg(streamId: String, limit: Int = 10): List<EpgProgram>
    fun streamCandidates(item: CatalogItem, preferredFormat: StreamFormat = StreamFormat.AUTO): List<String>
}
