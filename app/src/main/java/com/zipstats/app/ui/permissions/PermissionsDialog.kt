package com.zipstats.app.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zipstats.app.permission.AppPermission
import android.Manifest

@Composable
fun PermissionsDialog(
    permissions: List<AppPermission>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Permisos necesarios",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Para que la app funcione correctamente, necesitamos los siguientes permisos:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                permissions.forEach { permission ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = getPermissionIcon(permission.permission),
                            contentDescription = null,
                            tint = getPermissionIconColor(permission.permission),
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${getPermissionShortName(permission.permission)}:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = permission.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Entendido")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun getPermissionIcon(permission: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        permission.contains("LOCATION") -> Icons.Default.LocationOn
        permission.contains("NOTIFICATION") || permission.contains("POST_NOTIFICATIONS") -> Icons.Default.Notifications
        permission.contains("CAMERA") -> Icons.Default.Camera
        permission.contains("MEDIA") || permission.contains("IMAGE") -> Icons.Default.Image
        else -> Icons.Default.Notifications
    }
}

@Composable
private fun getPermissionIconColor(permission: String): androidx.compose.ui.graphics.Color {
    return when {
        permission.contains("LOCATION") -> MaterialTheme.colorScheme.error
        permission.contains("NOTIFICATION") || permission.contains("POST_NOTIFICATIONS") -> androidx.compose.ui.graphics.Color(0xFFFFC107)
        permission.contains("CAMERA") -> androidx.compose.ui.graphics.Color(0xFF2196F3)
        permission.contains("MEDIA") || permission.contains("IMAGE") -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
        else -> MaterialTheme.colorScheme.primary
    }
}

private fun getPermissionShortName(permission: String): String {
    return when {
        permission.contains("LOCATION") -> "Ubicación"
        permission.contains("NOTIFICATION") || permission.contains("POST_NOTIFICATIONS") -> "Notificaciones"
        permission.contains("CAMERA") -> "Cámara"
        permission.contains("MEDIA_IMAGES") -> "Imágenes"
        permission.contains("MEDIA_DOCUMENTS") -> "Documentos"
        else -> "Permiso"
    }
}


