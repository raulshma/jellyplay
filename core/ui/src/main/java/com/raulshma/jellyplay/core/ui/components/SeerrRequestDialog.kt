package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem

@Composable
fun SeerrRequestDialog(
    item: SeerrSearchItem,
    isRequesting: Boolean = false,
    requestSuccess: Boolean? = null,
    requestError: String? = null,
    onConfirm: (selectedSeasons: List<Int>?) -> Unit,
    onDismiss: () -> Unit,
) {
    val isTv = item.mediaType.equals("tv", ignoreCase = true)
    var selectedSeasons by remember { mutableStateOf(setOf<Int>()) }

    AlertDialog(
        onDismissRequest = { if (!isRequesting) onDismiss() },
        title = {
            Text(
                text = if (requestSuccess == true) "Request Submitted!" else "Request Media",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            when {
                requestSuccess == true -> {
                    Column {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${item.displayName} has been requested successfully.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                requestError != null -> {
                    Column {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = requestError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                else -> {
                    Column {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.year?.let { year ->
                            Text(
                                text = year.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item.overview?.let { overview ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Season picker for TV shows
                        if (isTv) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "All seasons will be requested.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                requestSuccess == true -> {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
                requestError != null -> {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                else -> {
                    TextButton(
                        onClick = {
                            onConfirm(if (isTv && selectedSeasons.isNotEmpty()) selectedSeasons.toList() else null)
                        },
                        enabled = !isRequesting,
                    ) {
                        if (isRequesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Request")
                    }
                }
            }
        },
        dismissButton = {
            if (requestSuccess == null && requestError == null) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isRequesting,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel")
                }
            }
        },
        shape = RoundedCornerShape(20.dp),
    )
}
