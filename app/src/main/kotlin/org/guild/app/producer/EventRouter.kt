package org.guild.app.producer

import org.apache.avro.specific.SpecificRecord
import org.guild.adventurer.AdventurerLeveledUp
import org.guild.adventurer.AdventurerRegistered
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** Decides which topic an event lands on. The ONLY producer-side difference between the acts. */
interface EventRouter {
    fun topicFor(event: SpecificRecord): String
}

@Component
@Profile("broken")
class BrokenEventRouter : EventRouter {
    override fun topicFor(event: SpecificRecord): String = when (event) {
        is AdventurerRegistered -> "guild.adventurer_registered"
        is AdventurerLeveledUp -> "guild.adventurer_leveled_up"
        else -> error("broken topology has no topic for ${event.schema.name}")
    }
}

@Component
@Profile("fixed", "fixed-when")
class FixedEventRouter : EventRouter {
    override fun topicFor(event: SpecificRecord): String = "guild.adventurers"
}
