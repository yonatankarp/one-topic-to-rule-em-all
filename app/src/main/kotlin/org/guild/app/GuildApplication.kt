package org.guild.app

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class GuildApplication

fun main(args: Array<String>) {
    runApplication<GuildApplication>(*args)
}
