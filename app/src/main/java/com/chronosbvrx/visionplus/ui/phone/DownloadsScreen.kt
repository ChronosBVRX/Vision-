package com.chronosbvrx.visionplus.ui.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.NetworkMonitor
import com.chronosbvrx.visionplus.data.offline.OfflineDownload
import com.chronosbvrx.visionplus.data.offline.OfflineDownloadManager
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.data.offline.OfflineStoragePolicy
import com.chronosbvrx.visionplus.ui.brand.VisionWordmark
import com.chronosbvrx.visionplus.ui.components.EmptyState
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * Pantalla de Descargas y Modo Especial Sin Conexión para Teléfonos y Tablets:
 *
 * - Cuando [isOfflineMode] es `true`, presenta la Interfaz Especial Sin Conexión ("Vision+ Offline")
 *   con cabecera dedicada, estado de red y acceso inmediato a todas las películas y series guardadas.
 * - Muestra progreso real byte a byte para descargas en curso, controles de pausa/reanudación
 *   y reproducción instantánea cifrada en hardware.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    isOfflineMode: Boolean = false,
    onRetryConnection: (() -> Unit)? = null,
    onOpenFilm: (Film) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    remember(context) {
        OfflineRegistry.init(context)
        true
    }
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()

    val activeVolume = remember(downloads) { OfflineStoragePolicy.getActiveVolume(context) }
    val usedBytes = remember(downloads) { OfflineStoragePolicy.getUsedVaultBytes(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isOfflineMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            VisionWordmark(height = 22.dp)
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFFFB74D).copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.WifiOff,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB74D),
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "OFFLINE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFFB74D),
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            "Descargas",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                },
                navigationIcon = {
                    if (!isOfflineMode && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                        }
                    }
                },
                actions = {
                    if (isOfflineMode) {
                        IconButton(
                            onClick = {
                                NetworkMonitor.refresh(context)
                                onRetryConnection?.invoke()
                            },
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reintentar conexión", tint = VisionBlue)
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
            if (isOfflineMode) {
                OfflineModeBannerCard(
                    completedCount = downloads.count { it.state == DownloadState.COMPLETED },
                    onRetry = {
                        NetworkMonitor.refresh(context)
                        onRetryConnection?.invoke()
                    },
                )
            }

            // Medidor visual de almacenamiento
            if (activeVolume != null) {
                StorageGaugeCard(
                    activeVolume = activeVolume,
                    usedVaultBytes = usedBytes,
                )
            }

            if (downloads.isEmpty()) {
                if (isOfflineMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = VisionBlue,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "Estás en Modo Sin Conexión",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Aún no tienes películas o series descargadas en este dispositivo. Conéctate a internet para explorar el catálogo o descargar contenido.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = VisionTextSecondary,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    NetworkMonitor.refresh(context)
                                    onRetryConnection?.invoke()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = VisionBlue),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Reintentar conexión", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    EmptyState(
                        title = "No tienes descargas aún",
                        subtitle = "Toca el botón «Descargar» en la ficha de cualquier película o serie para verla sin conexión a internet.",
                    )
                }
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
                            onPause = {
                                OfflineDownloadManager.pauseDownload(item.id, context)
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
private fun OfflineModeBannerCard(
    completedCount: Int,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF132238),
                        Color(0xFF1A2F4C),
                    ),
                ),
            )
            .border(1.dp, VisionBlue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(VisionBlue.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = VisionBlue,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Modo Sin Conexión · Vision+ Offline",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (completedCount > 0) {
                            "Sin internet · $completedCount ${if (completedCount == 1) "título disponible" else "títulos disponibles"} para ver ahora"
                        } else {
                            "No se detectó conexión a internet en tu dispositivo"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = VisionTextSecondary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = VisionBlue.copy(alpha = 0.22f),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = VisionBlue, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Reintentar conexión a internet",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
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
    onPause: () -> Unit,
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
                        model = coil.request.ImageRequest.Builder(LocalContext.current)
                            .data(item.posterUrl)
                            .crossfade(0)
                            .size(192, 288)
                            .build(),
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
                                "${item.formattedSize} · Listo para ver",
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
                                    if (item.bytesDownloaded > 0L) {
                                        "${OfflineStoragePolicy.formatBytes(item.bytesDownloaded)} / ${OfflineStoragePolicy.formatBytes(item.totalBytes)}"
                                    } else {
                                        "Conectando stream..."
                                    },
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
                            "Preparando descarga segura...",
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionBlue,
                        )
                    }
                    DownloadState.PAUSED -> {
                        Text(
                            "Descarga en pausa (${item.progressPercent}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFFFB74D),
                        )
                    }
                    DownloadState.ERROR -> {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    item.errorMessage ?: "Error en la descarga",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE53935),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(6.dp))

            // Controles de acción: Pausar, Reanudar/Reintentar, Eliminar
            if (item.state == DownloadState.DOWNLOADING) {
                IconButton(onClick = onPause) {
                    Icon(Icons.Default.Pause, contentDescription = "Pausar", tint = VisionBlue)
                }
            } else if (item.state == DownloadState.PAUSED || item.state == DownloadState.ERROR) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Download, contentDescription = "Reanudar", tint = VisionBlue)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar descarga", tint = VisionTextSecondary)
            }
        }
    }
}
