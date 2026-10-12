package com.chronosbvrx.visionplus.ui.tv

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.chronosbvrx.visionplus.data.offline.OfflineDownload
import com.chronosbvrx.visionplus.data.offline.OfflineDownloadManager
import com.chronosbvrx.visionplus.data.offline.OfflineRegistry
import com.chronosbvrx.visionplus.data.offline.OfflineStoragePolicy
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary

/**
 * Pantalla de descargas offline para Smart TV:
 *
 * Muestra el contenido guardado en la memoria USB conectada al televisor,
 * con navegación D-pad completa, estado del almacenamiento y reproducción directa sin internet.
 */
@Composable
fun TvDownloadsScreen(
    onPlayFilm: (Film) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val landing = rememberLandingFocus("tv_downloads_landing")
    val downloads by OfflineRegistry.downloadsFlow.collectAsState()

    val availableVolumes = remember { OfflineStoragePolicy.getAvailableVolumes(context) }
    val externalVolume = availableVolumes.firstOrNull { it.isRemovable }
    val completedDownloads = downloads.filter { it.state == DownloadState.COMPLETED }

    var selectedForDelete by remember { mutableStateOf<OfflineDownload?>(null) }

    BackHandler {
        if (selectedForDelete != null) {
            selectedForDelete = null
        } else {
            onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VisionBlack)
            .padding(horizontal = 48.dp, vertical = 28.dp),
    ) {
        // Encabezado
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Descargas Offline",
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
                "${completedDownloads.size} títulos guardados",
                style = MaterialTheme.typography.labelLarge,
                color = VisionTextSecondary,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Si no hay descargas
        if (completedDownloads.isEmpty()) {
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
                        if (externalVolume != null) "No tienes descargas en tu memoria USB" else "Conecta una memoria USB para descargar",
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
