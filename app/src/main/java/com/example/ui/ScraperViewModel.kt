package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ScrapeHistory
import com.example.data.database.ScraperRecipe
import com.example.data.model.ScrapedItem
import com.example.data.repository.ScraperRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ScrapingUiState {
    object Idle : ScrapingUiState
    object Loading : ScrapingUiState
    data class Success(val items: List<ScrapedItem>) : ScrapingUiState
    data class Error(val message: String) : ScrapingUiState
}

class ScraperViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = ScraperRepository(db.scraperDao())
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val recipes: StateFlow<List<ScraperRecipe>> = repository.allRecipes
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val history: StateFlow<List<ScrapeHistory>> = repository.allHistory
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Scraping Form State
    var inputUrl by mutableStateOf("https://news.ycombinator.com")
    var inputSelector by mutableStateOf(".titleline > a")
    var inputAttr by mutableStateOf("href")
    var inputAiPrompt by mutableStateOf("Haber başlıklarını ve linklerini ayıkla.")
    var selectedMode by mutableStateOf("SELECTOR") // "SELECTOR" or "AI"
    var saveRecipeTitle by mutableStateOf("")

    // Scraping Operation State
    var scrapingState: ScrapingUiState by mutableStateOf(ScrapingUiState.Idle)
    var showResultsOverlay by mutableStateOf(false)
    var activeScrapedItems by mutableStateOf<List<ScrapedItem>>(emptyList())
    
    // HTML Inspector State
    var rawHtmlText by mutableStateOf("")
    var isHtmlLoading by mutableStateOf(false)
    var elementSearchQuery by mutableStateOf("")

    // Selected History Item for Detail View
    var selectedHistoryItem by mutableStateOf<ScrapeHistory?>(null)

    // Quick fill helper lists
    val popularSuggestions = listOf(
        SelectorSuggestion("Haberler (YCombinator)", "https://news.ycombinator.com", ".titleline > a", "href", "Standard link scraping"),
        SelectorSuggestion("Paragraf Metinleri", "https://news.ycombinator.com", "p", "", "Sayfadaki p etiketleri"),
        SelectorSuggestion("Başlıklar", "https://news.ycombinator.com", "h1, h2, h3", "", "Sayfa başlık etiketleri"),
        SelectorSuggestion("Görsel Linkleri", "https://news.ycombinator.com", "img", "src", "Görseller")
    )

    fun applySuggestion(suggestion: SelectorSuggestion) {
        inputUrl = suggestion.url
        inputSelector = suggestion.selector
        inputAttr = suggestion.attr
        selectedMode = "SELECTOR"
    }

    /**
     * Executes the scraper process based on selected mode
     */
    fun performScrape() {
        if (inputUrl.isBlank()) {
            scrapingState = ScrapingUiState.Error("Lütfen geçerli bir URL adresi girin.")
            return
        }

        viewModelScope.launch {
            scrapingState = ScrapingUiState.Loading
            showResultsOverlay = true
            isHtmlLoading = true
            rawHtmlText = ""

            try {
                val results: List<ScrapedItem>
                if (selectedMode == "SELECTOR") {
                    results = repository.scrapeWithSelector(inputUrl, inputSelector, inputAttr)
                } else {
                    results = repository.scrapeWithAI(inputUrl, inputAiPrompt)
                }

                activeScrapedItems = results
                scrapingState = ScrapingUiState.Success(results)

                // Save scrape run to local history inside DB
                saveToHistory(
                    url = inputUrl,
                    mode = selectedMode,
                    status = "SUCCESS",
                    itemCount = results.size,
                    resJson = convertItemsToJson(results)
                )

                // Hızlıca HTML ham kaynağını da arka planda çek (HTML inceleme sekmesi için)
                launch {
                    try {
                        rawHtmlText = repository.fetchRawHtml(inputUrl)
                    } catch (e: Exception) {
                        rawHtmlText = "Ham HTML kaynağı çekilemedi: ${e.localizedMessage}"
                    } finally {
                        isHtmlLoading = false
                    }
                }

            } catch (e: Exception) {
                scrapingState = ScrapingUiState.Error(e.localizedMessage ?: "Bilinmeyen bir hata oluştu.")
                isHtmlLoading = false
                
                saveToHistory(
                    url = inputUrl,
                    mode = selectedMode,
                    status = "ERROR",
                    itemCount = 0,
                    resJson = "[]",
                    errMessage = e.localizedMessage ?: "Bağlantı veya parse hatası"
                )
            }
        }
    }

    /**
     * Sadece HTML Kodunu Çekmek & İncelemek İçin
     */
    fun loadHtmlOnly() {
        if (inputUrl.isBlank()) return
        viewModelScope.launch {
            isHtmlLoading = true
            rawHtmlText = ""
            try {
                rawHtmlText = repository.fetchRawHtml(inputUrl)
            } catch (e: Exception) {
                rawHtmlText = "HTML Kodları çekilemedi: ${e.localizedMessage}"
            } finally {
                isHtmlLoading = false
            }
        }
    }

    /**
     * Saves the current scraper configuration as a recipe
     */
    fun saveAsRecipe() {
        val title = saveRecipeTitle.trim().ifBlank { 
            "Kazıyıcı Recipe - " + inputUrl.substringAfter("://").substringBefore("/") 
        }

        viewModelScope.launch {
            val recipe = ScraperRecipe(
                title = title,
                url = inputUrl,
                mode = selectedMode,
                cssSelector = if (selectedMode == "SELECTOR") inputSelector else "",
                attrName = if (selectedMode == "SELECTOR") inputAttr else "",
                aiPrompt = if (selectedMode == "AI") inputAiPrompt else ""
            )
            repository.insertRecipe(recipe)
            saveRecipeTitle = "" // clear input
        }
    }

    fun loadRecipe(recipe: ScraperRecipe) {
        inputUrl = recipe.url
        selectedMode = recipe.mode
        if (recipe.mode == "SELECTOR") {
            inputSelector = recipe.cssSelector
            inputAttr = recipe.attrName
        } else {
            inputAiPrompt = recipe.aiPrompt
        }
    }

    fun deleteRecipe(id: Long) {
        viewModelScope.launch {
            repository.deleteRecipe(id)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteHistory(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private suspend fun saveToHistory(
        url: String,
        mode: String,
        status: String,
        itemCount: Int,
        resJson: String,
        errMessage: String = ""
    ) {
        val matchingRecipe = recipes.value.firstOrNull { 
            it.url == url && it.mode == mode && (mode == "AI" || it.cssSelector == inputSelector) 
        }
        val recipeTitle = matchingRecipe?.title ?: "Hızlı Kazıma Run"

        repository.insertHistory(
            ScrapeHistory(
                recipeId = matchingRecipe?.id,
                recipeTitle = recipeTitle,
                url = url,
                mode = mode,
                status = status,
                itemsFound = itemCount,
                resultJson = resJson,
                errorMessage = errMessage
            )
        )
    }

    private fun convertItemsToJson(items: List<ScrapedItem>): String {
        return try {
            val listType = Types.newParameterizedType(List::class.java, ScrapedItem::class.java)
            val adapter = moshi.adapter<List<ScrapedItem>>(listType)
            adapter.toJson(items)
        } catch (e: Exception) {
            "[]"
        }
    }

    fun deserializeHistoryResults(json: String): List<ScrapedItem> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val listType = Types.newParameterizedType(List::class.java, ScrapedItem::class.java)
            val adapter = moshi.adapter<List<ScrapedItem>>(listType)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

data class SelectorSuggestion(
    val title: String,
    val url: String,
    val selector: String,
    val attr: String,
    val description: String
)
