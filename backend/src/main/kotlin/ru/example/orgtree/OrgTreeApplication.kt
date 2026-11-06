package ru.example.orgtree

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

/** Запускает API синтетической оргструктуры и управляет миграциями через Flyway. */
@SpringBootApplication
@ConfigurationPropertiesScan
class OrgTreeApplication

fun main(args: Array<String>) {
    runApplication<OrgTreeApplication>(*args)
}
