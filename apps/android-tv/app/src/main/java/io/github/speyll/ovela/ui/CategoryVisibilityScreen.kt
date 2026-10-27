package io.github.speyll.ovela.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.speyll.ovela.model.Category
import io.github.speyll.ovela.model.ContentType
import io.github.speyll.ovela.model.hiddenCategoryIds
import io.github.speyll.ovela.ui.theme.LocalTouchUi
import io.github.speyll.ovela.ui.theme.TvPalette
import io.github.speyll.ovela.ui.theme.palette
import kotlinx.coroutines.delay

/**
 * Category visibility manager.
 *
 * Layout contract: the header (title, search, section tabs, facets and actions)
 * is measured first and the category list owns the remaining height with the
 * only vertical scroll container on the screen. Nothing above the list scrolls
 * vertically, nothing can widen the page horizontally, and on short landscape
 * screens the header compresses (search and section tabs share a row, the three
 * facets collapse into one horizontally scrollable strip) so the list still has
 * usable space.
 */
@Composable
fun CategoryVisibilityScreen(
    state: AppUiState,
    viewModel: AppViewModel,
    contentEntryFocus: FocusRequester,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    var selectedType by remember { mutableStateOf(ContentType.LIVE) }
    var query by remember { mutableStateOf("") }
    var hiddenOnly by remember { mutableStateOf(false) }
    var groupKey by remember { mutableStateOf(ALL_CATEGORY_GROUPS) }
    var languageKey by remember { mutableStateOf(ALL_CATEGORY_FACETS) }
    var qualityKey by remember { mutableStateOf(ALL_CATEGORY_FACETS) }
    var stagedByType by remember { mutableStateOf<Map<ContentType, Set<String>>>(emptyMap()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var confirmApply by remember { mutableStateOf(false) }
    var focusAfterAction by remember { mutableStateOf<String?>(null) }
    var lastApplied by remember { mutableStateOf<Pair<ContentType, Set<String>>?>(null) }

    val listState = rememberLazyListState()
    val rowFocusers = remember { mutableMapOf<String, FocusRequester>() }
    val firstRowFocus = remember { FocusRequester() }
    val selectAllFocus = remember { FocusRequester() }
    val actionFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val liveTypeFocus = remember { FocusRequester() }
    val movieTypeFocus = remember { FocusRequester() }
    val seriesTypeFocus = remember { FocusRequester() }
    val typeFocusers = remember {
        mapOf(
            ContentType.LIVE to liveTypeFocus,
            ContentType.MOVIE to movieTypeFocus,
            ContentType.SERIES to seriesTypeFocus,
        )
    }

    val categoryLists = remember(state.categories) {
        listOf(ContentType.LIVE, ContentType.MOVIE, ContentType.SERIES)
            .associateWith { type ->
                state.categories[type].visibleProviderCategories().sortedBy { it.name.lowercase() }
            }
    }
    val providerCategories = categoryLists[selectedType].orEmpty()
    val prefixGroups = remember(providerCategories) { categoryGroups(providerCategories) }
    val languageFacets = remember(providerCategories) {
        categoryFacets(providerCategories, CategoryFacetDimension.LANGUAGE)
    }
    val qualityFacets = remember(providerCategories) {
        categoryFacets(providerCategories, CategoryFacetDimension.QUALITY)
    }
    val groupFacets = remember(prefixGroups) {
        prefixGroups.map { CategoryFacetOption(it.key, it.label, it.categories.size) }
    }
    val hasRealPrefixes = prefixGroups.any { it.key != UNRECOGNIZED_CATEGORY_GROUP }
    val hasRealLanguages = languageFacets.any { it.key != UNCLASSIFIED_CATEGORY_FACET }
    val hasRealQualities = qualityFacets.any { it.key != UNCLASSIFIED_CATEGORY_FACET }
    val activeGroup = prefixGroups.firstOrNull { it.key == groupKey }
    val persistedHidden = state.settings.hiddenCategoryIds(selectedType)
    val stagedHidden = stagedByType[selectedType] ?: persistedHidden
    val normalizedQuery = query.trim()
    val results = remember(
        providerCategories,
        stagedHidden,
        normalizedQuery,
        hiddenOnly,
        groupKey,
        languageKey,
        qualityKey,
    ) {
        providerCategories.filter { category ->
            categoryVisibilityMatches(
                categoryId = category.id,
                name = category.name,
                hidden = category.id in stagedHidden,
                hiddenOnly = hiddenOnly,
                query = normalizedQuery,
                groupKey = groupKey,
                languageKey = languageKey,
                qualityKey = qualityKey,
            )
        }
    }
    val changeCount = changedCategoryCount(persistedHidden, stagedHidden)
    val changeSummary = categoryChangeSummary(persistedHidden, stagedHidden)
    val groupIds = activeGroup?.ids.orEmpty()
    val groupHasVisible = groupIds.any { it !in stagedHidden }
    val groupHasHidden = groupIds.any { it in stagedHidden }
    val undoAvailable = lastApplied?.first == selectedType && changeCount == 0

    LaunchedEffect(Unit) {
        delay(32L)
        runCatching { contentEntryFocus.requestFocus() }
    }
    LaunchedEffect(statusMessage) {
        if (statusMessage != null) {
            delay(2600L)
            statusMessage = null
        }
    }
    LaunchedEffect(prefixGroups) {
        if (groupKey != ALL_CATEGORY_GROUPS && prefixGroups.none { it.key == groupKey }) {
            groupKey = ALL_CATEGORY_GROUPS
        }
    }
    LaunchedEffect(languageFacets) {
        if (languageKey != ALL_CATEGORY_FACETS && languageFacets.none { it.key == languageKey }) {
            languageKey = ALL_CATEGORY_FACETS
        }
    }
    LaunchedEffect(qualityFacets) {
        if (qualityKey != ALL_CATEGORY_FACETS && qualityFacets.none { it.key == qualityKey }) {
            qualityKey = ALL_CATEGORY_FACETS
        }
    }
    LaunchedEffect(focusAfterAction, results) {
        val target = focusAfterAction ?: return@LaunchedEffect
        val index = results.indexOfFirst { it.id == target }
        if (index >= 0) {
            // A remote must always land on the row it just changed; a finger is
            // already looking at the row it tapped, so touch never jumps the list.
            if (!touchUi) {
                listState.ensureFocusedItemVisible(index)
                delay(24L)
                val requester = if (index == 0) firstRowFocus else rowFocusers["$selectedType:$target"]
                if (requester != null) runCatching { requester.requestFocus() }
            }
        }
        focusAfterAction = null
    }

    LaunchedEffect(confirmApply) {
        if (confirmApply) {
            delay(24L)
            runCatching { confirmFocus.requestFocus() }
        }
    }

    // Back dismisses the apply confirmation instead of discarding every staged
    // change and leaving the manager.
    BackHandler(enabled = confirmApply) { confirmApply = false }

    fun stage(next: Set<String>) {
        stagedByType = stagedByType + (selectedType to next)
        confirmApply = false
        statusMessage = null
        // A new edit replaces the snapshot: undoing it would discard that edit.
        lastApplied = null
    }

    fun applyChanges() {
        if (changeCount == 0) return
        if (changeCount > 8 && !confirmApply) {
            confirmApply = true
            return
        }
        val plan = categoryApplyPlan(persistedHidden, stagedHidden) ?: return
        viewModel.setProviderCategoriesHidden(selectedType, plan.hidden)
        stagedByType = stagedByType - selectedType
        lastApplied = selectedType to plan.undo
        confirmApply = false
        statusMessage = "Category visibility saved."
        focusAfterAction = results.firstOrNull()?.id
    }

    fun undoLastApply() {
        val snapshot = lastApplied ?: return
        viewModel.setProviderCategoriesHidden(snapshot.first, snapshot.second)
        stagedByType = stagedByType - snapshot.first
        lastApplied = null
        confirmApply = false
        statusMessage = "Category visibility restored."
        focusAfterAction = results.firstOrNull()?.id
    }

    fun cancelChanges() {
        stagedByType = stagedByType - selectedType
        confirmApply = false
        statusMessage = "Unsaved changes discarded."
        focusAfterAction = results.firstOrNull()?.id
    }

    fun selectType(type: ContentType) {
        selectedType = type
        groupKey = ALL_CATEGORY_GROUPS
        languageKey = ALL_CATEGORY_FACETS
        qualityKey = ALL_CATEGORY_FACETS
        confirmApply = false
        statusMessage = null
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Short landscape screens are the failure mode this layout is built for:
        // below ~480dp of available height the header compresses instead of
        // pushing the category list off-screen.
        val compact = categoryManagerCompact(maxHeight.value)
        val horizontalPadding = if (touchUi) 14.dp else 32.dp
        val gap = if (compact) 5.dp else 8.dp

        Column(
            Modifier
                .fillMaxSize()
                .padding(start = horizontalPadding, end = horizontalPadding, top = if (compact) 2.dp else 6.dp),
        ) {
            val summary = "${results.size} shown · ${stagedHidden.size} hidden · ${providerCategories.size} total"
            if (compact) {
                // Narrow landscape: title and counts share one line, both at a
                // size that never truncates the counts.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Manage categories",
                        color = colors.text,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.3).sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        summary,
                        color = colors.muted,
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                // Tall layouts have the vertical room: title, then a full-width
                // counts line that is never clipped by the title's width.
                Text(
                    "Manage categories",
                    color = colors.text,
                    fontSize = if (touchUi) 22.sp else 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(summary, color = colors.muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(gap))

            if (compact) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = "Search categories",
                        modifier = Modifier.weight(1f).focusProperties { down = typeFocusers.getValue(selectedType) },
                        focusRequester = contentEntryFocus,
                    )
                    Spacer(Modifier.width(2.dp))
                    VisibilityTypeTab(
                        label = "Live TV",
                        selected = selectedType == ContentType.LIVE,
                        count = categoryLists[ContentType.LIVE].orEmpty().size,
                        onClick = { selectType(ContentType.LIVE) },
                        focusRequester = liveTypeFocus,
                        compact = true,
                        modifier = Modifier.weight(.62f),
                    )
                    VisibilityTypeTab(
                        label = "Movies",
                        selected = selectedType == ContentType.MOVIE,
                        count = categoryLists[ContentType.MOVIE].orEmpty().size,
                        onClick = { selectType(ContentType.MOVIE) },
                        focusRequester = movieTypeFocus,
                        compact = true,
                        modifier = Modifier.weight(.62f),
                    )
                    VisibilityTypeTab(
                        label = "Series",
                        selected = selectedType == ContentType.SERIES,
                        count = categoryLists[ContentType.SERIES].orEmpty().size,
                        onClick = { selectType(ContentType.SERIES) },
                        focusRequester = seriesTypeFocus,
                        compact = true,
                        modifier = Modifier.weight(.62f),
                    )
                }
            } else {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search categories",
                    modifier = Modifier.fillMaxWidth().focusProperties { down = typeFocusers.getValue(selectedType) },
                    focusRequester = contentEntryFocus,
                )
                Spacer(Modifier.height(gap))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    VisibilityTypeTab(
                        label = "Live TV",
                        selected = selectedType == ContentType.LIVE,
                        count = categoryLists[ContentType.LIVE].orEmpty().size,
                        onClick = { selectType(ContentType.LIVE) },
                        focusRequester = liveTypeFocus,
                        modifier = Modifier.weight(1f),
                    )
                    VisibilityTypeTab(
                        label = "Movies",
                        selected = selectedType == ContentType.MOVIE,
                        count = categoryLists[ContentType.MOVIE].orEmpty().size,
                        onClick = { selectType(ContentType.MOVIE) },
                        focusRequester = movieTypeFocus,
                        modifier = Modifier.weight(1f),
                    )
                    VisibilityTypeTab(
                        label = "Series",
                        selected = selectedType == ContentType.SERIES,
                        count = categoryLists[ContentType.SERIES].orEmpty().size,
                        onClick = { selectType(ContentType.SERIES) },
                        focusRequester = seriesTypeFocus,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(gap))

            val groupSection = if (hasRealPrefixes) {
                FacetSection(
                    label = "Group",
                    allLabel = "All ${providerCategories.size}",
                    options = groupFacets,
                    selectedKey = groupKey,
                    onSelect = {
                        groupKey = it
                        confirmApply = false
                    },
                )
            } else null
            val languageSection = if (hasRealLanguages) {
                FacetSection(
                    label = "Language",
                    allLabel = "Any",
                    options = languageFacets,
                    selectedKey = languageKey,
                    onSelect = {
                        languageKey = it
                        confirmApply = false
                    },
                )
            } else null
            val qualitySection = if (hasRealQualities) {
                FacetSection(
                    label = "Quality",
                    allLabel = "Any",
                    options = qualityFacets,
                    selectedKey = qualityKey,
                    onSelect = {
                        qualityKey = it
                        confirmApply = false
                    },
                )
            } else null
            val sections = listOfNotNull(groupSection, languageSection, qualitySection)

            if (sections.isNotEmpty()) {
                if (compact) {
                    ManagerFacetStrip(sections)
                } else {
                    sections.forEachIndexed { index, section ->
                        ManagerFacetRow(section)
                        if (index != sections.lastIndex) Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.height(gap))
            }

            ManagerActionBlock(
                compact = compact,
                resultsCount = results.size,
                totalCount = providerCategories.size,
                hiddenOnly = hiddenOnly,
                changeCount = changeCount,
                changeSummary = changeSummary,
                confirmApply = confirmApply,
                undoAvailable = undoAvailable,
                activeGroupLabel = activeGroup?.takeIf { groupKey != ALL_CATEGORY_GROUPS }?.label,
                groupHasVisible = groupHasVisible,
                groupHasHidden = groupHasHidden,
                actionFocus = actionFocus,
                listFocus = firstRowFocus,
                confirmFocus = confirmFocus,
                onHideShown = {
                    stage(setHiddenCategories(stagedHidden, results.map { it.id }, hidden = true))
                    focusAfterAction = results.firstOrNull()?.id
                },
                onShowShown = {
                    stage(setHiddenCategories(stagedHidden, results.map { it.id }, hidden = false))
                    focusAfterAction = results.firstOrNull()?.id
                },
                onToggleHiddenOnly = { hiddenOnly = !hiddenOnly },
                onHideAll = {
                    stage(setHiddenCategories(stagedHidden, providerCategories.map { it.id }, hidden = true))
                    focusAfterAction = results.firstOrNull()?.id
                },
                onShowAll = {
                    stage(setHiddenCategories(stagedHidden, providerCategories.map { it.id }, hidden = false))
                    focusAfterAction = results.firstOrNull()?.id
                },
                onHideGroup = {
                    stage(setHiddenCategories(stagedHidden, groupIds, hidden = true))
                    focusAfterAction = results.firstOrNull()?.id
                },
                onShowGroup = {
                    stage(setHiddenCategories(stagedHidden, groupIds, hidden = false))
                    focusAfterAction = results.firstOrNull()?.id
                },
                onCancel = ::cancelChanges,
                onApply = ::applyChanges,
                onKeepEditing = { confirmApply = false },
                onUndo = ::undoLastApply,
            )

            if (!compact) {
                Spacer(Modifier.height(if (touchUi) 8.dp else 6.dp))
                Text(
                    if (changeCount > 0) {
                        "$changeCount unsaved · hide ${changeSummary.hideCount} · show ${changeSummary.showCount}"
                    } else {
                        "Tap a category to hide or show it."
                    },
                    color = if (changeCount > 0) colors.accent else colors.muted,
                    fontSize = 10.sp,
                    fontWeight = if (changeCount > 0) FontWeight.Medium else FontWeight.Normal,
                )
            }

            statusMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = colors.accent, fontSize = 10.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }

            Spacer(Modifier.height(if (compact) 3.dp else 6.dp))

            if (providerCategories.isEmpty()) {
                ManagerEmptyState("No provider categories loaded for this section.")
            } else if (results.isEmpty()) {
                ManagerEmptyState("No categories match this filter.")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = if (compact) 12.dp else 28.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
                ) {
                    items(results, key = { "$selectedType:${it.id}" }) { category ->
                        val hidden = category.id in stagedHidden
                        val key = "$selectedType:${category.id}"
                        val first = category.id == results.first().id
                        val requester = if (first) {
                            rowFocusers.remove(key)
                            firstRowFocus
                        } else {
                            rowFocusers.getOrPut(key) { FocusRequester() }
                        }
                        VisibilityCategoryRow(
                            category = category,
                            hidden = hidden,
                            compact = compact,
                            onClick = {
                                stage(toggleHiddenCategory(stagedHidden, category.id))
                                focusAfterAction = category.id
                            },
                            modifier = Modifier
                                .focusRequester(requester)
                                .then(if (first) Modifier.focusProperties { up = actionFocus } else Modifier),
                        )
                    }
                }
            }
        }
    }
}

