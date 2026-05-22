package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScraperDao {
    // Recipes
    @Query("SELECT * FROM scraper_recipes ORDER BY timestamp DESC")
    fun getAllRecipes(): Flow<List<ScraperRecipe>>

    @Query("SELECT * FROM scraper_recipes WHERE id = :id")
    suspend fun getRecipeById(id: Long): ScraperRecipe?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: ScraperRecipe): Long

    @Query("DELETE FROM scraper_recipes WHERE id = :id")
    suspend fun deleteRecipeById(id: Long)

    // History
    @Query("SELECT * FROM scrape_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<ScrapeHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ScrapeHistory): Long

    @Query("DELETE FROM scrape_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM scrape_history")
    suspend fun clearAllHistory()
}
