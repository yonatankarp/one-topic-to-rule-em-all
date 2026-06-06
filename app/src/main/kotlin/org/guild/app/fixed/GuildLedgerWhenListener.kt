package org.guild.app.fixed

import org.apache.avro.specific.SpecificRecord
import org.guild.adventurer.AdventurerDied
import org.guild.adventurer.AdventurerLeveledUp
import org.guild.adventurer.AdventurerRegistered
import org.guild.adventurer.QuestAccepted
import org.guild.app.ledger.GuildLedgerState
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * The `when`-dispatch alternative to [GuildLedgerListener] - the other column of
 * the talk's trade-off slide. Try it with: --spring.profiles.active=fixed-when
 *
 * Trade-off: dispatch is explicit and greppable in one place, but Avro-generated
 * classes are NOT sealed, so the compiler cannot enforce exhaustiveness - the
 * `else` branch is still required, and a forgotten event type lands there at
 * runtime, exactly like @KafkaHandler's isDefault. Real compile-time
 * exhaustiveness requires mapping events into your own sealed hierarchy first.
 */
@Profile("fixed-when")
@Component
class GuildLedgerWhenListener(
    private val ledger: GuildLedgerState,
    @Value("\${guild.registration-latency-ms}") private val latencyMs: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["guild.adventurers"], groupId = "guild-ledger")
    fun on(event: SpecificRecord) {
        when (event) {
            is AdventurerRegistered -> {
                Thread.sleep(latencyMs) // same simulated work as the other listeners
                ledger.register(event.adventurerId, event.name, event.characterClass)
            }
            is AdventurerLeveledUp -> ledger.levelUp(event.adventurerId, event.newLevel)
            is QuestAccepted -> ledger.acceptQuest(event.adventurerId, event.questName)
            is AdventurerDied -> ledger.die(event.adventurerId, event.cause)
            else -> log.warn("Unhandled event type: {}", event::class.simpleName)
        }
    }
}
