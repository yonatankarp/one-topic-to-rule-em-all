package org.guild.app.broken

import org.guild.adventurer.AdventurerLeveledUp
import org.guild.adventurer.AdventurerRegistered
import org.guild.app.ledger.GuildLedgerState
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * Act 1: one topic per event type. The registration listener simulates a real
 * consumer doing per-message work (enrichment, DB write) — so the busy topic
 * lags while the quiet level-up topic is consumed instantly.
 */
@Profile("broken")
@Component
class BrokenTopologyListeners(
    private val ledger: GuildLedgerState,
    @Value("\${guild.registration-latency-ms}") private val latencyMs: Long,
) {

    @KafkaListener(topics = ["guild.adventurer_registered"], groupId = "guild-ledger")
    fun onRegistered(event: AdventurerRegistered) {
        Thread.sleep(latencyMs) // DEMO ONLY: simulates per-message enrichment/DB work —
                                // never block a listener thread like this in production
        ledger.register(event.adventurerId, event.name, event.characterClass)
    }

    @KafkaListener(topics = ["guild.adventurer_leveled_up"], groupId = "guild-ledger")
    fun onLeveledUp(event: AdventurerLeveledUp) {
        ledger.levelUp(event.adventurerId, event.newLevel)
    }
}
