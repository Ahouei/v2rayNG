package com.v2ray.ang.ui.compose

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Colour tokens of the glass design language. Pure values so the contrast guarantees can be
 * unit-tested on the JVM.
 */
@Immutable
data class GlassPalette(
    val base: Color,
    val glowGreen: Color,
    val glowTeal: Color,
    val glowBlue: Color,
    val fill: Color,
    val edge: Color,
    val highlight: Color,
    val shadow: Color,
    val ink: Color,
    val inkVariant: Color,
)

object GlassTokens {
    val inkLight = Color(0xFF0E1726)
    val baseDark = Color(0xFF05080D)

    val Light = GlassPalette(
        base = Color(0xFFF4F7F6),
        glowGreen = Color(0x5517784B),
        glowTeal = Color(0x4D2BB3A3),
        glowBlue = Color(0x4D3B82F6),
        fill = Color.White.copy(alpha = 0.45f),
        edge = Color.White.copy(alpha = 0.85f),
        highlight = Color.White.copy(alpha = 0.70f),
        shadow = Color(0x330E1726),
        ink = inkLight,
        inkVariant = Color(0xFF3D4A5C),
    )

    val Dark = GlassPalette(
        base = baseDark,
        glowGreen = Color(0x552FAE74),
        glowTeal = Color(0x331FA39A),
        glowBlue = Color(0x443B6FD6),
        fill = Color.White.copy(alpha = 0.08f),
        edge = Color.White.copy(alpha = 0.18f),
        highlight = Color.White.copy(alpha = 0.14f),
        shadow = Color(0x66000000),
        ink = Color(0xFFF1F5F9),
        inkVariant = Color(0xFFB8C2CF),
    )

    fun palette(dark: Boolean): GlassPalette = if (dark) Dark else Light

    /**
     * Worst-case opaque colour seen behind glass text: the fill over the base plus the strongest
     * glow, used to check text contrast.
     */
    fun effectiveSurface(palette: GlassPalette, glow: Color): Color =
        palette.fill.compositeOver(glow.compositeOver(palette.base))

    /** Green "fastest"/selected glass tint built from the connected tokens. */
    fun greenTint(dark: Boolean): Color =
        (if (dark) colorConnectedDark else colorConnectedLight).copy(alpha = if (dark) 0.22f else 0.16f)

    /** WCAG 2.x contrast ratio between two opaque colours. */
    fun contrastRatio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Backdrop blur through RenderEffect exists only on Android 12 (API 31) and newer. */
    fun supportsBlur(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.S
}

/** Real blur is available only on Android 12 (API 31)+; below it glass is a translucent fill. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun isGlassBlurSupported(): Boolean = GlassTokens.supportsBlur(Build.VERSION.SDK_INT)

val GlassShapeLarge = RoundedCornerShape(28.dp)
val GlassShapePill = RoundedCornerShape(percent = 50)

/**
 * Soft "living" backdrop: layered radial glows over an off-white (light) or deep ink (dark) base.
 * On API 31+ the glow layer is blurred with RenderEffect for a frosted look; older versions draw
 * the same gradients without blur.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    dark: Boolean = LocalDarkTheme.current,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = GlassTokens.palette(dark)
    Box(modifier = modifier.fillMaxSize().background(palette.base)) {
        val glowLayer = if (isGlassBlurSupported()) Modifier.blur(48.dp) else Modifier
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(glowLayer)
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val r = max(w, h)
                    drawRect(
                        Brush.radialGradient(
                            listOf(palette.glowGreen, Color.Transparent),
                            center = Offset(w * 0.1f, h * 0.12f),
                            radius = r * 0.6f,
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(palette.glowTeal, Color.Transparent),
                            center = Offset(w * 0.95f, h * 0.45f),
                            radius = r * 0.55f,
                        )
                    )
                    drawRect(
                        Brush.radialGradient(
                            listOf(palette.glowBlue, Color.Transparent),
                            center = Offset(w * 0.25f, h * 0.95f),
                            radius = r * 0.6f,
                        )
                    )
                }
        )
        content()
    }
}

/**
 * Translucent glass container: fill, 1dp light edge, inner top highlight, soft outer shadow and
 * large rounded corners. [tint] overlays a colour (e.g. green for the fastest choice).
 * [interaction] (clickable/selectable) is applied after the shape clip so the ripple is clipped
 * while the shadow stays outside the shape; callers must not clip in [modifier].
 * Use elevation = 0.dp for list rows to skip the shadow layer.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShapeLarge,
    tint: Color = Color.Transparent,
    edgeColor: Color? = null,
    elevation: Dp = 12.dp,
    dark: Boolean = LocalDarkTheme.current,
    interaction: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = GlassTokens.palette(dark)
    val edge = edgeColor ?: palette.edge
    val edgeBrush = remember(edge) { Brush.verticalGradient(listOf(edge, edge.copy(alpha = 0.2f))) }
    Box(
        modifier = modifier
            .then(if (elevation > 0.dp) Modifier.glassOuterShadow(shape, palette.shadow, elevation) else Modifier)
            .clip(shape)
            .background(palette.fill)
            .background(tint)
            .glassHighlight(palette.highlight)
            .border(BorderStroke(1.dp, edgeBrush), shape)
            .then(interaction),
        content = content,
    )
}

/**
 * Soft shadow drawn only outside [shape], so it never shows through a translucent fill (an
 * elevation shadow is rendered under the whole outline and darkens the glass).
 */
fun Modifier.glassOuterShadow(shape: Shape, color: Color, radius: Dp): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val r = radius.toPx()
    val steps = 6
    onDrawBehind {
        clipPath(path, clipOp = ClipOp.Difference) {
            for (i in steps downTo 1) {
                val w = r * i / steps * 2f
                drawPath(
                    path = path,
                    color = color,
                    alpha = 1f / steps,
                    style = Stroke(width = w),
                )
            }
        }
    }
}

/** Inner top highlight drawn behind content (fades out by 45% of the height). */
fun Modifier.glassHighlight(color: Color): Modifier = drawWithCache {
    val brush = Brush.verticalGradient(0f to color, 0.45f to Color.Transparent)
    onDrawWithContent {
        drawRect(brush)
        drawContent()
    }
}
