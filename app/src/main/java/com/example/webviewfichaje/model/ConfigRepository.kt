package com.example.webviewfichaje.model

/**
 * Repositorio simple que actúa como fuente de verdad para la configuración.
 * Aquí podríamos leer de SharedPreferences o DataStore si queremos persistir,
 * pero ahora devolvemos valores por defecto (hard-coded).
 *
 * Mantener esta lógica en un repositorio facilita pruebas y cambios (separación de concerns).
 */
object ConfigRepository {

    // Valores por defecto (modifica aquí si quieres otro comportamiento)
    private const val DEFAULT_TARGET = "https://firepiping.factorialhr.es/employee_code_clock?token=eyJhbGciOiJIUzI1NiJ9.IjI1MjUyOCI.MJt7n-QtQiOf9elCZ5corWuetXyVSrDGRV8MU9pDJek&setting_id=107190"

    private const val DEFAULT_IDLE_MS = 45_000L
    private const val DEFAULT_CHECK_MS = 30_000L

    fun getConfig(): AppConfig {
        return AppConfig(
            targetUrl = DEFAULT_TARGET,
            idleTimeoutMs = DEFAULT_IDLE_MS,
            checkPeriodMs = DEFAULT_CHECK_MS
        )
    }
}
