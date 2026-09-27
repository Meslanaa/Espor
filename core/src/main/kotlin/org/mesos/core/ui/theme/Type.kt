package org.mesos.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.mesos.core.R

/*
 * Aurora typography. Both fonts are variable fonts bundled with MesOS (SIL Open
 * Font License, see assets/licenses): Sora for the clock and titles, Manrope for
 * everything people read.
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Display face: clock, large titles, big numbers. */
val Sora: FontFamily = FontFamily(
    variable(R.font.sora, 300),
    variable(R.font.sora, 400),
    variable(R.font.sora, 600),
    variable(R.font.sora, 700),
)

/** Text face: UI text, labels, body. */
val Manrope: FontFamily = FontFamily(
    variable(R.font.manrope, 400),
    variable(R.font.manrope, 500),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
    variable(R.font.manrope, 800),
)

private fun sora(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Sora,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private fun manrope(weight: FontWeight, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

internal val MesOSTypography = Typography(
    displayLarge = sora(FontWeight.Light, 57, 64, -1.5),
    displayMedium = sora(FontWeight.Light, 45, 52, -1.0),
    displaySmall = sora(FontWeight.Normal, 36, 44, -0.5),
    headlineLarge = sora(FontWeight.SemiBold, 32, 40, -0.8),
    headlineMedium = sora(FontWeight.SemiBold, 28, 36, -0.6),
    headlineSmall = sora(FontWeight.SemiBold, 24, 32, -0.4),
    titleLarge = manrope(FontWeight.ExtraBold, 20, 28),
    titleMedium = manrope(FontWeight.Bold, 16, 24),
    titleSmall = manrope(FontWeight.Bold, 14, 20),
    bodyLarge = manrope(FontWeight.Medium, 16, 24),
    bodyMedium = manrope(FontWeight.Medium, 14, 20),
    bodySmall = manrope(FontWeight.Medium, 12, 16),
    labelLarge = manrope(FontWeight.Bold, 14, 20),
    labelMedium = manrope(FontWeight.Bold, 12, 16),
    labelSmall = manrope(FontWeight.Bold, 11, 16, 0.2),
)
