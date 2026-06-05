package org.guild.app.ledger

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class AdventurerRecord(val name: String, val characterClass: String, val level: Int)

/**
 * The Guild Ledger — system of record for adventurers.
 * Profile-agnostic: both topologies feed the same state, so the ONLY
 * difference the audience sees between acts is event ordering.
 */
@Component
class GuildLedgerState {

    private val log = LoggerFactory.getLogger(javaClass)
    private val roster = ConcurrentHashMap<String, AdventurerRecord>()

    /** Level-ups that arrived for adventurers the ledger has never seen. The demo's red flag. */
    val unknownAdventurerErrors = CopyOnWriteArrayList<String>()

    fun register(id: String, name: String, characterClass: String) {
        roster[id] = AdventurerRecord(name, characterClass, level = 1)
        log.info("📜 {} the {} joined the guild (level 1)", name, characterClass)
    }

    fun levelUp(id: String, newLevel: Int): Boolean {
        val current = roster[id]
        if (current == null) {
            unknownAdventurerErrors += id
            log.error("💥 LEVEL-UP FOR UNKNOWN ADVENTURER {} — the ledger has never heard of them!", id)
            return false
        }
        roster[id] = current.copy(level = newLevel)
        log.info("⬆️ {} reached level {}", current.name, newLevel)
        return true
    }

    fun acceptQuest(id: String, questName: String) {
        val who = roster[id]?.name ?: id
        log.info("⚔️ {} accepted quest: {}", who, questName)
    }

    fun die(id: String, cause: String) {
        val removed = roster.remove(id)
        log.info("💀 {} has died ({})", removed?.name ?: id, cause)
    }

    fun levelOf(id: String): Int? = roster[id]?.level
}