private fun List<Category>?.visibleProviderCategories(): List<Category> =
    orEmpty().filter { it.id != "all" && !it.id.startsWith("__") }

private data class FacetSection(
    val label: String,
    val allLabel: String,
    val options: List<CategoryFacetOption>,
    val selectedKey: String,
    val onSelect: (String) -> Unit,
)

/** Tall layouts: one labelled, horizontally scrollable chip row per facet. */
@Composable
private fun ManagerFacetRow(section: FacetSection) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            section.label,
            color = colors.muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(if (touchUi) 56.dp else 62.dp),
        )
        // weight(1f) keeps the row inside the page width: the chips scroll
        // themselves rather than widening the page.
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(end = 6.dp),
        ) {
            item(key = "all") {
                ManagerChip(
                    label = section.allLabel,
                    selected = section.selectedKey == ALL_CATEGORY_FACETS,
                    onClick = { section.onSelect(ALL_CATEGORY_FACETS) },
                )
            }
            items(section.options, key = { it.key }) { option ->
                ManagerChip(
                    label = "${option.label} ${option.count}",
                    selected = section.selectedKey == option.key,
                    onClick = { section.onSelect(option.key) },
                )
            }
        }
    }
}

/** Short layouts: Group, Language and Quality in one compact scrollable strip. */
@Composable
private fun ManagerFacetStrip(sections: List<FacetSection>) {
    val colors = palette()
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(end = 6.dp),
    ) {
        sections.forEachIndexed { index, section ->
            if (index > 0) {
                item(key = "divider-${section.label}") {
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(Color.White.copy(alpha = .12f)),
                    )
                }
            }
            item(key = "label-${section.label}") {
                Text(
                    section.label.uppercase(),
                    color = colors.muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = .8.sp,
                )
            }
            item(key = "all-${section.label}") {
                ManagerChip(
                    label = section.allLabel,
                    selected = section.selectedKey == ALL_CATEGORY_FACETS,
                    onClick = { section.onSelect(ALL_CATEGORY_FACETS) },
                    compact = true,
                )
            }
            items(section.options, key = { "${section.label}-${it.key}" }) { option ->
                ManagerChip(
                    label = "${option.label} ${option.count}",
                    selected = section.selectedKey == option.key,
                    onClick = { section.onSelect(option.key) },
                    compact = true,
                )
            }
        }
    }
}

