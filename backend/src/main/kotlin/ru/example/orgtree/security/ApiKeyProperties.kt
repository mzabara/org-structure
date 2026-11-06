package ru.example.orgtree.security

import org.springframework.boot.context.properties.ConfigurationProperties

/** Принимает ключи только из серверного окружения, не передавая их клиентскому приложению. */
@ConfigurationProperties("app.security")
data class ApiKeyProperties(val apiKeys: List<String> = emptyList())
