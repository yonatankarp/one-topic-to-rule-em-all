package org.guild.app.fixed

import org.guild.adventurer.AdventurerDied
import org.guild.adventurer.AdventurerLeveledUp
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

/**
 * A second, independent consumer of the SAME topic (own consumer group):
 * one-topic-per-aggregate does NOT couple consumers to each other.
 * The notice board only gossips about the interesting events.
 */
@Profile("fixed")
@Component
@KafkaListener(topics = ["guild.adventurers"], groupId = "tavern-notice-board")
class TavernNoticeBoard {

    private val log = LoggerFactory.getLogger(javaClass)
    private val _gossip = CopyOnWriteArrayList<String>()
    val gossip: List<String> get() = _gossip

    @KafkaHandler
    fun on(event: AdventurerLeveledUp) {
        post("🍺 Hear ye! ${event.adventurerId.take(8)}... is now level ${event.newLevel}!")
    }

    @KafkaHandler
    fun on(event: AdventurerDied) {
        post("🍺 A toast! ${event.adventurerId.take(8)}... ${event.cause}. F.")
    }

    @KafkaHandler(isDefault = true)
    fun ignore(event: Any) {
        // the tavern doesn't care about paperwork
    }

    private fun post(line: String) {
        _gossip += line
        log.info(line)
    }
}
