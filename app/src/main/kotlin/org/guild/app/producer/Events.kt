package org.guild.app.producer

import org.guild.adventurer.AdventurerDied
import org.guild.adventurer.AdventurerLeveledUp
import org.guild.adventurer.AdventurerRegistered
import org.guild.adventurer.QuestAccepted
import java.time.Instant

private val FIRST = listOf("Bram", "Tilda", "Osric", "Mira", "Fenn", "Greta", "Aldo", "Wren", "Cael", "Petra")
private val LAST = listOf("Mudfoot", "Thornberry", "Oakshield", "Nettlebane", "Copperpot", "Grimble")
private val CLASSES = listOf("Fighter", "Wizard", "Rogue", "Cleric", "Bard")

fun randomName(i: Int) = "${FIRST[i % FIRST.size]} ${LAST[(i / FIRST.size) % LAST.size]} #$i"
fun randomClass(i: Int) = CLASSES[i % CLASSES.size]

fun registered(id: String, name: String, characterClass: String): AdventurerRegistered =
    AdventurerRegistered.newBuilder()
        .setAdventurerId(id).setName(name).setCharacterClass(characterClass)
        .setRegisteredAt(Instant.now()).build()

fun leveledUp(id: String, newLevel: Int): AdventurerLeveledUp =
    AdventurerLeveledUp.newBuilder()
        .setAdventurerId(id).setNewLevel(newLevel).setLeveledAt(Instant.now()).build()

fun questAccepted(id: String, quest: String): QuestAccepted =
    QuestAccepted.newBuilder()
        .setAdventurerId(id).setQuestName(quest).setAcceptedAt(Instant.now()).build()

fun died(id: String, cause: String): AdventurerDied =
    AdventurerDied.newBuilder()
        .setAdventurerId(id).setCause(cause).setDiedAt(Instant.now()).build()
