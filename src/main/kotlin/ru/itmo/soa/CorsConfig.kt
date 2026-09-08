package ru.itmo.soa

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {
    @Bean
    fun corsFilter(): CorsFilter {
        val configuration = CorsConfiguration().apply {
            // Reflect the requesting origin so cookies can also be used by browser clients.
            allowedOriginPatterns = listOf("*")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("Location")
            allowCredentials = true
            maxAge = 3600
        }

        return CorsFilter(UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        })
    }
}
