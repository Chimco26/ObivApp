package com.example.obivapp2.utils

object NetworkConfig {
    // Configuration des tentatives de reconnexion
    const val MAX_NETWORK_RETRIES = 5
    const val NETWORK_RETRY_DELAY_MS = 5000L // 5 secondes
    const val MAX_WAIT_FOR_NETWORK_ATTEMPTS = 60 // 5 minutes maximum
    
    // Timeouts pour les requêtes réseau
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L
    
    // Configuration des erreurs réseau
    val NETWORK_ERROR_KEYWORDS = listOf(
        "timeout",
        "connection",
        "network",
        "unreachable",
        "refused",
        "reset"
    )
    
    // Types d'erreurs réseau à retry
    val RETRYABLE_EXCEPTIONS = listOf(
        "java.net.SocketTimeoutException",
        "java.net.UnknownHostException",
        "java.net.ConnectException",
        "java.net.NoRouteToHostException"
    )
} 