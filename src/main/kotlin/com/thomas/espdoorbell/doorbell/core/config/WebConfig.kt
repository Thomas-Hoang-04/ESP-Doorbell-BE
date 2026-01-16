package com.thomas.espdoorbell.doorbell.core.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.config.ResourceHandlerRegistry
import org.springframework.web.reactive.config.WebFluxConfigurer
import java.nio.file.Paths

@Configuration
class WebConfig(
    private val appProperties: AppProperties
) : WebFluxConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        val absolutePath = Paths.get(appProperties.storage.uploadDir).toAbsolutePath().toUri().toString()
        
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations(absolutePath)
    }
}
