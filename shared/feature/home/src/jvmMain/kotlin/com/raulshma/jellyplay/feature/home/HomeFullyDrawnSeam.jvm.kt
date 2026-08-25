package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** JVM actual: desktop has no TTFD metric — the report is a no-op. */
@Composable
internal actual fun rememberReportHomeFullyDrawn(): () -> Unit = remember { {} }
