package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xtreamlytv.androidtv.model.Category
import com.xtreamlytv.androidtv.model.ContentType
import com.xtreamlytv.androidtv.model.hiddenCategoryIds
import com.xtreamlytv.androidtv.ui.theme.palette
import kotlinx.coroutines.delay

private data class VisibilitySection(
    val type: ContentType,
    val title: String,
    val categories: List<Category>,
)

@Composable
fun CategoryVisibilityScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
) {
    val sections = listOf(
        VisibilitySection(ContentType.LIVE, "Live TV", state.categories[ContentType.LIVE].visibleProviderCategories()),
        VisibilitySection(ContentType.MOVIE, "Movies", state.categories[ContentType.MOVIE].visibleProviderCategories()),
        VisibilitySection(ContentType.SERIES, "Series", state.categories[ContentType.SERIES].visibleProviderCategories()),
    )
    val firstCategory = sections.firstNotNullOfOrNull { section -> section.categories.firstOrNull()?.let { section.type to it.id } }

    LaunchedEffect(firstCategory) {
        if (firstCategory != null) {
            delay(32L)
            runCatching { contentEntryFocus.requestFocus() }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item("visibility-intro") {
            Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text("Provider category visibility", color = palette().text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Hidden categories are removed from category wheels, Home, and Favorites. This local filter is not PIN-protected.",
                    color = palette().muted,
                    fontSize = 9.sp,
                )
            }
        }

        sections.forEach { section ->
            item("header:${section.type}") {
                Text(
                    section.title,
                    color = palette().accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            if (section.categories.isEmpty()) {
                item("empty:${section.type}") {
                    Text("No provider categories loaded", color = palette().muted, fontSize = 9.sp)
                }
            } else {
                items(section.categories, key = { "${section.type}:${it.id}" }) { category ->
                    val hidden = category.id in state.settings.hiddenCategoryIds(section.type)
                    VisibilityCategoryRow(
                        category = category,
                        hidden = hidden,
                        onClick = { viewModel.setProviderCategoryHidden(section.type, category.id, !hidden) },
                        modifier = if (firstCategory == (section.type to category.id)) Modifier.focusRequester(contentEntryFocus) else Modifier,
                    )
                }
            }
        }
    }
}

private fun List<Category>?.visibleProviderCategories(): List<Category> =
    orEmpty().filter { it.id != "all" && !it.id.startsWith("__") }

@Composable
private fun VisibilityCategoryRow(
    category: Category,
    hidden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSurface(modifier.fillMaxWidth().height(38.dp), onClick = onClick, active = !hidden) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                category.name,
                color = palette().text,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(if (hidden) "Hidden" else "Visible ✓", color = if (hidden) palette().muted else palette().accent, fontSize = 9.sp)
        }
    }
}