/**
 * Every manager action lives in one wrapping block, so nothing can be clipped
 * off the right edge and nothing requires its own vertical scroller. Apply and
 * Cancel lead when there are staged changes; bulk and group actions follow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManagerActionBlock(
    compact: Boolean,
    resultsCount: Int,
    totalCount: Int,
    hiddenOnly: Boolean,
    changeCount: Int,
    changeSummary: CategoryChangeSummary,
    confirmApply: Boolean,
    undoAvailable: Boolean,
    activeGroupLabel: String?,
    groupHasVisible: Boolean,
    groupHasHidden: Boolean,
    actionFocus: FocusRequester,
    listFocus: FocusRequester,
    confirmFocus: FocusRequester,
    onHideShown: () -> Unit,
    onShowShown: () -> Unit,
    onToggleHiddenOnly: () -> Unit,
    onHideAll: () -> Unit,
    onShowAll: () -> Unit,
    onHideGroup: () -> Unit,
    onShowGroup: () -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onKeepEditing: () -> Unit,
    onUndo: () -> Unit,
) {
    val colors = palette()
    val hasResults = resultsCount > 0
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
    ) {
        if (confirmApply) {
            Text(
                "Hide ${changeSummary.hideCount} and show ${changeSummary.showCount} categories? $changeCount total changes.",
                color = colors.text,
                fontSize = if (compact) 10.sp else 11.sp,
                lineHeight = if (compact) 13.sp else 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(if (compact) 220.dp else 300.dp),
            )
            ManagerButton(
                "Apply changes",
                onApply,
                compact,
                primary = true,
                enabled = changeCount > 0,
                focusRequester = confirmFocus,
            )
            ManagerButton("Keep editing", onKeepEditing, compact)
        } else {
            if (changeCount > 0) {
                ManagerButton(
                    label = if (changeCount == 1) "Apply change" else "Apply $changeCount",
                    onClick = onApply,
                    compact = compact,
                    primary = true,
                )
                ManagerButton("Cancel", onCancel, compact)
            }
            ManagerButton(
                label = "Hide shown ($resultsCount)",
                onClick = onHideShown,
                compact = compact,
                enabled = hasResults,
                focusRequester = actionFocus,
                modifier = if (hasResults) Modifier.focusProperties { down = listFocus } else Modifier,
            )
            ManagerButton(
                label = "Show shown ($resultsCount)",
                onClick = onShowShown,
                compact = compact,
                enabled = hasResults,
            )
            ManagerChip(
                label = if (hiddenOnly) "Hidden only" else "All categories",
                selected = hiddenOnly,
                onClick = onToggleHiddenOnly,
                compact = compact,
            )
            ManagerButton("Hide all $totalCount", onHideAll, compact, enabled = totalCount > 0)
            ManagerButton("Show all $totalCount", onShowAll, compact, enabled = totalCount > 0)
        }
        if (activeGroupLabel != null && !confirmApply) {
            ManagerButton("Hide $activeGroupLabel", onHideGroup, compact, enabled = groupHasVisible)
            ManagerButton("Show $activeGroupLabel", onShowGroup, compact, enabled = groupHasHidden)
        }
        if (undoAvailable && !confirmApply) {
            ManagerButton("Undo", onUndo, compact)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ManagerEmptyState(message: String) {
    val colors = palette()
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Text(message, color = colors.muted, fontSize = 12.sp)
    }
}

@Composable
private fun ManagerChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    TvSurface(
        modifier = Modifier.height(
            when {
                compact && touchUi -> 30.dp
                compact -> 26.dp
                touchUi -> 32.dp
                else -> 28.dp
            },
        ),
        onClick = onClick,
        active = selected,
        radius = 13.dp,
    ) {
        // No fillMaxSize: a chip must measure to its label so a wrapping action
        // row keeps several chips on one line instead of one full-width bar each.
        Box(
            Modifier.fillMaxHeight().padding(horizontal = if (compact) 9.dp else 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (selected) colors.accent else colors.text.copy(alpha = .86f),
                fontSize = if (compact) 9.5.sp else 10.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ManagerButton(
    label: String,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    TvSurface(
        modifier = modifier.height(
            when {
                compact && touchUi -> 34.dp
                compact -> 26.dp
                touchUi -> 38.dp
                else -> 30.dp
            },
        ),
        onClick = onClick,
        enabled = enabled,
        background = Color.Transparent,
        radius = 9.dp,
        focusRequester = focusRequester,
    ) {
        Row(
            Modifier
                .fillMaxHeight()
                .background(if (primary) colors.accent else colors.panelStrong.copy(alpha = .9f))
                .padding(horizontal = if (compact) 11.dp else 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                color = (if (primary) colors.accentInk else colors.text).copy(alpha = if (enabled) 1f else .5f),
                fontSize = if (compact) 9.5.sp else 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun VisibilityTypeTab(
    label: String,
    selected: Boolean,
    count: Int,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    TvSurface(
        modifier = modifier.height(if (compact) 34.dp else if (touchUi) 42.dp else 38.dp),
        onClick = onClick,
        active = selected,
        focusRequester = focusRequester,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 6.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                color = if (selected) colors.accent else colors.text,
                fontSize = if (compact) 10.5.sp else if (touchUi) 13.sp else 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
            Spacer(Modifier.width(4.dp))
            Text(count.toString(), color = colors.muted, fontSize = if (compact) 9.sp else 10.sp)
        }
    }
}

@Composable
private fun VisibilityCategoryRow(
    category: Category,
    hidden: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = palette()
    val touchUi = LocalTouchUi.current
    val classification = remember(category.name) { classifyCategory(category.name) }
    // The group already reads from the provider prefix in the name and from the
    // Group facet, so the row only adds the language/quality summary.
    val detail = classification.summary
    TvSurface(
        modifier.fillMaxWidth().height(
            if (compact) 42.dp else if (touchUi) 56.dp else 52.dp,
        ),
        onClick = onClick,
        active = hidden,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (compact) 9.dp else 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(if (compact) 16.dp else if (touchUi) 20.dp else 18.dp)
                    .background(if (hidden) colors.accent else Color.Transparent, RoundedCornerShape(4.dp))
                    .border(1.dp, if (hidden) colors.accent else colors.muted.copy(alpha = .6f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (hidden) Text("✓", color = colors.accentInk, fontSize = if (compact) 9.sp else 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(if (compact) 8.dp else 10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    category.name,
                    color = if (hidden) colors.text else colors.text.copy(alpha = .86f),
                    fontSize = if (compact) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Text(
                        detail,
                        color = colors.muted,
                        fontSize = if (touchUi) 9.5.sp else 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (hidden) "Hidden" else "Visible",
                color = if (hidden) colors.accent else colors.muted,
                fontSize = if (compact) 9.5.sp else 11.sp,
                fontWeight = if (hidden) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
