package com.chronosbvrx.visionplus.ui.about

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.BuildConfig
import com.chronosbvrx.visionplus.ui.PROJECT_URL
import com.chronosbvrx.visionplus.ui.brand.VisionWordmark
import com.chronosbvrx.visionplus.ui.openLink
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionBlueBright
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.chronosbvrx.visionplus.ui.tv.TvActionOption
import com.chronosbvrx.visionplus.ui.tv.TvCompactAction
import com.chronosbvrx.visionplus.ui.tv.landingFocus
import com.chronosbvrx.visionplus.ui.tv.rememberLandingFocus

internal const val ABOUT_TAGLINE = "Películas, series y TV en vivo, en tu celular y en tu Android TV."

internal const val ABOUT_STORY_LEAD = "Un estudio de una sola persona."

/** La historia del autor: la pista elegante del oficio, sin nombre ni hospital. */
internal const val ABOUT_STORY =
    "Vision+ nació en 2026 de una idea simple: las mejores historias no se compran, se revelan. " +
        "En mi trabajo paso los días mirando imágenes que nadie más alcanza a ver. Al salir, quise que " +
        "las historias se vieran tan bien como se merecen. No hay un gran estudio detrás: hay una " +
        "persona cuidando cada detalle, todos los días, después del trabajo."

internal const val ABOUT_STORY_CLOSE =
    "Si esta app te acompaña en la sala, en el sillón o de madrugada, ya valió todo."

internal const val ABOUT_CREDITS =
    "Una app de ChronosBVRX.\n" +
        "Con el cariño de mi familia y la confianza de mis amigos, los primeros en verla.\n" +
        "Para quienes me enseñaron a mirar con atención."

/** Mensaje del botón Compartir: firmado, con el enlace estable del APK. */
internal const val ABOUT_SHARE_MESSAGE =
    "Te comparto Vision+ — la app que hice. Cine, series, anime y TV en vivo, cuidada hasta el " +
        "último detalle. — ChronosBVRX\n" +
        "https://github.com/ChronosBVRX/Vision-/releases/latest/download/VisionPlus.apk"

/** Comparte el APK estable con el mensaje firmado del autor. */
internal fun shareApp(context: Context) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, ABOUT_SHARE_MESSAGE)
        }
        context.startActivity(Intent.createChooser(send, "Compartir Vision+"))
    }
}

/** La firma del autor: el nombre del estudio con su trazo y el sello de fundación. */
@Composable
internal fun AuthorSignature(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "ChronosBVRX",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            color = Color.White,
        )
        Canvas(Modifier.width(150.dp).height(10.dp)) {
            val path = Path().apply {
                moveTo(2f, size.height * 0.62f)
                cubicTo(
                    size.width * 0.16f, 0f,
                    size.width * 0.30f, size.height,
                    size.width * 0.48f, size.height * 0.38f,
                )
                cubicTo(
                    size.width * 0.64f, 0f,
                    size.width * 0.80f, size.height,
                    size.width - 2f, size.height * 0.24f,
                )
            }
            drawPath(path, VisionBlueBright, style = Stroke(width = 2.6f, cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .border(1.dp, VisionBlue.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp),
        ) {
            Text(
                "EST. 2026",
                style = MaterialTheme.typography.labelSmall,
                color = VisionBlueBright,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }
    }
}

/** "Acerca de" on the phone: what this app is, who makes it and where the source lives. */
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

            // El autor: la historia y la firma.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(VisionSurface, RoundedCornerShape(18.dp))
                    .border(1.dp, VisionBlue.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "EL AUTOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = VisionBlueBright,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Text(
                    ABOUT_STORY_LEAD,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                Text(ABOUT_STORY, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
                Text(ABOUT_STORY_CLOSE, style = MaterialTheme.typography.bodyMedium, color = VisionBlueBright)
                Spacer(Modifier.height(2.dp))
                AuthorSignature()
            }

            Text(ABOUT_CREDITS, style = MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)

            Button(
                onClick = { shareApp(context) },
                colors = ButtonDefaults.buttonColors(containerColor = VisionBlue, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Compartir Vision+") }
            Button(
                onClick = { openLink(context, PROJECT_URL) },
                colors = ButtonDefaults.buttonColors(containerColor = VisionSurface, contentColor = Color.White),
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

/** "Acerca de" on the TV: the same story, read from the couch. */
@Composable
fun TvAboutScreen() {
    val context = LocalContext.current
    val landing = rememberLandingFocus()
    Row(Modifier.fillMaxSize().padding(horizontal = 96.dp, vertical = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            VisionWordmark(height = 56.dp)
            androidx.tv.material3.Text(ABOUT_TAGLINE, style = androidx.tv.material3.MaterialTheme.typography.titleMedium, color = Color.White)
            androidx.tv.material3.Text(
                "EL AUTOR · $ABOUT_STORY_LEAD",
                style = androidx.tv.material3.MaterialTheme.typography.titleSmall,
                color = VisionBlueBright,
            )
            androidx.tv.material3.Text(ABOUT_STORY, style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.88f))
            androidx.tv.material3.Text(ABOUT_STORY_CLOSE, style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = VisionBlueBright)
            AuthorSignature()
            androidx.tv.material3.Text(ABOUT_CREDITS, style = androidx.tv.material3.MaterialTheme.typography.bodyMedium, color = VisionTextSecondary)
            androidx.tv.material3.Text("Vision+ ${BuildConfig.VERSION_NAME}", style = androidx.tv.material3.MaterialTheme.typography.titleSmall, color = Color.White)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvCompactAction(
                    label = "Compartir Vision+",
                    modifier = Modifier.landingFocus(landing),
                ) { shareApp(context) }
                TvActionOption("Abrir el proyecto en GitHub") { openLink(context, PROJECT_URL) }
            }
        }
    }
}
