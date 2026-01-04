package com.zipstats.app.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zipstats.app.model.Avatars
import com.zipstats.app.ui.components.ZipStatsText

@Composable
fun AvatarPreview(
    avatarEmoji: String?,
    photoUrl: String?,
    userName: String,
    onTakePhoto: () -> Unit,
    onSelectImage: () -> Unit,
    onSelectAvatar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ZipStatsText(
                text = "Foto de Perfil",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            // Vista previa actual
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Imagen actual
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !photoUrl.isNullOrEmpty() -> {
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = "Foto de perfil",
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                            )
                        }
                        !avatarEmoji.isNullOrEmpty() -> {
                            ZipStatsText(
                                text = avatarEmoji,
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                        else -> {
                            ZipStatsText(
                                text = "👤",
                                style = MaterialTheme.typography.headlineLarge
                            )
                        }
                    }
                }
                
                // Información del usuario
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    ZipStatsText(
                        text = userName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    ZipStatsText(
                        text = when {
                            !photoUrl.isNullOrEmpty() -> "Foto personalizada"
                            !avatarEmoji.isNullOrEmpty() -> "Avatar: ${getAvatarName(avatarEmoji)}"
                            else -> "Sin foto de perfil"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                
                // Botón de editar
                IconButton(
                    onClick = { /* Mostrar opciones */ }
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Editar foto",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            HorizontalDivider()
            
            // Opciones de cambio
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZipStatsText(
                    text = "Cambiar foto:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón de cámara
                    OutlinedButton(
                        onClick = onTakePhoto,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ZipStatsText("Cámara")
                    }
                    
                    // Botón de galería
                    OutlinedButton(
                        onClick = onSelectImage,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        ZipStatsText("Galería")
                    }
                }
                
                // Botón de avatares
                TextButton(
                    onClick = onSelectAvatar,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ZipStatsText("Elegir Avatar")
                }
            }
        }
    }
}

private fun getAvatarName(emoji: String): String {
    return Avatars.list.find { it.emoji == emoji }?.name ?: "Avatar personalizado"
}
