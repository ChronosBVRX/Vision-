package com.chronosbvrx.visionplus.ui.games

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronosbvrx.visionplus.ui.theme.VisionBlack
import com.chronosbvrx.visionplus.ui.theme.VisionBlue
import com.chronosbvrx.visionplus.ui.theme.VisionSurface
import com.chronosbvrx.visionplus.ui.theme.VisionSurfaceHigh
import com.chronosbvrx.visionplus.ui.theme.VisionTextPrimary
import com.chronosbvrx.visionplus.ui.theme.VisionTextSecondary
import com.google.zxing.integration.android.IntentIntegrator

/**
 * En el teléfono, Juegos no es un catálogo: la tele lleva la sala y el juego.
 * El celular solo sirve para entrar por QR o por código y convertirse en mando.
 */
@Composable
fun PartyMobileScreen(onBack: () -> Unit, onOpenController: (String) -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    fun openController(roomCode: String) {
        val normalized = roomCode.trim().uppercase().take(4)
        if (normalized.length != 4) {
            message = "Escribe un código de 4 caracteres."
            return
        }
        message = "Sala $normalized lista. Abriendo mando…"
        onOpenController(normalized)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scan = IntentIntegrator.parseActivityResult(result.resultCode, result.data)
        val scanned = scan?.contents?.trim()?.uppercase()?.removePrefix("VISIONPLUS://PARTY/")?.removePrefix("VISIONPLUS://PARTY")
        if (!scanned.isNullOrBlank()) {
            code = scanned.take(4)
            openController(code)
        } else {
            message = "No se encontró un código válido."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VisionBlack)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Juegos", color = VisionTextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "La tele lleva el juego. Tu celular es el mando: escanea el QR o escribe el código de la sala.",
            color = VisionTextSecondary,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Button(
            onClick = { launcher.launch(IntentIntegrator(context as Activity).createScanIntent()) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text("Escanear código QR", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(4) },
            label = { Text("Código de sala") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { openController(code) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Abrir mando", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        if (message.isNotBlank()) {
            Text(message, color = VisionBlue, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Text(
            "No se necesita catálogo en el teléfono: elige el juego en la tele y juega desde aquí.",
            color = VisionTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
