package com.example.obivapp2.utils

object UrlModifier {
    
    /**
     * Tente de modifier l'URL pour contourner les protections
     */
    fun createBypassUrl(originalUrl: String): String {
        return try {
            // Ajouter des paramètres qui peuvent désactiver certains scripts
            val separator = if (originalUrl.contains("?")) "&" else "?"
            val params = listOf(
                "no_banner=1",
                "bypass=true", 
                "mobile=1",
                "autoplay=1",
                "controls=1",
                "minimal=1"
            ).joinToString("&")
            
            "$originalUrl$separator$params"
        } catch (e: Exception) {
            originalUrl
        }
    }
    
    /**
     * Génère un lien avec User-Agent mobile pour éviter certaines protections
     */
    fun createMobileUrl(originalUrl: String): String {
        return createBypassUrl(originalUrl)
    }
    
    /**
     * Crée plusieurs variantes d'URL pour différents contournements
     */
    fun createUrlVariants(originalUrl: String): List<Pair<String, String>> {
        return listOf(
            "Lien original" to originalUrl,
            "Lien mobile" to createMobileUrl(originalUrl),
            "Lien minimal" to "$originalUrl${if (originalUrl.contains("?")) "&" else "?"}minimal=1&no_ads=1",
            "Lien direct" to originalUrl.replace("/iframe/", "/embed/")
        )
    }
} 