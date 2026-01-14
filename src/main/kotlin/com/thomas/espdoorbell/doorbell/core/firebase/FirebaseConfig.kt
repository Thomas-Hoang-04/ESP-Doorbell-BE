package com.thomas.espdoorbell.doorbell.core.firebase

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import java.io.File
import java.io.InputStream

@Configuration
class FirebaseConfig(private val environment: Environment) {

    @Bean
    fun firebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps().isNotEmpty()) {
            return FirebaseApp.getInstance()
        }

        val serviceAccount: InputStream = if (environment.activeProfiles.contains("dev")) {
            ClassPathResource("firebase/firebase-spring.json").inputStream
        } else {
            val credentialsPath = System.getenv("FIREBASE_CREDENTIALS_PATH")
                ?: "/app/config/firebase-spring.json"
            File(credentialsPath).inputStream()
        }

        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(serviceAccount))
            .build()

        return FirebaseApp.initializeApp(options)
    }

    @Bean
    fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging =
        FirebaseMessaging.getInstance(firebaseApp)
}
