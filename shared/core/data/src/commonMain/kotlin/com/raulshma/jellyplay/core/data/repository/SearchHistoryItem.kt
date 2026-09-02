package com.raulshma.jellyplay.core.data.repository

data class SearchHistoryItem(
    val id: Long,
    val query: String,
    val searchedAt: Long,
)
