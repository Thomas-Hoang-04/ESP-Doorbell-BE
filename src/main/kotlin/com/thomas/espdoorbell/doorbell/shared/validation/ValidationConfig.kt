package com.thomas.espdoorbell.doorbell.shared.validation

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback
import reactor.core.publisher.Mono

@Configuration
class ValidationConfig {

    @Bean
    fun validation(): BeforeConvertCallback<Validatable>
        = BeforeConvertCallback { entity, _ ->
            entity.validate()
            Mono.just(entity)
        }
}