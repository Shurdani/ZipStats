package com.example.patineta.ui.records

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.patineta.model.Record

@Composable
fun RecordItem(
    record: Record,
    onDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = record.patinete,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Fecha: ${record.fecha}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Kilometraje: ${record.kilometraje} km",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Diferencia: +${record.diferencia} km",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (record.diferencia > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Solo mostrar el botón de eliminar si no es un registro inicial
            if (!record.isInitialRecord) {
                IconButton(
                    onClick = { onDelete(record.id) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar registro",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
} 