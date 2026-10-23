package com.hguide.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hguide.R
import com.hguide.core.PersonRole
import com.hguide.data.Person
import kotlin.math.abs
import kotlin.math.sin

val PanelShape = RoundedCornerShape(24.dp)

/** Slate-blue gradient panel with a hairline cobalt border. */
fun Modifier.panel(shape: androidx.compose.ui.graphics.Shape = PanelShape, border: Color = Hg.Line.copy(alpha = 0.75f)) =
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(Hg.Panel, Hg.PanelDeep)))
        .border(1.dp, border, shape)

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 32.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.panel().padding(padding), content = content)

@Composable
fun HgIcon(@DrawableRes res: Int, tint: Color = Hg.Muted, size: Dp = 28.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))

/** The one primary action per screen. */
@Composable
fun AmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 84.dp,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(height).clip(shape)
            .background(if (enabled) Brush.verticalGradient(listOf(Hg.Amber, Hg.AmberDeep)) else Brush.verticalGradient(listOf(Hg.LineSoft, Hg.LineSoft)))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 36.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(text, color = if (enabled) Hg.OnAmber else Hg.Dim, style = MaterialTheme.typography.titleLarge)
    }
}

/** Quiet outlined pill for secondary actions. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color = Hg.Text,
    enabled: Boolean = true,
    height: Dp = 60.dp,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.height(height).clip(shape).border(1.dp, Hg.Line, shape).background(Hg.PanelDeep.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 26.dp).alpha(if (enabled) 1f else 0.4f),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { HgIcon(it, tint = color, size = 24.dp) }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Small outlined tag ("직접 녹음", "AI 목소리"). */
@Composable
fun Tag(text: String, color: Color = Hg.Cyan, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium,
        modifier = modifier.clip(shape).border(1.dp, color.copy(alpha = 0.8f), shape).padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
fun CircleButton(@DrawableRes icon: Int, onClick: () -> Unit, size: Dp = 64.dp, filled: Boolean = false, enabled: Boolean = true) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (filled) Modifier.background(Brush.verticalGradient(listOf(Hg.Amber, Hg.AmberDeep))) else Modifier.border(2.dp, Hg.Muted, CircleShape))
            .clickable(enabled = enabled, onClick = onClick).alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) { HgIcon(icon, tint = if (filled) Hg.OnAmber else Hg.Text, size = size * 0.45f) }
}

/** Back chevron + screen title, optional trailing action. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(56.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { HgIcon(R.drawable.ic_back, Hg.Text, 36.dp) }
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Hg.Text, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Steering-wheel outline (the "start driving" glyph; deliberately not a play triangle). */
@Composable
fun SteeringWheel(color: Color = Hg.OnAmber, size: Dp = 40.dp) = Canvas(Modifier.size(size)) {
    val s = this.size.minDimension
    val stroke = s * 0.11f
    val c = center
    drawCircle(color, radius = s / 2 - stroke / 2, style = Stroke(stroke))
    drawCircle(color, radius = s * 0.12f)
    drawLine(color, Offset(c.x - s * 0.40f, c.y - s * 0.04f), Offset(c.x + s * 0.40f, c.y - s * 0.04f), stroke)
    drawLine(color, c, Offset(c.x, c.y + s * 0.42f), stroke)
}

/** Voice waveform bars. [progress] 0..1 colours the played part amber; null = all amber. */
@Composable
fun Waveform(modifier: Modifier, bars: Int = 48, progress: Float? = null, seed: Int = 7, played: Color = Hg.Amber, rest: Color = Hg.Line) =
    Canvas(modifier) {
        val gap = this.size.width / bars
        val w = gap * 0.45f
        for (i in 0 until bars) {
            val v = 0.18f + 0.82f * abs(sin(i * 0.55f + seed) * sin(i * 0.17f + seed * 0.3f))
            val h = this.size.height * v
            val col = if (progress == null || i.toFloat() / bars <= progress) played else rest
            drawRoundRect(col, Offset(i * gap + (gap - w) / 2, (this.size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
        }
    }

/** Amber ring with a tiny waveform: marks the parent's voice. */
@Composable
fun VoiceMark(size: Dp = 64.dp) = Box(
    Modifier.size(size).clip(CircleShape).background(Hg.PanelDeep).border(2.dp, Hg.Amber.copy(alpha = 0.85f), CircleShape),
    contentAlignment = Alignment.Center,
) { Waveform(Modifier.size(size * 0.5f, size * 0.36f), bars = 7, seed = 3) }

@DrawableRes
fun avatarOf(p: Person?): Int = when {
    p == null -> R.drawable.avatar_me
    p.name.contains("아빠") -> R.drawable.avatar_dad
    p.name.contains("엄마") -> R.drawable.avatar_mom
    p.role == PersonRole.CHILD -> R.drawable.avatar_me
    else -> R.drawable.avatar_dad
}

@Composable
fun Avatar(p: Person?, size: Dp = 120.dp, ring: Color = Hg.Line) =
    Image(
        painterResource(avatarOf(p)), contentDescription = p?.name, contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape).border(3.dp, ring, CircleShape),
    )

/** Illustration with rounded corners, filling its box. */
@Composable
fun Art(@DrawableRes res: Int, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp), overlay: @Composable BoxScope.() -> Unit = {}) =
    Box(modifier.clip(shape)) {
        Image(painterResource(res), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        overlay()
    }

/** Rotates through the bundled memory illustrations so the list is not one image repeated. */
@DrawableRes
fun memoryArt(clipId: Long): Int = when ((clipId % 3).toInt()) {
    0 -> R.drawable.art_memory_han_river
    1 -> R.drawable.art_home_road
    else -> R.drawable.art_drive_bg
}

@Composable
fun Divider(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(Hg.LineSoft))

@Composable
fun Label(text: String, color: Color = Hg.Muted, style: TextStyle = MaterialTheme.typography.bodyLarge, modifier: Modifier = Modifier) =
    Text(text, color = color, style = style, modifier = modifier)

/** "아빠" → "아빠와", "엄마" → "엄마와", consonant-final → "과". */
fun withAnd(name: String): String {
    val last = name.lastOrNull() ?: return name
    val hasFinal = last in '가'..'힣' && (last - '가') % 28 != 0
    return name + if (hasFinal) "과" else "와"
}

fun mmss(ms: Long): String = "%02d:%02d".format(ms / 60_000, ms / 1000 % 60)
