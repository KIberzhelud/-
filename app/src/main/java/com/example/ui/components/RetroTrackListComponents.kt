package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CallIncomingGreen
import com.example.ui.theme.LocalVistaTheme
import com.example.ui.theme.MusicSourceAmber
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.symbianGlossyTile
import com.example.ui.theme.symbianMetallicBar
import com.example.ui.theme.vistaAeroButton
import com.example.ui.theme.vistaInsetBox

@Composable
fun RetroListSectionStrip(
    title: String,
    countBadge: String? = null,
    iconRes: Int = R.drawable.ic_symbian_music,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(2.dp))
            .symbianMetallicBar()
            .border(1.dp, theme.chromeBorderOuter, RoundedCornerShape(2.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                ),
                color = theme.focusBorder,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!countBadge.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .vistaInsetBox(shape = RoundedCornerShape(2.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = countBadge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RetroGlossyTrackRow(
    index: Int,
    title: String,
    subtitle: String = "",
    trackNumberOverride: Int? = null,
    badgeText: String? = null,
    badgeLossless: Boolean = false,
    durationText: String? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    statusBarColor: Color? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    leadingSlot: (@Composable () -> Unit)? = null,
    trailingSlot: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val theme = LocalVistaTheme.current
    val displayNumber = trackNumberOverride ?: (index + 1)
    val textShadow = remember(theme.isGlassMode) {
        if (theme.isGlassMode) {
            Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 1.2f),
                blurRadius = 3f
            )
        } else {
            null
        }
    }

    val rowChromeModifier = if (isActive) {
        Modifier.vistaAeroButton(isPrimary = false, isActive = true, shape = RoundedCornerShape(3.dp))
    } else {
        Modifier.symbianGlossyTile(bucket = index, isFocused = false, shape = RoundedCornerShape(3.dp))
    }

    val interactionModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .then(rowChromeModifier)
            .then(interactionModifier)
            .padding(horizontal = 7.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val resolvedLed = statusBarColor ?: when {
            isPlaying -> CallIncomingGreen
            isActive -> theme.accent
            else -> theme.chromeInnerGlow.copy(alpha = 0.35f)
        }
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(resolvedLed)
        )
        Spacer(Modifier.width(6.dp))

        Text(
            text = "%02d.".format(displayNumber.coerceAtLeast(1)),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = if (isActive) MusicSourceAmber else TextMuted,
            modifier = Modifier.width(24.dp)
        )

        if (leadingSlot != null) {
            leadingSlot()
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .vistaInsetBox(isFocused = isActive, shape = RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = CallIncomingGreen,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(R.drawable.ic_symbian_music),
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 12.5.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    shadow = textShadow
                ),
                color = if (isActive) theme.focusBorder else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (isActive) TextPrimary.copy(alpha = 0.88f) else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (!badgeText.isNullOrBlank()) {
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.42f), RoundedCornerShape(2.dp))
                    .border(
                        width = 1.dp,
                        color = if (badgeLossless) CallIncomingGreen.copy(alpha = 0.7f) else theme.chromeBorderOuter,
                        shape = RoundedCornerShape(2.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    ),
                    color = if (badgeLossless) CallIncomingGreen else MusicSourceAmber,
                    maxLines = 1
                )
            }
        }

        if (!durationText.isNullOrBlank()) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                color = if (isActive) MusicSourceAmber else TextMuted,
                maxLines = 1
            )
        }

        if (trailingSlot != null) {
            Spacer(Modifier.width(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                content = trailingSlot
            )
        }
    }
}
