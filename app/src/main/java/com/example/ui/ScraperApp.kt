package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.data.database.ScrapeHistory
import com.example.data.database.ScraperRecipe
import com.example.data.model.ScrapedItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScraperApp(
    viewModel: ScraperViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recipes by viewModel.recipes.collectAsState()
    val history by viewModel.history.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Kazıyıcı", "Tariflerim", "Geçmiş")
    val tabIcons = listOf(Icons.Default.PlayArrow, Icons.Default.Star, Icons.Default.Refresh)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "App Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Web Kazıyıcı",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                actions = {
                    val apiKey = BuildConfig.GEMINI_API_KEY
                    val isKeyConfigured = apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY"
                    
                    if (!isKeyConfigured) {
                        IconButton(onClick = {
                            Toast.makeText(
                                context,
                                "Gemini API Anahtarı eksik! Yapay Zekalı kazıma için Secrets'ten GEMINI_API_KEY tanımlayın.",
                                Toast.LENGTH_LONG
                            ).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Varsayılan API Anahtarı Uyarısı",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            Toast.makeText(
                                context,
                                "Gemini AI Entegrasyonu Aktif!",
                                Toast.LENGTH_SHORT
                            ).show()
                        }) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "API Yapılandırıldı",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabTitles.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(title, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(tabIcons[index], contentDescription = title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> ScrapeFormTab(viewModel, onNavigateToRecipes = { selectedTab = 1 })
                1 -> RecipesTab(recipes, viewModel, onLoadRecipe = { recipe ->
                    viewModel.loadRecipe(recipe)
                    selectedTab = 0
                })
                2 -> HistoryTab(history, viewModel)
            }

            // Global Details Results Sheet Overlay
            if (viewModel.showResultsOverlay) {
                ResultsSheet(
                    viewModel = viewModel,
                    onDismiss = { viewModel.showResultsOverlay = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScrapeFormTab(
    viewModel: ScraperViewModel,
    onNavigateToRecipes: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Suggested Targets (Horizontal Bar)
        Text(
            text = "Hızlı Başlangıç Önerileri",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            items(viewModel.popularSuggestions) { suggestion ->
                SuggestionChipCard(suggestion = suggestion) {
                    viewModel.applySuggestion(suggestion)
                    Toast.makeText(context, "Öneri uygulandı!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Target URL Configuration Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Hedef Web Sitesi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = viewModel.inputUrl,
                    onValueChange = { viewModel.inputUrl = it },
                    label = { Text("Web Adresi veya URL") },
                    placeholder = { Text("https://example.com") },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = "Link") },
                    trailingIcon = {
                        if (viewModel.inputUrl.isNotEmpty()) {
                            IconButton(onClick = { viewModel.inputUrl = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("target_url_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipBoard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipBoard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val pasteText = clipData.getItemAt(0).text.toString()
                                if (pasteText.isNotBlank()) {
                                    viewModel.inputUrl = pasteText
                                    Toast.makeText(context, "Panodan yapıştırıldı!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Pano boş!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Paste", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Panodan Al")
                    }

                    Button(
                        onClick = {
                            viewModel.loadHtmlOnly()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.List, contentDescription = "HTML Only", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("HTML İncele")
                    }
                }
            }
        }

        // Mode Switcher Card (Selector vs AI)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Kazıma Yöntemi",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    val isSelector = viewModel.selectedMode == "SELECTOR"
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelector) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.selectedMode = "SELECTOR" }
                    ) {
                        Text(
                            text = "Standart CSS Seçici",
                            fontWeight = FontWeight.Bold,
                            color = if (isSelector) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isSelector) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.selectedMode = "AI" }
                    ) {
                        Text(
                            text = "Yapay Zeka (Gemini)",
                            fontWeight = FontWeight.Bold,
                            color = if (!isSelector) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }

                AnimatedContent(
                    targetState = viewModel.selectedMode,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "mode_fields_toggle"
                ) { mode ->
                    if (mode == "SELECTOR") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = viewModel.inputSelector,
                                onValueChange = { viewModel.inputSelector = it },
                                label = { Text("CSS Seçici (CSS Selector)") },
                                placeholder = { Text(".product-title, h3, a.link") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("css_selector_input"),
                                trailingIcon = {
                                    IconButton(onClick = {
                                        Toast.makeText(context, "Örn: div.class_name, #id, tag_adı", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.Info, contentDescription = "Help")
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = viewModel.inputAttr,
                                onValueChange = { viewModel.inputAttr = it },
                                label = { Text("Öznitelik / Attribute Değeri (Opsiyonel)") },
                                placeholder = { Text("örn: href, src, title") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (viewModel.inputAttr.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.inputAttr = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )

                            // Quick helper chips
                            Text(
                                text = "Sık Kullanılan Seçiciler:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                QuickSelectorChip("Link Adresleri (a)", "a", "href") { s, a ->
                                    viewModel.inputSelector = s
                                    viewModel.inputAttr = a
                                }
                                QuickSelectorChip("Görseller (img)", "img", "src") { s, a ->
                                    viewModel.inputSelector = s
                                    viewModel.inputAttr = a
                                }
                                QuickSelectorChip("Başlık etiketleri (h1-h3)", "h1, h2, h3", "") { s, a ->
                                    viewModel.inputSelector = s
                                    viewModel.inputAttr = a
                                }
                                QuickSelectorChip("Metin Paragrafları (p)", "p", "") { s, a ->
                                    viewModel.inputSelector = s
                                    viewModel.inputAttr = a
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = viewModel.inputAiPrompt,
                                onValueChange = { viewModel.inputAiPrompt = it },
                                label = { Text("Yapay Zekaya Ayıklama Komutu") },
                                placeholder = { Text("Örn: Ürün adını, fiyatlarını ve puanlarını çıkart") },
                                minLines = 3,
                                maxLines = 5,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().testTag("ai_prompt_input")
                            )

                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3F),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Bilgi",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "Yapay Zeka modu, web sayfasının metinsel içeriğini Gemini API yardımıyla analiz ederek filtreleme yapar ve yapısal düzende çıkarır.",
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scrape Button
        Button(
            onClick = {
                viewModel.performScrape()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("scrape_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(4.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Scrape")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "KAZIMAYA BAŞLA",
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun SuggestionChipCard(
    suggestion: SelectorSuggestion,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
        modifier = Modifier
            .width(200.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = suggestion.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = suggestion.description,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Seçici: ${suggestion.selector}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun QuickSelectorChip(
    label: String,
    selector: String,
    attr: String,
    onApply: (String, String) -> Unit
) {
    AssistChip(
        onClick = { onApply(selector, attr) },
        label = { Text(label, fontSize = 11.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecipesTab(
    recipes: List<ScraperRecipe>,
    viewModel: ScraperViewModel,
    onLoadRecipe: (ScraperRecipe) -> Unit
) {
    val context = LocalContext.current
    
    if (recipes.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.FavoriteBorder,
                contentDescription = "Tarifler Boş",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Henüz kayıtlı tarifiniz bulunmuyor.",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Kazıdıktan sonra çıkan panelden 'Tarif Olarak Kaydet' butonuna basarak tariflerinizi burada listeleyebilirsiniz.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Kayıtlı Kazıyıcı Şablonları (${recipes.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(recipes, key = { it.id }) { recipe ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                            .clickable { onLoadRecipe(recipe) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isAi = recipe.mode == "AI"
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(if (isAi) "AI" else "Selector", fontSize = 10.sp) },
                                        colors = SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = if (isAi) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                            labelColor = if (isAi) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                        ),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = recipe.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = recipe.url,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (recipe.mode == "SELECTOR") {
                                    Text(
                                        text = "Seçici: ${recipe.cssSelector} ${if (recipe.attrName.isNotEmpty()) "[${recipe.attrName}]" else ""}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                } else {
                                    Text(
                                        text = "Komut: ${recipe.aiPrompt}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            
                            IconButton(onClick = {
                                viewModel.deleteRecipe(recipe.id)
                                Toast.makeText(context, "Tarif silindi", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Sil",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryTab(
    history: List<ScrapeHistory>,
    viewModel: ScraperViewModel
) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    if (history.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Geçmiş Boş",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Aktivite kaydı bulunmuyor.",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Uyguladığınız tüm kazıma faaliyetleri başarı veya başarısızlık durumları ile burada günlüğe eklenir.",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Kazıma Geçmişi (${history.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        Toast.makeText(context, "Tüm geçmiş temizlendi", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Süpür", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tümünü Sil", fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(history, key = { it.id }) { log ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                            .clickable {
                                if (log.status == "SUCCESS") {
                                    val deserialized = viewModel.deserializeHistoryResults(log.resultJson)
                                    viewModel.activeScrapedItems = deserialized
                                    viewModel.scrapingState = ScrapingUiState.Success(deserialized)
                                    viewModel.inputUrl = log.url
                                    viewModel.selectedMode = log.mode
                                    viewModel.showResultsOverlay = true
                                    viewModel.loadHtmlOnly()
                                } else {
                                    viewModel.selectedHistoryItem = log
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isSuccess = log.status == "SUCCESS"
                                    Surface(
                                        color = if (isSuccess) Color(0xFFE6F4EA) else Color(0xFFFCE8E6),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (isSuccess) "BAŞARILI" else "HATA",
                                            color = if (isSuccess) Color(0xFF137333) else Color(0xFFC5221F),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = log.recipeTitle,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 140.dp)
                                    )
                                }

                                Text(
                                    text = dateFormat.format(Date(log.timestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = log.url,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Yöntem: ${if (log.mode == "AI") "Yapay Zeka" else "CSS Seçici"}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (log.status == "SUCCESS") {
                                    Text(
                                        text = "${log.itemsFound} Öğe Bulundu",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                } else {
                                    Text(
                                        text = "Hatayı İncele",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Interactive Error Dialog for failed scrapes
    viewModel.selectedHistoryItem?.let { log ->
        AlertDialog(
            onDismissRequest = { viewModel.selectedHistoryItem = null },
            confirmButton = {
                Button(onClick = { viewModel.selectedHistoryItem = null }) {
                    Text("Kapat")
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = "Hata", tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kazıma Başarısız Oldu")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hedef URL:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(log.url, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Hata Detayı:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = log.errorMessage.ifBlank { "Host adresi çözümlenemedi veya internet bağlantısı kesildi." },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Text(
                        text = "İpucu: Sitede robot koruması (Cloudflare vb.), IP engelleme olabilir veya girdiğiniz URL adresi eksiktir.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
fun ResultsSheet(
    viewModel: ScraperViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var innerSheetTab by remember { mutableStateOf(0) }
    var zoomItemHtml by remember { mutableStateOf<ScrapedItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { /* Block clicks */ }
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    Text(
                        text = "Kazıma Sonuçları",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    IconButton(onClick = {
                        val json = convertListToJson(viewModel.activeScrapedItems)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Scraped Data", json))
                        Toast.makeText(context, "Tüm liste JSON formatında panoya kopyalandı!", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "JSON Kopyala", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Results Layout State
                when (val state = viewModel.scrapingState) {
                    is ScrapingUiState.Idle -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth())
                    }
                    is ScrapingUiState.Loading -> {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Web sayfası indiriliyor ve veriler ayıklanıyor...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Lütfen bekleyin...",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                    is ScrapingUiState.Error -> {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hata",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Kazıma Sırasında Hata Oluştu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = onDismiss) {
                                Text("Geri Dön")
                            }
                        }
                    }
                    is ScrapingUiState.Success -> {
                        var isSavingActive by remember { mutableStateOf(false) }

                        Column(modifier = Modifier.weight(1f)) {
                            // Sub-Header Indicators
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${viewModel.activeScrapedItems.size} Öğe Çıkarıldı",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )

                                Text(
                                    text = viewModel.inputUrl.substringAfter("://").substringBefore("/"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (!isSavingActive) {
                                TextButton(
                                    onClick = { isSavingActive = true },
                                    modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Kaydet", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Bu Ayarı Tarif Yap", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = viewModel.saveRecipeTitle,
                                        onValueChange = { viewModel.saveRecipeTitle = it },
                                        placeholder = { Text("Tarif Adı girin (Örn: Hürriyet Manşet)") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.saveAsRecipe()
                                            isSavingActive = false
                                            Toast.makeText(context, "Tariflerim sekmesine kaydedildi!", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Kaydet")
                                    }
                                    IconButton(onClick = { isSavingActive = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "İptal")
                                    }
                                }
                            }

                            // Interactive results internal tabs
                            TabRow(selectedTabIndex = innerSheetTab) {
                                Tab(
                                    selected = innerSheetTab == 0,
                                    onClick = { innerSheetTab = 0 },
                                    text = { Text("Bulunan Öğeler (Tablo)", fontWeight = FontWeight.Bold) }
                                )
                                Tab(
                                    selected = innerSheetTab == 1,
                                    onClick = { innerSheetTab = 1 },
                                    text = { Text("Ham HTML İncele", fontWeight = FontWeight.Bold) }
                                )
                            }

                            if (innerSheetTab == 0) {
                                ItemsTableScreen(
                                    items = viewModel.activeScrapedItems,
                                    viewModel = viewModel,
                                    onItemClick = { zoomItemHtml = it }
                                )
                            } else {
                                HtmlInspectorScreen(viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Expanded dialog zooming into specific items raw HTML block
        zoomItemHtml?.let { item ->
            Dialog(onDismissRequest = { zoomItemHtml = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Öğe #${item.index} HTML Kaynağı",
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(onClick = { zoomItemHtml = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Kapat")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Görünür Metin:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.text,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (!item.attrValue.isNullOrBlank()) {
                            Text(
                                text = "Ayıklanan Öznitelik (href vb.):",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                text = item.attrValue,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ham HTML Kod Bloğu:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        ) {
                            SelectionContainer {
                                Text(
                                    text = item.html,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Element HTML", item.html))
                                    Toast.makeText(context, "HTML bloğu panoya kopyalandı", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Kopyala", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("HTML Kopyala")
                            }
                            OutlinedButton(
                                onClick = { zoomItemHtml = null },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Kapat")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemsTableScreen(
    items: List<ScrapedItem>,
    viewModel: ScraperViewModel,
    onItemClick: (ScrapedItem) -> Unit
) {
    var filterQuery by remember { mutableStateOf("") }
    
    val filteredItems = items.filter {
        it.text.contains(filterQuery, ignoreCase = true) || 
        (it.attrValue?.contains(filterQuery, ignoreCase = true) ?: false)
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Search bar
        OutlinedTextField(
            value = filterQuery,
            onValueChange = { filterQuery = it },
            placeholder = { Text("Bulunan öğeler içerisinde ara...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Ara") },
            trailingIcon = {
                if (filterQuery.isNotEmpty()) {
                    IconButton(onClick = { filterQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Temizle")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        )

        if (filteredItems.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Bulunamadı",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aramanizla uyuşan öğe bulunamadı.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredItems) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item) },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "#${item.index}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Öğe Metni",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "HTML Çıktısı",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Default.List,
                                        contentDescription = "Metin",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            SelectionContainer {
                                Text(
                                    text = item.text.ifBlank { "[Boş veya Görsel Öğesi]" },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (!item.attrValue.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Öznitelik",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${viewModel.inputAttr.ifBlank { "Değer" }}: ",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    SelectionContainer {
                                        Text(
                                            text = item.attrValue,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 240.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HtmlInspectorScreen(viewModel: ScraperViewModel) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Finder Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Sayfa kaynağında etiket, sınıf ara...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Bul") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Wipe")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = {
                    if (viewModel.rawHtmlText.isNotEmpty()) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Page Html", viewModel.rawHtmlText))
                        Toast.makeText(context, "Tüm sayfa kaynağı kopyalandı!", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Kopyala")
            }
        }

        if (viewModel.isHtmlLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (viewModel.rawHtmlText.isBlank()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Sayfa kaynağı yüklenemedi veya boş döndü.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            // Display highlighted formatted text or selection
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    val codeText = viewModel.rawHtmlText
                    
                    val annotatedString = if (searchQuery.isNotBlank() && codeText.contains(searchQuery, ignoreCase = true)) {
                        buildAnnotatedStringWithHighlights(codeText, searchQuery, Color(0xFFFFCC00), Color.Black)
                    } else {
                        AnnotatedString(codeText)
                    }

                    Text(
                        text = annotatedString,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFD4D4D4),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

private fun convertListToJson(items: List<ScrapedItem>): String {
    return try {
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        val listType = Types.newParameterizedType(List::class.java, ScrapedItem::class.java)
        val adapter = moshi.adapter<List<ScrapedItem>>(listType)
        adapter.indent("  ").toJson(items)
    } catch (e: Exception) {
        "[]"
    }
}

@Composable
private fun buildAnnotatedStringWithHighlights(
    text: String,
    query: String,
    highlightColor: Color,
    textColor: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder(text)
    var index = text.indexOf(query, ignoreCase = true)
    
    var limitCount = 0
    while (index >= 0 && limitCount < 80) {
        builder.addStyle(
            style = androidx.compose.ui.text.SpanStyle(
                background = highlightColor,
                color = textColor,
                fontWeight = FontWeight.Bold
            ),
            start = index,
            end = index + query.length
        )
        index = text.indexOf(query, index + query.length, ignoreCase = true)
        limitCount++
    }
    return builder.toAnnotatedString()
}
