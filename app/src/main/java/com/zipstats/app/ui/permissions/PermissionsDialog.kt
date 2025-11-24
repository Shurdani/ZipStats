package com.zipstats.app.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zipstats.app.permission.AppPermission
import com.zipstats.app.ui.components.DialogNeutralButton
import com.zipstats.app.ui.components.DialogSaveButton


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
            DialogSaveButton(
                text = "Entendido",
                onClick = onConfirm
            )
        },
        dismissButton = {
            DialogNeutralButton(
                text = "Cancelar",
                onClick = onDismiss
            )
        }
    )
}

@Composable
private fun getPermissionIcon(permission: String): androidx.compose.ui.graphics.vector.ImageVector {
    // Solo manejamos los permisos que realmente solicitamos
    return when {
        permission.contains("LOCATION") -> Icons.Default.LocationOn
        permission.contains("NOTIFICATION") || permission.contains("POST_NOTIFICATIONS") -> Icons.Default.Notifications
        permission.contains("CAMERA") -> Icons.Default.Camera
        else -> Icons.Default.Info // Fallback
    }
}

@Composable
private fun getPermissionIconColor(permission: String): androidx.compose.ui.graphics.Color {
    return when {
        permission.contains("LOCATION") -> MaterialTheme.colorScheme.error
        permission.contains("NOTIFICATION") || permission.contains("POST_NOTIFICATIONS") -> androidx.compose.ui.graphics.Color(0xFFFFC107) // Amarillo/Naranja para Notificaciones
        permission.contains("CAMERA") -> androidx.compose.ui.graphics.Color(0xFF2196F3) // Azul para Cámara
        else -> MaterialTheme.colorScheme.primary
    }
}

private fun getPermissionShortName(permission: String): String {
    return when {
        permission.contains("LOCATION") -> "Ubicación"
        permission.contains("NOTIFICATION") || permission.contains("POST_NOTIFICATIONS") -> "Notificaciones"
        permission.contains("CAMERA") -> "Cámara"
        else -> "Permiso"
    }
}