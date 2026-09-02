package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppNavTab {
    HOME,
    LIBRARY,
    PROGRESS,
    SAVED,
    SEARCH
}

/**
 * Tablet / Wide-screen Floating Pill Navigation Rail
 */
@Composable
fun FloatingNavigationRail(
    currentTab: AppNavTab,
    onTabSelected: (AppNavTab) -> Unit,
    onNewReadingClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val pillShape = RoundedCornerShape(32.dp)

    Surface(
        shape = pillShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp)
            .width(84.dp)
            .fillMaxHeight()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                shape = pillShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: + Quick Action & Navigation Items
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Primary Floating Action Button inside the pill
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .bouncyClick(scaleDown = 0.88f) {
                            onNewReadingClick()
                        }
                        .testTag("floating_rail_fab")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Reading",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FloatingRailItem(
                    selected = currentTab == AppNavTab.HOME,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.HOME)
                    },
                    iconSelected = Icons.Filled.Home,
                    iconUnselected = Icons.Outlined.Home,
                    label = "Home",
                    testTag = "tab_home"
                )

                FloatingRailItem(
                    selected = currentTab == AppNavTab.LIBRARY,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.LIBRARY)
                    },
                    iconSelected = Icons.Filled.CollectionsBookmark,
                    iconUnselected = Icons.Outlined.CollectionsBookmark,
                    label = "Library",
                    testTag = "tab_library"
                )

                FloatingRailItem(
                    selected = currentTab == AppNavTab.PROGRESS,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.PROGRESS)
                    },
                    iconSelected = Icons.Filled.Insights,
                    iconUnselected = Icons.Outlined.Insights,
                    label = "Progress",
                    testTag = "tab_progress"
                )

                FloatingRailItem(
                    selected = currentTab == AppNavTab.SAVED,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.SAVED)
                    },
                    iconSelected = Icons.Filled.Bookmark,
                    iconUnselected = Icons.Outlined.BookmarkBorder,
                    label = "Saved",
                    testTag = "tab_saved"
                )

                FloatingRailItem(
                    selected = currentTab == AppNavTab.SEARCH,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.SEARCH)
                    },
                    iconSelected = Icons.Filled.Search,
                    iconUnselected = Icons.Outlined.Search,
                    label = "Search",
                    testTag = "tab_search"
                )
            }

            // Bottom Section: Settings Button
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(42.dp)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSettingsClick()
                    },
                    modifier = Modifier.testTag("floating_rail_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Mobile Bottom Navigation Bar tailored specifically for compact phone screens in a floating pill shape
 */
@Composable
fun MobileBottomNavigationBar(
    currentTab: AppNavTab,
    onTabSelected: (AppNavTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MobilePillNavItem(
                    selected = currentTab == AppNavTab.HOME,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.HOME)
                    },
                    iconSelected = Icons.Filled.Home,
                    iconUnselected = Icons.Outlined.Home,
                    label = "Home",
                    testTag = "mobile_tab_home",
                    modifier = Modifier.weight(1f)
                )

                MobilePillNavItem(
                    selected = currentTab == AppNavTab.LIBRARY,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.LIBRARY)
                    },
                    iconSelected = Icons.Filled.CollectionsBookmark,
                    iconUnselected = Icons.Outlined.CollectionsBookmark,
                    label = "Library",
                    testTag = "mobile_tab_library",
                    modifier = Modifier.weight(1f)
                )

                MobilePillNavItem(
                    selected = currentTab == AppNavTab.PROGRESS,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.PROGRESS)
                    },
                    iconSelected = Icons.Filled.Insights,
                    iconUnselected = Icons.Outlined.Insights,
                    label = "Progress",
                    testTag = "mobile_tab_progress",
                    modifier = Modifier.weight(1f)
                )

                MobilePillNavItem(
                    selected = currentTab == AppNavTab.SAVED,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.SAVED)
                    },
                    iconSelected = Icons.Filled.Bookmark,
                    iconUnselected = Icons.Outlined.BookmarkBorder,
                    label = "Saved",
                    testTag = "mobile_tab_saved",
                    modifier = Modifier.weight(1f)
                )

                MobilePillNavItem(
                    selected = currentTab == AppNavTab.SEARCH,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTabSelected(AppNavTab.SEARCH)
                    },
                    iconSelected = Icons.Filled.Search,
                    iconUnselected = Icons.Outlined.Search,
                    label = "Search",
                    testTag = "mobile_tab_search",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MobilePillNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconSelected: ImageVector,
    iconUnselected: ImageVector,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = spring(stiffness = 400f),
        label = "pill_indicator_color"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = 400f),
        label = "pill_content_color"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(indicatorColor)
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) iconSelected else iconUnselected,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FloatingRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconSelected: ImageVector,
    iconUnselected: ImageVector,
    label: String,
    testTag: String
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = spring(stiffness = 400f),
        label = "indicator_color"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = 400f),
        label = "content_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .bouncyClick(scaleDown = 0.90f) {
                onClick()
            }
            .padding(vertical = 4.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 32.dp)
                .clip(CircleShape)
                .background(indicatorColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (selected) iconSelected else iconUnselected,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}
