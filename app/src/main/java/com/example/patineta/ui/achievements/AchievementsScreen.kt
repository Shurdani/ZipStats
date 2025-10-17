package com.example.patineta.ui.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.example.patineta.R
import com.example.patineta.model.Achievement
import com.example.patineta.model.AchievementLevel
import com.example.patineta.model.AchievementRequirementType
import com.example.patineta.ui.records.RecordsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel = hiltViewModel(),
    @Suppress("UNUSED_PARAMETER") navController: NavHostController
) {
    val achievements by viewModel.achievements.collectAsState()
    var selectedLevel by remember { mutableStateOf<AchievementLevel?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val lastUnlockedAchievementId by viewModel.lastUnlockedAchievementId.collectAsState()
    val shouldRefreshAchievements by viewModel.shouldRefreshAchievements.collectAsState()

    // Observar cuando se deben refrescar los logros
    LaunchedEffect(shouldRefreshAchievements) {
        if (shouldRefreshAchievements > 0) {
            viewModel.loadAchievements()
        }
    }
    
    // Hacer scroll al último logro desbloqueado cuando se navega desde la Snackbar
    LaunchedEffect(lastUnlockedAchievementId) {
        lastUnlockedAchievementId?.let { achievementId ->
            if (achievements is AchievementsUiState.Success) {
                val achievementsList = (achievements as AchievementsUiState.Success).achievements
                val filteredList = achievementsList
                    .filter { it.level != AchievementLevel.SECRETO || it.isUnlocked }
                    .sortedWith(compareBy(
                        // Primero ordenar por estado: desbloqueados primero
                        { !it.isUnlocked },
                        // Luego por categoría (solo para desbloqueados): NOVATO, EXPLORADOR, MAESTRO, SECRETO
                        { achievement ->
                            when (achievement.level) {
                                AchievementLevel.NOVATO -> 1
                                AchievementLevel.EXPLORADOR -> 2
                                AchievementLevel.MAESTRO -> 3
                                AchievementLevel.SECRETO -> 4
                            }
                        },
                        // Finalmente por el valor del requisito
                        { 
                            when (it.requirementType) {
                                AchievementRequirementType.DISTANCE -> it.requiredDistance ?: 0.0
                                AchievementRequirementType.TRIPS -> (it.requiredTrips ?: 0).toDouble()
                                AchievementRequirementType.CONSECUTIVE_DAYS -> (it.requiredConsecutiveDays ?: 0).toDouble()
                                AchievementRequirementType.UNIQUE_SCOOTERS -> (it.requiredUniqueScooters ?: 0).toDouble()
                                AchievementRequirementType.LONGEST_TRIP -> it.requiredLongestTrip ?: 0.0
                                AchievementRequirementType.UNIQUE_WEEKS -> (it.requiredUniqueWeeks ?: 0).toDouble()
                                AchievementRequirementType.MAINTENANCE_COUNT -> (it.requiredMaintenanceCount ?: 0).toDouble()
                                AchievementRequirementType.CO2_SAVED -> it.requiredCO2Saved ?: 0.0
                                AchievementRequirementType.UNIQUE_MONTHS -> (it.requiredUniqueMonths ?: 0).toDouble()
                                AchievementRequirementType.CONSECUTIVE_MONTHS -> (it.requiredConsecutiveMonths ?: 0).toDouble()
                                AchievementRequirementType.ALL_OTHERS -> 999999.0
                                AchievementRequirementType.MULTIPLE -> 0.0
                            }
                        }
                    ))
                
                val index = filteredList.indexOfFirst { it.id == achievementId }
                if (index >= 0) {
                    listState.animateScrollToItem(index)
                }
            }
            viewModel.clearLastUnlockedAchievementId()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Logros") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pestañas de niveles (sin incluir SECRETO)
            ScrollableTabRow(
                selectedTabIndex = when(selectedLevel) {
                    null -> 0
                    AchievementLevel.NOVATO -> 1
                    AchievementLevel.EXPLORADOR -> 2
                    AchievementLevel.MAESTRO -> 3
                    AchievementLevel.SECRETO -> 0 // Si se selecciona SECRETO, volver a Todos
                },
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null }
                ) {
                    Text(
                        text = "Todos",
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        fontWeight = if (selectedLevel == null) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Tab(
                    selected = selectedLevel == AchievementLevel.NOVATO,
                    onClick = { selectedLevel = AchievementLevel.NOVATO }
                ) {
                    Text(
                        text = "🔰 Novato",
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        fontWeight = if (selectedLevel == AchievementLevel.NOVATO) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Tab(
                    selected = selectedLevel == AchievementLevel.EXPLORADOR,
                    onClick = { selectedLevel = AchievementLevel.EXPLORADOR }
                ) {
                    Text(
                        text = "🧭 Explorador",
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        fontWeight = if (selectedLevel == AchievementLevel.EXPLORADOR) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Tab(
                    selected = selectedLevel == AchievementLevel.MAESTRO,
                    onClick = { selectedLevel = AchievementLevel.MAESTRO }
                ) {
                    Text(
                        text = "👑 Maestro",
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                        fontWeight = if (selectedLevel == AchievementLevel.MAESTRO) FontWeight.Bold else FontWeight.Normal
                    )
                }
                // NO incluir pestaña de SECRETO - solo aparecerán en "Todos" cuando estén desbloqueados
            }

            when (achievements) {
                is AchievementsUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is AchievementsUiState.Success -> {
                    val achievementsList = (achievements as AchievementsUiState.Success).achievements
                    val filteredAchievements = when (selectedLevel) {
                        null -> achievementsList
                            // En "Todos": mostrar logros secretos solo si están desbloqueados
                            .filter { it.level != AchievementLevel.SECRETO || it.isUnlocked }
                            .sortedWith(compareBy(
                                // Primero ordenar por estado: desbloqueados primero
                                { !it.isUnlocked },
                                // Luego por categoría: NOVATO, EXPLORADOR, MAESTRO, SECRETO
                                { achievement ->
                                    when (achievement.level) {
                                        AchievementLevel.NOVATO -> 1
                                        AchievementLevel.EXPLORADOR -> 2
                                        AchievementLevel.MAESTRO -> 3
                                        AchievementLevel.SECRETO -> 4
                                    }
                                },
                                // Finalmente por el valor del requisito
                                { 
                                    when (it.requirementType) {
                                        AchievementRequirementType.DISTANCE -> it.requiredDistance ?: 0.0
                                        AchievementRequirementType.TRIPS -> (it.requiredTrips ?: 0).toDouble()
                                        AchievementRequirementType.CONSECUTIVE_DAYS -> (it.requiredConsecutiveDays ?: 0).toDouble()
                                        AchievementRequirementType.UNIQUE_SCOOTERS -> (it.requiredUniqueScooters ?: 0).toDouble()
                                        AchievementRequirementType.LONGEST_TRIP -> it.requiredLongestTrip ?: 0.0
                                        AchievementRequirementType.UNIQUE_WEEKS -> (it.requiredUniqueWeeks ?: 0).toDouble()
                                        AchievementRequirementType.MAINTENANCE_COUNT -> (it.requiredMaintenanceCount ?: 0).toDouble()
                                        AchievementRequirementType.CO2_SAVED -> it.requiredCO2Saved ?: 0.0
                                        AchievementRequirementType.UNIQUE_MONTHS -> (it.requiredUniqueMonths ?: 0).toDouble()
                                        AchievementRequirementType.CONSECUTIVE_MONTHS -> (it.requiredConsecutiveMonths ?: 0).toDouble()
                                        AchievementRequirementType.ALL_OTHERS -> 999999.0
                                        AchievementRequirementType.MULTIPLE -> 0.0
                                    }
                                }
                            ))
                        else -> achievementsList
                            // En pestañas específicas: NO mostrar logros secretos nunca
                            .filter { it.level == selectedLevel && it.level != AchievementLevel.SECRETO }
                            .sortedWith(compareBy(
                                // Primero ordenar por estado: desbloqueados primero
                                { !it.isUnlocked },
                                // Luego por el valor del requisito (en pestañas específicas no hace falta ordenar por categoría)
                                { 
                                    when (it.requirementType) {
                                        AchievementRequirementType.DISTANCE -> it.requiredDistance ?: 0.0
                                        AchievementRequirementType.TRIPS -> (it.requiredTrips ?: 0).toDouble()
                                        AchievementRequirementType.CONSECUTIVE_DAYS -> (it.requiredConsecutiveDays ?: 0).toDouble()
                                        AchievementRequirementType.UNIQUE_SCOOTERS -> (it.requiredUniqueScooters ?: 0).toDouble()
                                        AchievementRequirementType.LONGEST_TRIP -> it.requiredLongestTrip ?: 0.0
                                        AchievementRequirementType.UNIQUE_WEEKS -> (it.requiredUniqueWeeks ?: 0).toDouble()
                                        AchievementRequirementType.MAINTENANCE_COUNT -> (it.requiredMaintenanceCount ?: 0).toDouble()
                                        AchievementRequirementType.CO2_SAVED -> it.requiredCO2Saved ?: 0.0
                                        AchievementRequirementType.UNIQUE_MONTHS -> (it.requiredUniqueMonths ?: 0).toDouble()
                                        AchievementRequirementType.CONSECUTIVE_MONTHS -> (it.requiredConsecutiveMonths ?: 0).toDouble()
                                        AchievementRequirementType.ALL_OTHERS -> 999999.0
                                        AchievementRequirementType.MULTIPLE -> 0.0
                                    }
                                }
                            ))
                    }

                    if (filteredAchievements.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = when (selectedLevel) {
                                    AchievementLevel.SECRETO -> "Los logros secretos están ocultos... 🤫"
                                    else -> "No hay logros disponibles en esta categoría"
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredAchievements) { achievement ->
                                AchievementCard(
                                    achievement = achievement,
                                    onShare = { viewModel.shareAchievement(achievement) }
                                )
                            }
                        }
                    }
                }
                is AchievementsUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (achievements as AchievementsUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(
    achievement: Achievement,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var showImageDialog by remember { mutableStateOf(false) }
    var imageLoadError by remember { mutableStateOf(false) }
    
    // Función para obtener el nombre del recurso de la imagen usando el ID del logro
    fun getAchievementImageResId(achievementId: String): Int {
        return try {
            // Intentar con el ID del logro (ej: achievement_novato_1)
            var resId = context.resources.getIdentifier(
                "achievement_$achievementId",
                "drawable",
                context.packageName
            )
            
            if (resId == 0) {
                // Intentar sin el prefijo de categoría (ej: achievement_1)
                val shortId = achievementId.substringAfterLast('_')
                resId = context.resources.getIdentifier(
                    "achievement_$shortId",
                    "drawable",
                    context.packageName
                )
            }
            
            if (resId == 0) {
                imageLoadError = true
                R.drawable.ic_achievement
            } else {
                resId
            }
        } catch (e: Exception) {
            imageLoadError = true
            R.drawable.ic_achievement
        }
    }
    
    if (showImageDialog) {
        AlertDialog(
            onDismissRequest = { 
                showImageDialog = false
                imageLoadError = false
            },
            title = { 
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Mostrar la imagen del logro
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (imageLoadError) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "Logro",
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Image(
                                painter = painterResource(id = getAchievementImageResId(achievement.id)),
                                contentDescription = achievement.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = achievement.description,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = achievement.shareMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImageDialog = false
                        imageLoadError = false
                        onShare()
                    }
                ) {
                    Text(
                        text = "Compartir",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showImageDialog = false
                        imageLoadError = false
                    }
                ) {
                    Text(
                        text = "Cerrar",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable(enabled = achievement.isUnlocked) { 
                showImageDialog = true 
            },
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) 
                MaterialTheme.colorScheme.surface 
            else 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (achievement.isUnlocked) 1.dp else 0.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mostrar emoji o imagen pequeña
            if (achievement.isUnlocked) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageLoadError) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "Logro",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    } else {
                        Image(
                            painter = painterResource(id = getAchievementImageResId(achievement.id)),
                            contentDescription = achievement.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Bloqueado",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                if (achievement.isUnlocked) {
                    Text(
                        text = "Toca para ver la imagen y compartir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { achievement.progress.toFloat() / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
} 