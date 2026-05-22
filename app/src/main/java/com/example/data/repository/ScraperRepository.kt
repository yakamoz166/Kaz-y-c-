package com.example.data.repository

import com.example.data.database.ScraperDao
import com.example.data.database.ScraperRecipe
import com.example.data.database.ScrapeHistory
import com.example.data.model.ScrapedItem
import com.example.data.network.GeminiApiClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

class ScraperRepository(private val dao: ScraperDao) {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val allRecipes: Flow<List<ScraperRecipe>> = dao.getAllRecipes()
    val allHistory: Flow<List<ScrapeHistory>> = dao.getAllHistory()

    suspend fun insertRecipe(recipe: ScraperRecipe): Long = dao.insertRecipe(recipe)
    suspend fun deleteRecipe(id: Long) = dao.deleteRecipeById(id)
    suspend fun getRecipeById(id: Long): ScraperRecipe? = dao.getRecipeById(id)

    suspend fun insertHistory(history: ScrapeHistory): Long = dao.insertHistory(history)
    suspend fun deleteHistory(id: Long) = dao.deleteHistoryById(id)
    suspend fun clearHistory() = dao.clearAllHistory()

    /**
     * Sadece ham HTML kodunu çeker (HTML editöründe göstermek için)
     */
    suspend fun fetchRawHtml(url: String): String = withContext(Dispatchers.IO) {
        val cleanUrl = if (!url.trim().startsWith("http://") && !url.trim().startsWith("https://")) {
            "https://${url.trim()}"
        } else url.trim()

        val doc = Jsoup.connect(cleanUrl)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .referrer("http://www.google.com")
            .timeout(20000)
            .followRedirects(true)
            .get()
        doc.outerHtml()
    }

    /**
     * Standart CSS Seçici yöntemiyle web sitesinden veri kazır
     */
    suspend fun scrapeWithSelector(
        url: String,
        selector: String,
        attrName: String = ""
    ): List<ScrapedItem> = withContext(Dispatchers.IO) {
        val cleanUrl = if (!url.trim().startsWith("http://") && !url.trim().startsWith("https://")) {
            "https://${url.trim()}"
        } else url.trim()

        val doc = Jsoup.connect(cleanUrl)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .referrer("http://www.google.com")
            .timeout(20000)
            .followRedirects(true)
            .get()

        val cssSelector = selector.trim().ifBlank { "body" }
        val elements = doc.select(cssSelector)

        elements.mapIndexed { index, element ->
            val attrValue = if (attrName.trim().isNotBlank()) {
                element.attr(attrName.trim())
            } else {
                null
            }
            ScrapedItem(
                index = index + 1,
                text = element.text(),
                attrValue = attrValue,
                html = element.outerHtml()
            )
        }
    }

    /**
     * AI tabanlı veri kazıma. Sitenin içeriğini çeker ve Gemini'ye yollayıp JSON olarak temiz veri ayıklar.
     */
    suspend fun scrapeWithAI(
        url: String,
        prompt: String
    ): List<ScrapedItem> = withContext(Dispatchers.IO) {
        val cleanUrl = if (!url.trim().startsWith("http://") && !url.trim().startsWith("https://")) {
            "https://${url.trim()}"
        } else url.trim()

        // Adım 1: Sayfayı çek
        val doc = Jsoup.connect(cleanUrl)
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .referrer("http://www.google.com")
            .timeout(20000)
            .followRedirects(true)
            .get()

        // Adım 2: Sayfanın görünür metnini kısaltıp ayıkla.
        val bodyText = doc.body().text()
        val truncatedText = if (bodyText.length > 15000) {
            bodyText.substring(0, 15000) + "... [İçerik Çok Uzun Olduğu İçin Kırpıldı]"
        } else {
            bodyText
        }

        // Adım 3: Gemini için detaylı prompt hazırla
        val geminiPrompt = """
            Aşağıdaki web sitesi düz metin içeriğini analiz et ve kullanıcının istediği bilgileri ayıkla.
            
            Kullanıcının Veri Çekme İstemi: "$prompt"
            
            Bulduğun her bir eşleşen öğeyi içeren bir JSON dizisi döndür.
            Dönecek JSON bir liste dizisi olmalı ve her bir öğe tam olarak şu alanları içermelidir:
            - "text": Öğenin kısa başlığı, ismi veya birincil açıklaması.
            - "attrValue": Bulduğun herhangi bir ek detay (örn: fiyatı, adresi, puanı, linki, tarihi)(varsa). Sadece string değer koy.
            - "html": İlgili öğenin bağlamı, kısa açıklaması veya açıklayıcı metni.
            
            Herhangi bir açıklama, önsöz veya markdown kodu etiketleri (```json gibi etiketler) ekleme. Sadece doğrudan saf geçerli bir JSON dizisi döndür.
            
            Web Sitesi Düz Metin İçeriği:
            $truncatedText
        """.trimIndent()

        // Adım 4: Gemini modelini çağır
        val rawAiResult = GeminiApiClient.generateContent(geminiPrompt)
        
        if (rawAiResult.startsWith("Error")) {
            throw IOException(rawAiResult)
        }

        // JSON çıktıyı temizle (bazen markdown kodu olarak verebiliyor)
        val cleanJson = cleanRawJson(rawAiResult)

        // Adım 5: Elde edilen JSON'u ScrapedItem listesine dönüştür
        try {
            parseDynamicJson(cleanJson)
        } catch (e: Exception) {
            listOf(
                ScrapedItem(
                    index = 1,
                    text = "AI Yanıtı Çözümlenemedi",
                    attrValue = "Lütfen ham çıktıyı inceleyin",
                    html = rawAiResult
                )
            )
        }
    }

    private fun cleanRawJson(raw: String): String {
        var temp = raw.trim()
        if (temp.startsWith("```json")) {
            temp = temp.substring(7)
        } else if (temp.startsWith("```")) {
            temp = temp.substring(3)
        }
        if (temp.endsWith("```")) {
            temp = temp.substring(0, temp.length - 3)
        }
        return temp.trim()
    }

    private fun parseDynamicJson(json: String): List<ScrapedItem> {
        return try {
            val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
            val listType = Types.newParameterizedType(List::class.java, mapType)
            val listAdapterMap = moshi.adapter<List<Map<String, Any>>>(listType)
            val rawList = listAdapterMap.fromJson(json) ?: return emptyList()
            
            rawList.mapIndexed { index, map ->
                val text = map["text"]?.toString() 
                    ?: map["title"]?.toString() 
                    ?: map["ad"]?.toString() 
                    ?: map["name"]?.toString() 
                    ?: map.values.firstOrNull()?.toString() 
                    ?: "Öğe ${index + 1}"
                    
                val attrValue = map["attrValue"]?.toString() 
                    ?: map["price"]?.toString() 
                    ?: map["fiyat"]?.toString() 
                    ?: map["value"]?.toString() 
                    ?: map["url"]?.toString() 
                    ?: ""
                    
                val html = map["html"]?.toString() 
                    ?: map["description"]?.toString() 
                    ?: map.toString()
                
                ScrapedItem(
                    index = index + 1,
                    text = text,
                    attrValue = attrValue,
                    html = html
                )
            }
        } catch (e: Exception) {
            listOf(
                ScrapedItem(
                    index = 1,
                    text = "AI JSON Parse Hatası",
                    attrValue = e.localizedMessage,
                    html = json
                )
            )
        }
    }
}
