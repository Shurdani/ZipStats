package com.zipstats.app.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import javax.inject.Inject

data class AppPermission(
    val permission: String,
    val name: String,
    val description: String,
    val isRequired: Boolean = true
)

class PermissionManager @Inject constructor(
    private val context: Context
) {
    /**
     * Obtiene todos los permisos de la app con sus descripciones
     * NOTA: Al tener minSdk 31+, los permisos de almacenamiento/galería ya no son necesarios
     * para la selección de medios (PickVisualMedia) ni para la exportación de archivos (MediaStore).
     * Solo la ubicación, cámara y notificaciones requieren manejo explícito en tiempo de ejecución.
     */
    fun getAllPermissions(): List<AppPermission> {
        val permissions = mutableListOf<AppPermission>()

        // Permiso de ubicación (solo FINE_LOCATION, ya que incluye COARSE)
        permissions.add(
            AppPermission(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                name = "Ubicación",
                description = "Necesario para el seguimiento GPS de rutas y calcular distancias recorridas"
            )
        )

        // Permiso de notificaciones
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(
                AppPermission(
                    permission = Manifest.permission.POST_NOTIFICATIONS,
                    name = "Notificaciones",
                    description = "Necesario para mostrar notificaciones de logros y el seguimiento GPS en segundo plano"
                )
            )
        }

        // Permiso de cámara
        permissions.add(
            AppPermission(
                permission = Manifest.permission.CAMERA,
                name = "Cámara",
                description = "Necesario para tomar fotos de perfil"
            )
        )

        // Permisos de servicios en primer plano para grabación de pantalla (Android 14+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(
                AppPermission(
                    permission = Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION,
                    name = "Grabación de pantalla",
                    description = "Necesario para grabar la pantalla al guardar vídeos de rutas animadas",
                    isRequired = false
                )
            )
        }

        return permissions
    }

    /**
     * Obtiene todos los permisos que se deben solicitar al inicio
     */
    fun getRequiredStartupPermissions(): Array<String> {
        val permissions = getAllPermissions()
            .filter { it.isRequired }
            .map { it.permission }
            .toMutableList()

        // Asegurar que se incluyan ambos permisos de ubicación (aunque solo mostremos uno en el diálogo)
        if (Manifest.permission.ACCESS_FINE_LOCATION in permissions &&
            Manifest.permission.ACCESS_COARSE_LOCATION !in permissions) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        return permissions.toTypedArray()
    }

    /**
     * Verifica si todos los permisos requeridos están concedidos
     */
    fun hasAllRequiredPermissions(): Boolean {
        return getAllPermissions()
            .filter { it.isRequired }
            .all { hasPermission(it.permission) }
    }

    /**
     * Verifica si un permiso específico está concedido
     * Nota: Los permisos de foreground service (FOREGROUND_SERVICE_*) no se pueden verificar
     * con checkSelfPermission, se otorgan automáticamente si están en el manifest
     */
    fun hasPermission(permission: String): Boolean {
        // Los permisos de foreground service se otorgan automáticamente si están en el manifest
        if (permission.contains("FOREGROUND_SERVICE_")) {
            return true // Se asume que están concedidos si están en el manifest
        }
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Obtiene el estado de todos los permisos
     */
    fun getPermissionStates(): Map<String, Boolean> {
        return getAllPermissions().associate { it.permission to hasPermission(it.permission) }
    }

    // Funciones de permisos de almacenamiento eliminadas ya que minSdk 31+ garantiza el uso de MediaStore
    // y PickVisualMedia no requiere permiso de lectura en tiempo de ejecución.

    /**
     * Verifica si la app tiene permisos de ubicación
     */
    fun hasLocationPermissions(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /**
     * Obtiene los permisos de ubicación necesarios
     */
    fun getLocationPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    /**
     * Verifica si tiene permiso de cámara
     */
    fun hasCameraPermission(): Boolean {
        return hasPermission(Manifest.permission.CAMERA)
    }

    /**
     * Verifica si tiene permiso de notificaciones (necesario para foreground service)
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // No se necesita en versiones anteriores
        }
    }
}