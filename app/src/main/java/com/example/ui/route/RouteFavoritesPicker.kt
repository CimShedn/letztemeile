package com.example.ui.route

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.local.FavoritePlaceEntity
import com.example.model.SearchLocation
import com.example.viewmodel.RouteSearchField

/**
 * Expandable Favorites Picker for the Route Planner:
 * - Pick any saved favorite directly as Start or Ziel.
 * - Reorder favorites freely via vertical Drag & Drop on the right-side drag handle
 *   (immediately persisted via [onReorderFavorites]).
 * - Delete individual favorites with one tap via the trash icon.
 */
@Composable
fun RouteFavoritesAccordionPanel(
    favorites: List<FavoritePlaceEntity>,
    expanded: Boolean,
    targetField: RouteSearchField,
    onToggleExpanded: () -> Unit,
    onTargetFieldChanged: (RouteSearchField) -> Unit,
    onSelectFavorite: (RouteSearchField, SearchLocation) -> Unit,
    onReorderFavorites: (List<String>) -> Unit,
    onDeleteFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "route_favorites_chevron"
    )

    var localFavorites by remember { mutableStateOf(favorites) }
    var draggedItemId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    val defaultItemHeightPx = with(LocalDensity.current) { 64.dp.toPx() }

    // Keep local drag buffer synchronized whenever no drag gesture is active
    LaunchedEffect(favorites, draggedItemId) {
        if (draggedItemId == null) {
            localFavorites = favorites
        }
    }

    val latestFavorites by rememberUpdatedState(localFavorites)
    val latestOnReorder by rememberUpdatedState(onReorderFavorites)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
            .testTag("route_favorites_panel")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Collapsible Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("route_favorites_header")
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${stringResource(R.string.route_favorites_title)} (${favorites.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (expanded) {
                    Text(
                        text = if (targetField == RouteSearchField.START) {
                            stringResource(R.string.route_favorites_for_start)
                        } else {
                            stringResource(R.string.route_favorites_for_dest)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.settings_collapse_cd else R.string.settings_expand_cd
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    )

                    // Target Selector Pills: "Für Start" / "Für Ziel"
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FavoriteTargetChip(
                            label = stringResource(R.string.route_favorites_for_start),
                            selected = targetField == RouteSearchField.START,
                            onClick = { onTargetFieldChanged(RouteSearchField.START) },
                            testTag = "route_favorites_target_start",
                            modifier = Modifier.weight(1f)
                        )
                        FavoriteTargetChip(
                            label = stringResource(R.string.route_favorites_for_dest),
                            selected = targetField == RouteSearchField.DESTINATION,
                            onClick = { onTargetFieldChanged(RouteSearchField.DESTINATION) },
                            testTag = "route_favorites_target_dest",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (localFavorites.isEmpty()) {
                        Text(
                            text = stringResource(R.string.scooter_favorites_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.route_favorites_drag_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )

                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            localFavorites.forEach { fav ->
                                val isDragged = draggedItemId == fav.id
                                Surface(
                                    color = if (isDragged) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(
                                        width = if (isDragged) 1.5.dp else 0.75.dp,
                                        color = if (isDragged) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    shadowElevation = if (isDragged) 8.dp else 0.dp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .zIndex(if (isDragged) 1f else 0f)
                                        .onSizeChanged { size ->
                                            if (size.height > 0) {
                                                itemHeights[fav.id] = size.height
                                            }
                                        }
                                        .graphicsLayer {
                                            if (isDragged) {
                                                translationY = dragOffsetY
                                                scaleX = 1.02f
                                                scaleY = 1.02f
                                            }
                                        }
                                        .testTag("route_favorite_row_${fav.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .clickable(enabled = draggedItemId == null) {
                                                onSelectFavorite(targetField, fav.toSearchLocation())
                                            }
                                            .padding(start = 12.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = fav.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (fav.subtitle.isNotBlank()) {
                                                Text(
                                                    text = fav.subtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = { onDeleteFavorite(fav.id) },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .testTag("route_favorite_delete_${fav.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = stringResource(R.string.route_favorites_delete_cd),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        // Dedicated Drag Handle: consumes vertical drag events cleanly inside LazyColumn
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .pointerInput(fav.id) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggedItemId = fav.id
                                                            dragOffsetY = 0f
                                                        },
                                                        onDragEnd = {
                                                            val orderedIds = latestFavorites.map { it.id }
                                                            draggedItemId = null
                                                            dragOffsetY = 0f
                                                            latestOnReorder(orderedIds)
                                                        },
                                                        onDragCancel = {
                                                            val orderedIds = latestFavorites.map { it.id }
                                                            draggedItemId = null
                                                            dragOffsetY = 0f
                                                            latestOnReorder(orderedIds)
                                                        },
                                                        onDrag = { change, dragAmount ->
                                                            change.consume()
                                                            dragOffsetY += dragAmount.y
                                                            val mutable = latestFavorites.toMutableList()
                                                            var idx = mutable.indexOfFirst { it.id == fav.id }
                                                            if (idx == -1) return@detectDragGestures
                                                            var swapped = false

                                                            while (dragOffsetY > 0f && idx < mutable.lastIndex) {
                                                                val nextId = mutable[idx + 1].id
                                                                val stepH = (itemHeights[nextId]?.toFloat() ?: defaultItemHeightPx) + 12f
                                                                if (dragOffsetY >= stepH * 0.5f) {
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx + 1, item)
                                                                    dragOffsetY -= stepH
                                                                    idx++
                                                                    swapped = true
                                                                } else {
                                                                    break
                                                                }
                                                            }
                                                            while (dragOffsetY < 0f && idx > 0) {
                                                                val prevId = mutable[idx - 1].id
                                                                val stepH = (itemHeights[prevId]?.toFloat() ?: defaultItemHeightPx) + 12f
                                                                if (dragOffsetY <= -stepH * 0.5f) {
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx - 1, item)
                                                                    dragOffsetY += stepH
                                                                    idx--
                                                                    swapped = true
                                                                } else {
                                                                    break
                                                                }
                                                            }

                                                            if (swapped) {
                                                                localFavorites = mutable
                                                            }
                                                        }
                                                    )
                                                }
                                                .testTag("route_favorite_drag_${fav.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DragHandle,
                                                contentDescription = stringResource(R.string.route_favorites_drag_cd),
                                                tint = if (isDragged) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteTargetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 0.75.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = modifier
            .heightIn(min = 38.dp)
            .testTag(testTag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
