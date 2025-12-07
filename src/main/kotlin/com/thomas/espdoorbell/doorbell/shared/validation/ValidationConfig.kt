package com.thomas.espdoorbell.doorbell.shared.validation

import com.thomas.espdoorbell.doorbell.shared.entity.BaseEntity
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback
import reactor.core.publisher.Mono

@Configuration
class ValidationConfig {

    @Bean
    fun validation(): BeforeConvertCallback<BaseEntity>
        = BeforeConvertCallback { entity, _ ->
            entity.validate()
            Mono.just(entity)
        }
}