package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scraper_recipes")
data class ScraperRecipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val mode: String, // "SELECTOR" or "AI"
    val cssSelector: String = "", // e.g. "div.product-price" or "h1"
    val attrName: String = "", // e.g. "href" or "src" (empty if text)
    val aiPrompt: String = "", // e.g. "Extract all product titles and prices"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "scrape_history")
data class ScrapeHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long? = null, // Optional relation to ScraperRecipe
    val recipeTitle: String = "",
    val url: String,
    val mode: String,
    val status: String, // "SUCCESS" or "ERROR"
    val itemsFound: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val resultJson: String = "", // JSON string containing scraped items
    val errorMessage: String = ""
)
