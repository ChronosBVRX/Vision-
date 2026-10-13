package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.chronosbvrx.visionplus.data.Film
import com.chronosbvrx.visionplus.data.offline.DownloadState
import com.chronosbvrx.visionplus.data.offline.NetworkMonitor
import com.chronosbvrx.visionplus.data.offline.OfflineDownload
import com.chronosbvrx.visionplus.data.offline.OfflineDownloadManager
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.data.offline.OfflineStoragePolicy
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * Pantalla de descargas offline e Interfaz Especial Sin Conexión para Smart TV:
 *
 * Muestra el contenido guardado en la memoria USB conectada al televisor,
 * con navegación D-pad completa, banner de modo sin conexión, progreso en vivo
 * y reproducción directa sin internet.
 */
@Composable
fun TvDownloadsScreen(
    isOfflineMode: Boolean = false,
    onRetryConnection: (() -> Unit)? = null,
    onPlayFilm: (Film) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    remember(context) {
        OfflineRegistry.init(context)
        true
    }
    val landing = rememberLandingFocus("tv_downloads_landing")
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()

    val availableVolumes = remember(downloads) { OfflineStoragePolicy.getAvailableVolumes(context) }
    val externalVolume = availableVolumes.firstOrNull { it.isRemovable }
    val completedDownloads = downloads.filter { it.state == DownloadState.COMPLETED }
    val activeDownloads = downloads.filter { it.state != DownloadState.COMPLETED }

    BackHandler(enabled = !isOfflineMode) {
        onBack()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VisionBlack)
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        // Banner especial cuando la Smart TV no tiene conexión a internet
        if (isOfflineMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF132238), Color(0xFF1B3354)),
                        ),
                    )
                    .border(1.dp, VisionBlue.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                "MODO SIN CONEXIÓN · VISION+ OFFLINE",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Tu Smart TV no tiene conexión a internet. Reproduce tus películas y series guardadas en tu memoria USB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = VisionTextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Button(
                        onClick = {
                            NetworkMonitor.refresh(context)
                            onRetryConnection?.invoke()
                        },
                        colors = ButtonDefaults.colors(
                            containerColor = VisionBlue,
                            focusedContainerColor = Color.White,
                            contentColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                        modifier = if ( downloads.isEmpty() ) Modifier.landingFocus(landing) else Modifier,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reintentar conexión", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isOfflineMode) "Mis Descargas en USB" else "Descargas Offline",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(16.dp))

                // Indicador de almacenamiento USB
                if (externalVolume != null) {
                    Box(
                        modifier = Modifier
                            .background(VisionBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Usb,
                                contentDescription = null,
                                tint = VisionBlue,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "USB Conectada · ${externalVolume.usableFormatted} libres",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFE53935).copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "Sin memoria USB conectada",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFFF8A80),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Text(
                "${completedDownloads.size} títulos listos",
                style = MaterialTheme.typography.labelLarge,
                color = VisionTextSecondary,
            )
        }

        Spacer(Modifier.height(14.dp))

        // Descargas en curso (si existen)
        if (activeDownloads.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                activeDownloads.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(VisionSurface)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (item.subtitle.isNotBlank()) "${item.title} · ${item.subtitle}" else item.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(4.dp))
                            val statusText = when (item.state) {
                                DownloadState.DOWNLOADING -> "Descargando en USB: ${item.progressPercent}% (${OfflineStoragePolicy.formatBytes(item.bytesDownloaded)} / ${OfflineStoragePolicy.formatBytes(item.totalBytes)})"
                                DownloadState.QUEUED -> "Preparando flujo cifrado..."
                                DownloadState.PAUSED -> "En pausa (${item.progressPercent}%)"
                                DownloadState.ERROR -> item.errorMessage ?: "Error en la descarga"
                                DownloadState.COMPLETED -> "Completado"
                            }
                            Text(
                                statusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.state == DownloadState.ERROR) Color(0xFFFF8A80) else VisionBlue,
                            )
                            if (item.state == DownloadState.DOWNLOADING) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { item.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = VisionBlue,
                                    trackColor = Color(0xFF232D3F),
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.state == DownloadState.PAUSED || item.state == DownloadState.ERROR) {
                                Button(
                                    onClick = { OfflineDownloadManager.resumeDownload(context, item.id) },
                                    colors = ButtonDefaults.colors(
                                        containerColor = VisionBlue,
                                        focusedContainerColor = Color.White,
                                        contentColor = Color.White,
                                        focusedContentColor = Color.Black,
                                    ),
                                    modifier = if (completedDownloads.isEmpty() && idx == 0) Modifier.landingFocus(landing) else Modifier,
                                ) {
                                    Text("Reintentar", fontWeight = FontWeight.Bold)
                                }
                            }
                            Button(
                                onClick = { OfflineDownloadManager.deleteDownload(context, item.id) },
                                colors = ButtonDefaults.colors(
                                    containerColor = Color(0xFF263248),
                                    focusedContainerColor = Color(0xFFE53935),
                                    contentColor = Color.White,
                                    focusedContentColor = Color.White,
                                ),
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Cancelar", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // Si no hay descargas completadas ni en curso
        if (completedDownloads.isEmpty() && activeDownloads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Usb,
                        contentDescription = null,
                        tint = VisionTextSecondary,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (externalVolume != null) "No tienes descargas en tu memoria USB" else "Conecta una memoria USB para ver tus descargas",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Abre cualquier película o serie con una memoria USB conectada y pulsa «Descargar en USB».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = VisionTextSecondary,
                    )
                    if (!isOfflineMode) {
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.colors(
                                containerColor = VisionBlue,
                                focusedContainerColor = Color.White,
                                contentColor = Color.White,
                                focusedContentColor = Color.Black,
                            ),
                            modifier = Modifier.landingFocus(landing),
                        ) {
                            Text("Explorar catálogo", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return@Column
        }

        // Cuadrícula de títulos descargados
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            itemsIndexed(completedDownloads, key = { _, item -> item.id }) { index, item ->
                Column(Modifier.fillMaxWidth()) {
                    TvPosterCard(
                        title = item.title,
                        imageUrl = item.posterUrl,
                        score = 10.0,
                        badge = "OFFLINE",
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.landingFocus(landing) else Modifier),
                        onClick = { onPlayFilm(item.toFilm()) },
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            item.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = VisionBlue,
                            fontWeight = FontWeight.SemiBold,
                        )
                        // Botón de eliminar con mando
                        Button(
                            onClick = { OfflineDownloadManager.deleteDownload(context, item.id) },
                            colors = ButtonDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color(0xFFE53935),
                                contentColor = VisionTextSecondary,
                                focusedContentColor = Color.White,
                            ),
                            shape = ButtonDefaults.shape(RoundedCornerShape(4.dp)),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar de USB", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }
}
