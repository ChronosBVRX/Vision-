package com.chronosbvrx.visionplus.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.ui.PROJECT_URL
import com.chronosbvrx.visionplus.ui.brand.VisionWordmark
import com.chronosbvrx.visionplus.ui.openLink
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.chronosbvrx.visionplus.ui.tv.TvActionOption
import com.chronosbvrx.visionplus.ui.tv.landingFocus
import com.chronosbvrx.visionplus.ui.tv.rememberLandingFocus

internal const val ABOUT_TAGLINE = "Películas, series y TV en vivo, en tu celular y en tu Android TV."

internal const val ABOUT_CONTENT =
    "Vision+ es una plataforma de streaming y entretenimiento multimedia de alta fidelidad, " +
        "diseñada con interfaz nativa para Smart TV, Android TV y dispositivos móviles. " +
        "Desarrollada y mantenida por ChronosBVRX."

internal const val ABOUT_CREDITS =
    "Desarrollado por ChronosBVRX. Todos los derechos reservados sobre la marca y diseño de Vision+. " +
        "Los componentes de código abierto se rigen bajo sus respectivas licencias de software libre."

/** "Acerca de" on the phone: what this app is and where the source lives. */
@Composable
fun AboutScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = contentPadding.calculateTopPadding())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Acerca de", style = MaterialTheme.typography.headlineMedium)
            VisionWordmark(height = 40.dp, modifier = Modifier.padding(vertical = 8.dp))
            Text(ABOUT_TAGLINE, style = MaterialTheme.typography.bodyLarge, color = Color.White)
            Text(ABOUT_CONTENT, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
            Text(ABOUT_CREDITS, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
            Button(
                onClick = { openLink(context, PROJECT_URL) },
                colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Abrir el proyecto en GitHub") }
            Text(
                "Vision+ ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = VisionTextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
            )
        }
    }
}

/** "Acerca de" on the TV: the same text, read from the couch. */
@Composable
fun TvAboutScreen() {
    val context = LocalContext.current
    val landing = rememberLandingFocus()
    Row(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            VisionWordmark(height = 56.dp)
            androidx.tv.material3.Text(ABOUT_TAGLINE, style = androidx.tv.material3.MaterialTheme.typography.titleMedium, color = Color.White)
            androidx.tv.material3.Text(ABOUT_CONTENT, style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
            androidx.tv.material3.Text(ABOUT_CREDITS, style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
            androidx.tv.material3.Text("Vision+ ${BuildConfig.VERSION_NAME}", style = androidx.tv.material3.MaterialTheme.typography.titleSmall, color = Color.White)
            TvActionOption("Abrir el proyecto en GitHub", modifier = Modifier.landingFocus(landing)) { openLink(context, PROJECT_URL) }
        }
    }
}
