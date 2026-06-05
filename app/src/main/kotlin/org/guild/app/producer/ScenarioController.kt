package org.guild.app.producer

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

data class ScenarioResult(val heroId: String)

@RestController
class ScenarioController(private val runner: ScenarioRunner) {

    @PostMapping("/scenario/run")
    fun run(): ScenarioResult = ScenarioResult(runner.run())
}
