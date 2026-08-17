package com.raulshma.jellyplay.feature.newsletter

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val HEADER_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

@Composable
fun NewsletterHeader(
    serverName: String,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val greeting = when (today.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY -> "Weekend Digest"
        else -> "Your Daily Digest"
    }

    Column(modifier = modifier.padding(vertical = 8.dp)) {
        if (serverName.isNotBlank()) {
            Text(
                text = serverName,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
        }
        Text(
            text = greeting,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = today.format(HEADER_DATE_FORMATTER),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
