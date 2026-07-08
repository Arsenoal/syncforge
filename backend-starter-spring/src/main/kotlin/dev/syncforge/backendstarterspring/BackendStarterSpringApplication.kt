package dev.syncforge.backendstarterspring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BackendStarterSpringApplication

fun main(args: Array<String>) {
    runApplication<BackendStarterSpringApplication>(*args)
}