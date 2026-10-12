package com.chronosbvrx.visionplus.ui.phone

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.OfflineDownload
import com.chronosbvrx.visionplus.data.offline.OfflineDownloadManager
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.data.offline.OfflineStoragePolicy
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * Pantalla de Descargas para Teléfonos y Tablets:
 *
 * Ofrece la experiencia premium de gestión offline estilo Netflix/Disney+:
 * - Gráfico visual de almacenamiento (Espacio Vision+ vs Libre).
 * - Lista de películas y series descargadas con calidad y peso.
 * - Progreso animado para descargas en curso.
 * - Reproducción inmediata en 0 ms sin internet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    onOpenFilm: (Film) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()

    val activeVolume = remember { OfflineStoragePolicy.getActiveVolume(context) }
    val usedBytes = remember(downloads) { OfflineStoragePolicy.getUsedVaultBytes(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Descargas",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = VisionBlack),
            )
        },
        containerColor = VisionBlack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Medidor visual de almacenamiento
            if (activeVolume != null) {
                StorageGaugeCard(
                    activeVolume = activeVolume,
                    usedVaultBytes = usedBytes,
                )
            }

            if (downloads.isEmpty()) {
                EmptyState(
                    title = "No tienes descargas aún",
                    subtitle = "Toca el botón «Descargar» en la ficha de cualquier película o serie para verla sin conexión a internet.",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(downloads, key = { it.id }) { item ->
                        DownloadItemCard(
                            item = item,
                            onClick = {
                                if (item.state == DownloadState.COMPLETED) {
                                    onOpenFilm(item.toFilm())
                                }
                            },
                            onDelete = {
                                OfflineDownloadManager.deleteDownload(context, item.id)
                            },
                            onRetry = {
                                OfflineDownloadManager.resumeDownload(context, item.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageGaugeCard(
    activeVolume: com.chronosbvrx.visionplus.data.offline.StorageVolumeInfo,
    usedVaultBytes: Long,
) {
    val total = if (activeVolume.totalBytes > 0) activeVolume.totalBytes else 1L
    val vaultFraction = (usedVaultBytes.toFloat() / total).coerceIn(0f, 1f)
    val freeFraction = (activeVolume.usableBytes.toFloat() / total).coerceIn(0f, 1f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = VisionSurface,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    activeVolume.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Libre: ${activeVolume.usableFormatted}",
                    style = MaterialTheme.typography.labelMedium,
                    color = VisionTextSecondary,
                )
            }

            Spacer(Modifier.height(10.dp))

            // Barra visual de almacenamiento
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF232D3F)),
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Porción ocupada por Vision+ (Azul)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(vaultFraction)
                            .height(8.dp)
                            .background(VisionBlue),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(VisionBlue),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Vision+: ${OfflineStoragePolicy.formatBytes(usedVaultBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }

                Text(
                    "Total: ${activeVolume.totalFormatted}",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: OfflineDownload,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = item.state == DownloadState.COMPLETED, onClick = onClick),
        color = VisionSurface,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Póster en miniatura
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 96.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161F30)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.posterUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                if (item.state == DownloadState.COMPLETED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(14.dp))

            // Información y estado
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionBlue,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(4.dp))

                when (item.state) {
                    DownloadState.COMPLETED -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(VisionBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    item.quality.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionBlue,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                item.formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                color = VisionTextSecondary,
                            )
                        }
                    }
                    DownloadState.DOWNLOADING -> {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Descargando ${item.progressPercent}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionBlue,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    item.formattedSize,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VisionTextSecondary,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { item.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = VisionBlue,
                                trackColor = Color(0xFF232D3F),
                            )
                        }
                    }
                    DownloadState.QUEUED -> {
                        Text(
                            "En cola de descarga...",
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionTextSecondary,
                        )
                    }
                    DownloadState.PAUSED -> {
                        Text(
                            "Descarga pausada",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB74D),
                        )
                    }
                    DownloadState.ERROR -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Error en la descarga",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFE53935),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Acción: Borrar o reintentar
            if (item.state == DownloadState.ERROR) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Download, contentDescription = "Reintentar", tint = VisionBlue)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar descarga", tint = VisionTextSecondary)
            }
        }
    }
}
