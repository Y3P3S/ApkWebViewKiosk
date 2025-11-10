package com.example.webviewfichaje.model

/**
 * Data class que representa la configuración principal de la app.
 * En MVC el Model contiene los datos / reglas de negocio.
 */
data class AppConfig(
    val targetUrl: String,
    val idleTimeoutMs: Long,
    val checkPeriodMs: Long
)
