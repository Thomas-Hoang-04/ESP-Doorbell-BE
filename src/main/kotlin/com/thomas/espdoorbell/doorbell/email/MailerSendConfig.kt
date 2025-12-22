package com.thomas.espdoorbell.doorbell.email

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "mailersend")
class MailerSendConfig {
    lateinit var apiKey: String
}