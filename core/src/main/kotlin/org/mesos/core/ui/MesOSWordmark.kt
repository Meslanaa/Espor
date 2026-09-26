package org.mesos.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Placeholder MesOS wordmark used for boot identity and About MesOS until final artwork exists. */
@Composable
fun MesOSWordmark(modifier: Modifier = Modifier) {
    Text(
        text = "MESOS",
        modifier = modifier,
        style = MaterialTheme.typography.displayMedium.copy(
            fontWeight = FontWeight.Light,
            letterSpacing = 12.sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
    )
}
