package com.ahouei.v2rayekai.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GlassTokensTest {

    @Test
    fun blurOnlyFromApi31() {
        assertFalse(GlassTokens.supportsBlur(24))
        assertFalse(GlassTokens.supportsBlur(30))
        assertTrue(GlassTokens.supportsBlur(31))
        assertTrue(GlassTokens.supportsBlur(36))
    }

    @Test
    fun paletteSelectsByTheme() {
        assertEquals(GlassTokens.Light, GlassTokens.palette(false))
        assertEquals(GlassTokens.Dark, GlassTokens.palette(true))
        assertEquals(Color(0xFF05080D), GlassTokens.Dark.base)
        assertEquals(0.45f, GlassTokens.Light.fill.alpha, 0.01f)
        assertEquals(0.08f, GlassTokens.Dark.fill.alpha, 0.01f)
    }

    @Test
    fun inkContrastOnGlassIsAtLeast45ForEveryGlow() {
        for (dark in listOf(false, true)) {
            val p = GlassTokens.palette(dark)
            for (glow in listOf(Color.Transparent, p.glowGreen, p.glowTeal, p.glowBlue)) {
                val bg = GlassTokens.effectiveSurface(p, glow)
                assertTrue(GlassTokens.contrastRatio(p.ink, bg) >= 4.5, "ink dark=$dark")
                assertTrue(GlassTokens.contrastRatio(p.inkVariant, bg) >= 4.5, "inkVariant dark=$dark")
            }
        }
    }

    @Test
    fun connectedOrbContentContrast() {
        assertTrue(GlassTokens.contrastRatio(onColorConnectedLight, colorConnectedLight) >= 4.5)
        assertTrue(GlassTokens.contrastRatio(onColorConnectedDark, colorConnectedDark) >= 4.5)
    }

    @Test
    fun contrastRatioBounds() {
        assertEquals(21.0, GlassTokens.contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, GlassTokens.contrastRatio(Color.White, Color.White), 0.001)
    }

    @Test
    fun easyPillWhiteLabelContrastOnEveryStop() {
        for (dark in listOf(false, true)) {
            val stops = GlassTokens.easyPillGradient(dark)
            assertEquals(2, stops.size)
            for (stop in stops) {
                assertTrue(GlassTokens.contrastRatio(Color.White, stop) >= 4.5, "stop $stop dark=$dark")
            }
        }
    }

    @Test
    fun easyPillWhiteLabelContrastUnderGloss() {
        val alpha = GlassTokens.easyPillGlossAlphaAt(GlassTokens.EASY_PILL_LABEL_TOP)
        assertTrue(alpha < GlassTokens.EASY_PILL_GLOSS_TOP_ALPHA)
        for (dark in listOf(false, true)) {
            for (stop in GlassTokens.easyPillGradient(dark)) {
                val bg = Color.White.copy(alpha = alpha).compositeOver(stop)
                assertTrue(GlassTokens.contrastRatio(Color.White, bg) >= 4.5, "stop $stop dark=$dark")
            }
        }
    }

    @Test
    fun protocolTagTextContrastOnTintedGlass() {
        for (dark in listOf(false, true)) {
            val p = GlassTokens.palette(dark)
            for (glow in listOf(Color.Transparent, p.glowGreen, p.glowTeal, p.glowBlue)) {
                val bg = GlassTokens.protocolTagTint.compositeOver(GlassTokens.effectiveSurface(p, glow))
                assertTrue(GlassTokens.contrastRatio(GlassTokens.protocolTagText(dark), bg) >= 4.5, "dark=$dark")
            }
        }
    }
}
