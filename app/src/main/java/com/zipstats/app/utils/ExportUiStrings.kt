package com.zipstats.app.utils

/**
 * Textos compartidos para exportaciones (Excel, PDF, GPX): notificaciones, snackbars y toasts.
 */
object ExportUiStrings {

    const val NOTIFICATION_CHANNEL_ID = "export_channel"

    /** Visible en Ajustes del sistema (Android O+) */
    const val NOTIFICATION_CHANNEL_NAME = "Exportaciones"

    const val NOTIFICATION_CHANNEL_DESCRIPTION =
        "Progreso y resultado al guardar archivos en Descargas"

    const val PROGRESS_SUBTITLE = "Preparando archivo…"

    const val PROGRESS_TITLE_EXCEL = "Exportando archivo Excel"
    const val PROGRESS_TITLE_PDF = "Exportando informe PDF"
    const val PROGRESS_TITLE_GPX = "Exportando ruta GPX"

    const val COMPLETION_TITLE = "Exportación completada"

    fun savedToDownloadsRelative(fileName: String) = "Guardado en Descargas/$fileName"

    const val EXPORT_WITHOUT_NOTIFICATION_PERMISSION =
        "Exportando archivo. Para ver el progreso, concede permisos de notificación."

    const val ERROR_TEMP_FILE_INVALID = "Error: El archivo temporal no es válido"
    const val ERROR_CREATE_DOWNLOADS = "Error: No se pudo crear el archivo en Descargas"
    const val ERROR_STORAGE_PERMISSION = "Se necesita permiso de almacenamiento para exportar"
    const val ERROR_PDF_GENERATE = "No se pudo generar el informe PDF"
    const val ERROR_PDF_SAVE = "No se pudo guardar el informe PDF"

    const val ERROR_EXPORT_PREFIX = "Error al exportar: "
    const val ERROR_SAVE_PREFIX = "Error al guardar el archivo: "
}
