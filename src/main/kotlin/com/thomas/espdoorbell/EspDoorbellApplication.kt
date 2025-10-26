package com.thomas.espdoorbell

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EspDoorbellApplication

fun main(args: Array<String>) {
    runApplication<EspDoorbellApplication>(*args)
}
