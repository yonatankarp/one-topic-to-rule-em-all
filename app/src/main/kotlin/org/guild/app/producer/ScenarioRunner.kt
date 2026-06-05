package org.guild.app.producer

import org.apache.avro.specific.SpecificRecord
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ScenarioRunner(
    private val kafka: KafkaTemplate<String, Any>,
    private val router: EventRouter,
    @Value("\${guild.flood-size}") private val floodSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The scenario: flood the guild with registrations, then register our hero
     * and IMMEDIATELY level him up. In the broken topology the level-up overtakes
     * the registration because the registration consumer is drowning in backlog.
     */
    fun run(): String {
        repeat(floodSize) { i ->
            val id = UUID.randomUUID().toString()
            send(id, registered(id, randomName(i), randomClass(i)))
        }

        val heroId = UUID.randomUUID().toString()
        log.info("🐣 Producing the hero's lifecycle (id={})", heroId)
        send(heroId, registered(heroId, "Bob the Brave", "Fighter"))
        send(heroId, leveledUp(heroId, 2))
        send(heroId, questAccepted(heroId, "Slay the Rat King of Köpenick"))
        send(heroId, died(heroId, "rocks fell"))

        kafka.flush()
        log.info("🏁 Scenario produced: {} flood registrations + the hero's lifecycle", floodSize)
        return heroId
    }

    private fun send(key: String, event: SpecificRecord) {
        // runCatching catches only router.topicFor() throws (synchronous) — the 🤷 path.
        // Async broker failures surface via the future callback.
        runCatching { kafka.send(router.topicFor(event), key, event) }
            .onSuccess { future ->
                future.whenComplete { _, ex ->
                    if (ex != null) log.error("❌ send failed for {}: {}", event.schema.name, ex.message)
                }
            }
            .onFailure { log.warn("🤷 {} has nowhere to go in this topology", event.schema.name) }
    }
}
