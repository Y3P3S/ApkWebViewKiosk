package com.example.webviewfichaje.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * [WebViewScreen] es el Composable principal que integra el WebView nativo y el overlay de carga.
 * @param targetUrl La URL que debe cargar inicialmente el WebView.
 * @param showOverlayState El estado observable para mostrar/ocultar el overlay de carga.
 * @param onWebViewCreated Callback que devuelve la instancia del WebView a la Activity (Controller).
 */
@Composable
fun WebViewScreen(
    targetUrl: String,
    showOverlayState: androidx.compose.runtime.MutableState<Boolean>,
    onWebViewCreated: (WebView) -> Unit
) {
    // 1. Estado para mantener la referencia al WebView una vez creado.
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // 2. Integración del WebView nativo en Compose.
        AndroidView(
            factory = { ctx ->
                // Crea el WebView usando la función de fábrica configurada.
                val wv = createConfiguredWebView(ctx).apply {
                    // Configura los parámetros de layout para ocupar todo el espacio.
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    loadUrl(targetUrl) // Carga la URL inicial.
                }
                webView = wv // Guarda la referencia.
                onWebViewCreated(wv) // Notifica a la Activity.
                wv // Devuelve la View nativa.
            },
            modifier = Modifier.fillMaxSize()
        )

        // 3. Overlay de carga animado (Spinner).
        AnimatedVisibility(
            visible = showOverlayState.value, // Visibilidad controlada por el estado de la Activity.
            enter = fadeIn(), // Animación de entrada: desvanecimiento.
            exit = fadeOut() // Animación de salida: desvanecimiento.
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE6000000)) // Fondo semi-transparente y oscuro (E6 = 90% opacidad).
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center) // Centra el contenido en el Box.
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color.White) // Muestra el spinner blanco.
                    Spacer(modifier = Modifier.height(8.dp)) // Espacio vertical.
                }
            }
        }
    }
}