package com.zipstats.app.ui.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.zipstats.app.R
import com.zipstats.app.model.Achievement
import com.zipstats.app.model.AchievementLevel
import com.zipstats.app.model.AchievementRequirementType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: AchievementsViewModel = hiltViewModel(),
    @Suppress("UNUSED_PARAMETER") navController: NavHostController
) {
    val achievements by viewModel.achievements.collectAsState()
    var selectedLevel by remember { mutableStateOf<AchievementLevel?>(null) }

    // Usamos GridState para el scroll en la cuadrícula
    val gridState = rememberLazyGridState()

    val lastUnlockedAchievementId by viewModel.lastUnlockedAchievementId.collectAsState()
    val shouldRefreshAchievements by viewModel.shouldRefreshAchievements.collectAsState()

    // Estado para el diálogo de detalle (ahora se maneja aquí para simplificar)
    var selectedAchievementForDialog by remember { mutableStateOf<Achievement?>(null) }

    LaunchedEffect(shouldRefreshAchievements) {
        if (shouldRefreshAchievements > 0) {
            viewModel.loadAchievements()
        }
    }

    // Scroll automático al logro desbloqueado
    LaunchedEffect(lastUnlockedAchievementId) {
        lastUnlockedAchievementId?.let { achievementId ->
            if (achievements is AchievementsUiState.Success) {
                val achievementsList = (achievements as AchievementsUiState.Success).achievements
                val filteredList = filterAndSortAchievements(achievementsList, selectedLevel)

                val index = filteredList.indexOfFirst { it.id == achievementId }
                if (index >= 0) {
                    gridState.animateScrollToItem(index)
                }
            }
            viewModel.clearLastUnlockedAchievementId()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Logros", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, // Moderno (Surface)
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // --- NUEVO: FILTROS CON CHIPS (Scroll Horizontal) ---
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedLevel == null,
                        onClick = { selectedLevel = null },
                        label = { Text("Todos") },
                        leadingIcon = if (selectedLevel == null) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
                item {
                    FilterChip(
                        selected = selectedLevel == AchievementLevel.NOVATO,
                        onClick = { selectedLevel = AchievementLevel.NOVATO },
                        label = { Text("🔰 Novato") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedLevel == AchievementLevel.EXPLORADOR,
                        onClick = { selectedLevel = AchievementLevel.EXPLORADOR },
                        label = { Text("🧭 Explorador") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedLevel == AchievementLevel.MAESTRO,
                        onClick = { selectedLevel = AchievementLevel.MAESTRO },
                        label = { Text("👑 Maestro") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }

            when (achievements) {
                is AchievementsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AchievementsUiState.Success -> {
                    val achievementsList = (achievements as AchievementsUiState.Success).achievements
                    // Usamos la función auxiliar para filtrar
                    val filteredAchievements = filterAndSortAchievements(achievementsList, selectedLevel)

                    if (filteredAchievements.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No hay logros en esta categoría",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // --- NUEVO: GRID VERTICAL (2 Columnas) ---
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 150.dp), // Adaptable
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredAchievements) { achievement ->
                                AchievementGridCard(
                                    achievement = achievement,
                                    onClick = {
                                        // Abrir diálogo si está desbloqueado o si quieres permitir ver info de bloqueados
                                        // Aquí permitimos ver info de todos, pero solo compartir los desbloqueados
                                        selectedAchievementForDialog = achievement
                                    }
                                )
                            }
                        }
                    }
                }
                is AchievementsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = (achievements as AchievementsUiState.Error).message,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    // Diálogo de Detalle (Modal)
    selectedAchievementForDialog?.let { achievement ->
        AchievementDetailDialog(
            achievement = achievement,
            onDismiss = { selectedAchievementForDialog = null },
            onShare = {
                viewModel.shareAchievement(achievement)
                // Opcional: cerrar diálogo al compartir
                // selectedAchievementForDialog = null
            }
        )
    }
}

/**
 * Lógica de filtrado y ordenación extraída para reutilizar
 */
private fun filterAndSortAchievements(
    list: List<Achievement>,
    level: AchievementLevel?
): List<Achievement> {
    return when (level) {
        null -> list
            // En "Todos": mostrar secretos solo si desbloqueados
            .filter { it.level != AchievementLevel.SECRETO || it.isUnlocked }
            .sortedWith(compareBy(
                { !it.isUnlocked }, // Desbloqueados primero
                {
                    when (it.level) {
                        AchievementLevel.NOVATO -> 1
                        AchievementLevel.EXPLORADOR -> 2
                        AchievementLevel.MAESTRO -> 3
                        AchievementLevel.SECRETO -> 4
                    }
                },
                { getRequirementValue(it) }
            ))
        else -> list
            .filter { it.level == level && it.level != AchievementLevel.SECRETO }
            .sortedWith(compareBy(
                { !it.isUnlocked },
                { getRequirementValue(it) }
            ))
    }
}

private fun getRequirementValue(it: Achievement): Double {
    return when (it.requirementType) {
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

@Composable
private fun AchievementGridCard(
    achievement: Achievement,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val isUnlocked = achievement.isUnlocked

    // Función para obtener recurso de imagen (igual que antes)
    val imageResId = remember(achievement.id) {
        try {
            var resId = context.resources.getIdentifier("achievement_${achievement.id}", "drawable", context.packageName)
            if (resId == 0) {
                val shortId = achievement.id.substringAfterLast('_')
                resId = context.resources.getIdentifier("achievement_$shortId", "drawable", context.packageName)
            }
            if (resId == 0) R.drawable.ic_achievement else resId
        } catch (e: Exception) {
            R.drawable.ic_achievement
        }
    }

    // Colores según estado
    val containerColor = if (isUnlocked)
        MaterialTheme.colorScheme.surface
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    val textColor = if (isUnlocked)
        MaterialTheme.colorScheme.onSurface
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp), // Altura fija para uniformidad en el grid
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnlocked) 2.dp else 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. IMAGEN MEDALLA
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f) // Que la imagen ocupe el espacio flexible
            ) {
                // Círculo de fondo para la medalla
                Surface(
                    shape = CircleShape,
                    color = if (isUnlocked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = imageResId),
                            contentDescription = null,
                            modifier = Modifier.size(70.dp), // Medalla grande
                            contentScale = ContentScale.Fit,
                            // Si está bloqueado, lo ponemos en blanco y negro o con alfa
                            alpha = if (isUnlocked) 1f else 0.3f,
                            colorFilter = if (!isUnlocked) ColorFilter.tint(Color.Gray) else null
                        )

                        // Candado superpuesto si está bloqueado
                        if (!isUnlocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Bloqueado",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 2. TEXTOS
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Barra de progreso
                val progress = (achievement.progress.toFloat() / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.8f) // No ocupar todo el ancho
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Texto de progreso (Opcional, si quieres que se vea el %)
                if (!isUnlocked) {
                    // FIX: Formatear a 1 decimal máximo para evitar números largos (ej: 58.1%)
                    Text(
                        text = "${String.format("%.1f", achievement.progress)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Completado",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementDetailDialog(
    achievement: Achievement,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current

    // Recalcular recurso de imagen (reutilizando lógica)
    val imageResId = remember(achievement.id) {
        try {
            var resId = context.resources.getIdentifier("achievement_${achievement.id}", "drawable", context.packageName)
            if (resId == 0) {
                val shortId = achievement.id.substringAfterLast('_')
                resId = context.resources.getIdentifier("achievement_$shortId", "drawable", context.packageName)
            }
            if (resId == 0) R.drawable.ic_achievement else resId
        } catch (e: Exception) {
            R.drawable.ic_achievement
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Título Grande
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                // La Medalla Gigante
                // Aquí es donde iría tu imagen circular grande (image_e1e6be.jpg)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.size(160.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(80.dp)
                        )
                        // Si está bloqueado, filtro B/N
                        if (!achievement.isUnlocked) {
                            // Simular filtro gris superpuesto
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.White.copy(alpha = 0.6f))
                            )
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Descripción e Historia
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Estado / Progreso
                if (!achievement.isUnlocked) {
                    // FIX: Formatear a 1 decimal máximo
                    Text(
                        text = "Progreso: ${String.format("%.1f", achievement.progress)}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                } else {
                    Text(
                        text = "¡Conseguido! 🎉",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Botones de Acción
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cerrar")
                    }

                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        enabled = achievement.isUnlocked // Solo compartir si lo tienes
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Compartir")
                    }
                }
            }
        }
    }
}