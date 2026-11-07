package com.proxyplatform.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal object KunPalette {
    val Primary = Color(0xFF2864E8)
    val PrimaryDeep = Color(0xFF173B7A)
    val Cyan = Color(0xFF23B9C6)
    val Ink = Color(0xFF15233B)
    val Muted = Color(0xFF68768C)
    val Background = Color(0xFFF4F7FC)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFEEF3FA)
    val Border = Color(0xFFE0E7F0)
    val Success = Color(0xFF16866F)
    val SuccessSoft = Color(0xFFE4F5EF)
    val Warning = Color(0xFF9C6814)
    val WarningSoft = Color(0xFFFFF3DB)
    val Error = Color(0xFFBE3C45)
    val ErrorSoft = Color(0xFFFFE9E8)
}

private val KunLightScheme = lightColorScheme(
    primary = KunPalette.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F0FF),
    onPrimaryContainer = KunPalette.PrimaryDeep,
    secondary = KunPalette.Success,
    onSecondary = Color.White,
    secondaryContainer = KunPalette.SuccessSoft,
    onSecondaryContainer = Color(0xFF125F50),
    tertiary = Color(0xFFB66A12),
    onTertiary = Color.White,
    tertiaryContainer = KunPalette.WarningSoft,
    onTertiaryContainer = Color(0xFF754700),
    background = KunPalette.Background,
    onBackground = KunPalette.Ink,
    surface = KunPalette.Surface,
    onSurface = KunPalette.Ink,
    surfaceVariant = KunPalette.SurfaceSoft,
    onSurfaceVariant = KunPalette.Muted,
    error = KunPalette.Error,
    onError = Color.White,
    errorContainer = KunPalette.ErrorSoft,
    onErrorContainer = Color(0xFF8C2029),
    outline = Color(0xFFD4DDEA),
    outlineVariant = KunPalette.Border,
)

@Composable
internal fun KUNTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KunLightScheme,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(30.dp),
        ),
        content = content,
    )
}

@Composable
internal fun BrandMark(size: Dp = 52.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(Brush.linearGradient(listOf(KunPalette.Primary, KunPalette.Cyan))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "K",
            color = Color.White,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
internal fun PageHeading(
    eyebrow: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun FeaturePill(
    text: String,
    color: Color = KunPalette.Primary,
    container: Color = Color(0xFFE8F0FF),
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun KUNCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

@Composable
internal fun ExpandableDetails(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(label) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.Start)) {
            Text(if (expanded) "إخفاء التفاصيل" else label, fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
internal fun KUNPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(52.dp),
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        shape = RoundedCornerShape(16.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
        else Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun KUNSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(50.dp),
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun StateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    error: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tint = if (error) KunPalette.Error else MaterialTheme.colorScheme.primary
    val soft = if (error) KunPalette.ErrorSoft else MaterialTheme.colorScheme.primaryContainer
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(soft),
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(23.dp), strokeWidth = 2.5.dp)
                } else {
                    Text(if (error) "!" else "K", color = tint, fontWeight = FontWeight.ExtraBold)
                }
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(2.dp))
                KUNPrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                )
            }
        }
    }
}

@Composable
internal fun KeyValueRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}
