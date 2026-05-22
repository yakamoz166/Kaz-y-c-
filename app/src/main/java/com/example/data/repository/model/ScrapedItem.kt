package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ScrapedItem(
    val index: Int,
    val text: String,
    val attrValue: String? = null,
    val html: String
)
