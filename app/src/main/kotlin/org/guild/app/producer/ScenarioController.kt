package org.guild.app.producer

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ScenarioController(private val runner: ScenarioRunner) {

    @PostMapping("/scenario/run")
    fun run(): Map<String, String> = mapOf("heroId" to runner.run())
}
