package com.xtreamlytv.androidtv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

@Composable
fun CategoryVisibilityScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
) {
    var selectedType by remember { mutableStateOf(ContentType.LIVE) }
    var query by remember { mutableStateOf("") }
    var hiddenOnly by remember { mutableStateOf(false) }
    val categoryLists = remember(state.categories) {
        listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)
            .associateWith { type -> state.categories[type].visibleProviderCategories() }
    }
    val hiddenIds = state.settings.hiddenCategoryIds(selectedType)
    val providerCategories = categoryLists[selectedType].orEmpty()
    val normalizedQuery = query.trim()
    val results = remember(providerCategories, hiddenIds, normalizedQuery, hiddenOnly) {
        providerCategories.filter { category ->
            (!hiddenOnly || category.id in hiddenIds) &&
                (normalizedQuery.isBlank() || category.name.contains(normalizedQuery, ignoreCase = true))
        }
    }

    LaunchedEffect(Unit) {
        delay(32L)
        runCatching { contentEntryFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item("visibility-intro") {
            Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Provider category visibility", color = palette().text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Hidden categories are removed from category wheels, Home, and Favorites. This local filter is not PIN-protected.",
                    color = palette().muted,
                    fontSize = 9.sp,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search ${selectedType.visibilityTitle().lowercase()} categories",
                        modifier = Modifier.weight(1f),
                        focusRequester = contentEntryFocus,
                    )
                    if (query.isNotEmpty()) {
                        TvButton("Clear", { query = "" }, Modifier.width(68.dp), TvButtonStyle.Secondary)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES).forEach { type ->
                        TvChip(
                            label = "${type.visibilityTitle()} (${categoryLists[type].orEmpty().size})",
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                        )
                    }
                    TvChip(
                        label = "Hidden only (${hiddenIds.size})",
                        selected = hiddenOnly,
                        onClick = { hiddenOnly = !hiddenOnly },
                    )
                }
                Text(
                    "${results.size} matching ${if (results.size == 1) "category" else "categories"}",
                    color = palette().muted,
                    fontSize = 8.5.sp,
                )
            }
        }

        if (results.isEmpty()) {
            item("empty:$selectedType:$normalizedQuery:$hiddenOnly") {
                Text(
                    if (providerCategories.isEmpty()) "No provider categories loaded" else "No categories match these filters.",
                    color = palette().muted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(vertical = 18.dp),
                )
            }
        } else {
            items(results, key = { "$selectedType:${it.id}" }) { category ->
                val hidden = category.id in hiddenIds
                VisibilityCategoryRow(
                    category = category,
                    hidden = hidden,
                    onClick = { viewModel.setProviderCategoryHidden(selectedType, category.id, !hidden) },
                )
            }
        }
    }
}

private fun List<Category>?.visibleProviderCategories(): List<Category> =
    orEmpty().filter { it.id != "all" && !it.id.startsWith("__") }

private fun ContentType.visibilityTitle(): String = when (this) {
    ContentType.LIVE -> "Live TV"
    ContentType.MOVIE -> "Movies"
    ContentType.SERIES, ContentType.EPISODE -> "Series"
}

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
