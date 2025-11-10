package com.example.webviewfichaje.screens

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.*

/**
 * [PageEvent] es una clase sellada (sealed class) que define los eventos simples
 * que el WebView puede emitir.
 */
sealed class PageEvent {
    object Started : PageEvent()
    data class Finished(val url: String?) : PageEvent()
    data class Error(val description: String?, val failingUrl: String?) : PageEvent()
}

/**
 * [createConfiguredWebView] es la función principal que construye el WebView.
 * @param context Contexto de la aplicación.
 * @param onPageEvent Un callback opcional para notificar eventos a la capa superior (Compose).
 * @return Una instancia de WebView totalmente configurada.
 */
@SuppressLint("SetJavaScriptEnabled")
fun createConfiguredWebView(context: Context, onPageEvent: ((PageEvent) -> Unit)? = null): WebView {
    // 1. Crear la instancia nativa del WebView.
    val webView = WebView(context)

    // 2. Configurar los ajustes (WebSettings).
    val settings: WebSettings = webView.settings
    settings.javaScriptEnabled = true // Habilita la ejecución de JavaScript en la página.
    settings.domStorageEnabled = true // Permite usar almacenamiento local (localStorage) en el navegador.
    settings.allowContentAccess = true
    settings.loadsImagesAutomatically = true

    // 3. Implementar el WebViewClient para manejar eventos de la página.
    webView.webViewClient = object : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            onPageEvent?.invoke(PageEvent.Started) // Notificar inicio de carga.
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            try { view?.tag = null } catch (_: Exception) {} // Limpiamos el tag de fallo (si lo tenía).
            onPageEvent?.invoke(PageEvent.Finished(url)) // Notificar finalización de carga.
        }

        // Se llama cuando ocurre un error de carga (API >= M) en el frame principal.
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            super.onReceivedError(view, request, error)

            // CRÍTICO: Si el tag es "SKIP_ERROR_UI", la Activity está intentando recargar.
            // Ignoramos la carga del HTML de error para evitar el bucle de "Sin conexión".
            if (view?.tag == "SKIP_ERROR_UI") {
                val failing = request?.url?.toString()
                // Sobreescribimos el marcador con la URL fallida para un reintento futuro si es necesario.
                try { view.tag = failing } catch (_: Exception) {}
                onPageEvent?.invoke(PageEvent.Error(error?.description?.toString(), failing))
                return // Salimos sin cargar el HTML de error.
            }

            // Solo actuamos si el error es en el frame principal (la página que el usuario intenta ver).
            if (request?.isForMainFrame == true && error?.errorCode != ERROR_UNKNOWN) {
                val failing = request.url?.toString()
                try { view?.tag = failing } catch (_: Exception) {} // Guardamos la URL que falló en el tag del WebView.

                // HTML con la página de error "Sin conexión" (diseño responsivo simple).
                val html = """
            <html>
              <head>
                <meta name="viewport" content="width=device-width,initial-scale=1"/>
                <style>
                  /* Estilos para centrar el contenido y mejorar la estética */
                  body { font-family: 'Inter', sans-serif; display:flex; align-items:center; justify-content:center; height:100vh; margin:0; background-color: #f4f4f9; color: #333; }
                  .container { text-align:center; max-width:480px; padding:32px; border-radius:12px; background-color:#ffffff; box-shadow: 0 4px 8px rgba(0,0,0,0.1); }
                  h2 { color: #dc3545; margin-bottom: 10px; }
                  p { color: #6c757d; }
                  .icon { font-size: 60px; margin-bottom: 20px; display: block; color: #ffc107; }
                </style>
              </head>
              <body>
                <div class="container">
                  <span class="icon">&#9888;</span> <!-- Icono de advertencia -->
                  <h2>Sin conexión a Internet</h2>
                  <p>No se puede cargar la página web. Por favor, verifica tu conexión e inténtalo de nuevo.</p>
                  <p><small>La aplicación intentará reconectar automáticamente cuando la red esté disponible.</small></p>
                </div>
              </body>
            </html>
                """.trimIndent()

                // Cargamos el HTML de error en el WebView.
                view?.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                // Notificar el evento de error.
                onPageEvent?.invoke(PageEvent.Error(error?.description?.toString(), failing))
            }
        }
    }

    // 4. Implementar WebChromeClient para manejar elementos de la UI del navegador (como alertas, diálogos, etc.).
    webView.webChromeClient = WebChromeClient()

    return webView
}