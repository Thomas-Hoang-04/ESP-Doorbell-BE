package com.thomas.espdoorbell

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
// TODO: Update system so that Android client will be the one to trigger the stream
class EspDoorbellApplication

fun main(args: Array<String>) {
    runApplication<EspDoorbellApplication>(*args)
}
