package org.guild.app.fixed

import org.guild.adventurer.AdventurerDied
import org.guild.adventurer.AdventurerLeveledUp
import org.guild.adventurer.AdventurerRegistered
import org.guild.adventurer.QuestAccepted
import org.guild.app.ledger.GuildLedgerState
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Act 2: every adventurer event on ONE topic, keyed by adventurer id.
 * Spring routes each record to the handler matching its deserialized type —
 * the class reads like the aggregate's lifecycle.
 */
@Profile("fixed")
@Component
@KafkaListener(topics = ["guild.adventurers"], groupId = "guild-ledger")
class GuildLedgerListener(
    private val ledger: GuildLedgerState,
    @Value("\${guild.registration-latency-ms}") private val latencyMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaHandler
    fun on(event: AdventurerRegistered) {
        Thread.sleep(latencyMs) // same simulated work as Act 1 — same load, different outcome
        ledger.register(event.adventurerId, event.name, event.characterClass)
    }

    @KafkaHandler
    fun on(event: AdventurerLeveledUp) {
        ledger.levelUp(event.adventurerId, event.newLevel)
    }

    @KafkaHandler
    fun on(event: QuestAccepted) {
        ledger.acceptQuest(event.adventurerId, event.questName)
    }

    @KafkaHandler
    fun on(event: AdventurerDied) {
        ledger.die(event.adventurerId, event.cause)
    }

    @KafkaHandler(isDefault = true)
    fun unknown(event: Any) {
        log.warn("❓ Unhandled event type: {}", event::class.simpleName)
    }
}
