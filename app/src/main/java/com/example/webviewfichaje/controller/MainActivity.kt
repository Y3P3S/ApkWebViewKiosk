package com.example.webviewfichaje.controller

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.example.webviewfichaje.model.ConfigRepository
import com.example.webviewfichaje.screens.WebViewScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.concurrent.atomic.AtomicLong

/**
 * [MainActivity] actúa como el controlador (Controller) principal de la aplicación.
 * Gestiona el estado de inactividad, la conectividad de red y la orquestación del WebView.
 */
class MainActivity : ComponentActivity() {

    // --- Estado / referencias ---

    // Marca de tiempo atómica (thread-safe) del último evento de interacción del usuario (touch/teclado).
    private val lastInteractionMillis = AtomicLong(System.currentTimeMillis())

    // Referencia al WebView: se inicializa cuando Compose lo crea.
    private var webViewRef: WebView? = null

    // Configuración de la app, cargada desde el repositorio.
    private lateinit var config: com.example.webviewfichaje.model.AppConfig

    // Estado de Compose para controlar la visibilidad del overlay de carga (MutableState es observable).
    private val showOverlayState = mutableStateOf(false)

    // Servicio de conectividad de red.
    private lateinit var connectivityManager: ConnectivityManager

    // Callback de red: se dispara cuando una red con capacidad de Internet está disponible (vuelve la conexión).
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val wv = webViewRef
            if (wv != null) {
                // Ejecutar la lógica de recarga en el hilo principal de Android (UI Thread).
                runOnUiThread {
                    showOverlayState.value = true // Mostrar el overlay de carga.

                    // CRÍTICO: Limpia el contenido actual (pantalla de error) para evitar el parpadeo.
                    // Esto fuerza una pantalla limpia (negra/blanca) mientras está el spinner.
                    wv.loadData("<html><head><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/></head><body style='background:#111'></body></html>", "text/html", "utf-8")

                    // 1. Establecemos el marcador "SKIP_ERROR_UI".
                    wv.tag = "SKIP_ERROR_UI"

                    lifecycleScope.launch {
                        try {
                            // Damos un tiempo breve para que el marcador sea visible en WebViewFactory.
                            delay(100L)

                            // CRÍTICO: Siempre recargar la URL objetivo (targetUrl) en modo Kiosco
                            // para asegurar el regreso a la página principal, copiando el comportamiento
                            // del monitor de inactividad que funciona.
                            withContext(Dispatchers.Main) { wv.loadUrl(config.targetUrl) }

                            // 2. Limpiamos el marcador después de que la carga se haya iniciado.
                            delay(200L)
                            withContext(Dispatchers.Main) {
                                if (wv.tag == "SKIP_ERROR_UI") {
                                    wv.tag = null
                                }
                            }

                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            // Esperar un breve momento para que la carga termine visualmente (o falle).
                            delay(900L)
                            withContext(Dispatchers.Main) { showOverlayState.value = false } // Ocultar el overlay.
                        }
                    }
                }
            }
        }
    }

    // Función auxiliar para verificar el estado de la red.
    private fun isNetworkConnected(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    // --- Ciclo de vida ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        config = ConfigRepository.getConfig() // Cargar configuración.

        // Mantener la pantalla encendida (modo kiosk).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        enterImmersiveMode() // Aplicar modo inmersivo para ocultar barras de sistema.

        // Inicializar el ConnectivityManager.
        connectivityManager = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // --- UI: Compose ---
        setContent {
            BackHandler { /* no-op */ } // Ignorar el botón de atrás.

            // Determinar la URL inicial.
            val initialUrl = config.targetUrl

            WebViewScreen(
                targetUrl = initialUrl,
                showOverlayState = showOverlayState,
                onWebViewCreated = { wv -> webViewRef = wv }
            )
        }

        // --- Registrar el callback de red ---
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Iniciar el monitor de inactividad en una corrutina.
        startSimpleIdleMonitor()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        // Actualiza la marca de tiempo de la última interacción para resetear el monitor de inactividad.
        lastInteractionMillis.set(System.currentTimeMillis())
    }

    override fun onDestroy() {
        super.onDestroy()
        // Desregistrar callback de red para prevenir memory leaks.
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) { /* ignorar */ }

        // Limpiar y destruir el WebView.
        webViewRef?.apply {
            try {
                clearHistory()
                removeAllViews()
                destroy()
            } catch (_: Exception) { /* ignorar */ }
        }
        webViewRef = null
    }

    // -------------------
    // Monitor simple de inactividad
    // -------------------
    private fun startSimpleIdleMonitor() {
        // Parsear la URL objetivo (target) para hacer una comparación segura.
        val targetUri = try { URI(config.targetUrl) } catch (_: Exception) { null }

        lifecycleScope.launch {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val last = lastInteractionMillis.get()
                    val idleFor = now - last

                    // Si la inactividad supera el umbral...
                    if (idleFor >= config.idleTimeoutMs) {
                        val wv = webViewRef
                        if (wv != null) {
                            val currentUrl = withContext(Dispatchers.Main) { wv.url }

                            // Recarga necesaria si: 1) La URL actual es nula, O 2) Es distinta a la URL target.
                            val needsReload = if (targetUri == null) {
                                false
                            } else {
                                (currentUrl.isNullOrEmpty()) || !currentUrl.startsWith(targetUri.toString())
                            }

                            if (needsReload) {
                                withContext(Dispatchers.Main) { showOverlayState.value = true } // Mostrar overlay.

                                try {
                                    withContext(Dispatchers.Main) {
                                        wv.loadUrl(config.targetUrl) // Cargar la URL target.
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                delay(1000L) // Espera corta.

                                withContext(Dispatchers.Main) { showOverlayState.value = false } // Ocultar overlay.

                                // Resetear el tiempo de inactividad.
                                lastInteractionMillis.set(System.currentTimeMillis())
                            }
                        }
                    }

                    // Esperar antes de la siguiente comprobación.
                    delay(config.checkPeriodMs)
                } catch (e: Exception) {
                    e.printStackTrace()
                    delay(2_000L)
                }
            }
        }
    }

    // -------------------
    // Helpers simples (Modo Inmersivo)
    // -------------------

    // Oculta las barras de sistema (status bar y navigation bar) para una experiencia a pantalla completa.
    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // El modo inmersivo permanece si se desliza una barra.
                        or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN // Oculta la status bar.
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // Oculta la navigation bar.
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION // Permite que el contenido se extienda bajo la navigation bar.
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN // Permite que el contenido se extienda bajo la status bar.
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE // Ayuda a prevenir que el contenido salte al cambiar la visibilidad de las barras.
                )
    }
}