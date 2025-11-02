package com.thomas.espdoorbell.doorbell.model.validation

import com.thomas.espdoorbell.doorbell.model.entity.base.BaseEntity
import com.thomas.espdoorbell.doorbell.model.entity.events.Notifications
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

    @Bean
    fun validateNotification(): BeforeConvertCallback<Notifications>
        = BeforeConvertCallback { entity, _ ->
            entity.validateRecipient()
            Mono.just(entity)
        }
}