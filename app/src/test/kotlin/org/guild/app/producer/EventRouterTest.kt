package org.guild.app.producer

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EventRouterTest {

    @Test
    fun `broken router scatters event types across topics`() {
        val router = BrokenEventRouter()
        assertThat(router.topicFor(registered("a", "Bob", "Fighter"))).isEqualTo("guild.adventurer_registered")
        assertThat(router.topicFor(leveledUp("a", 2))).isEqualTo("guild.adventurer_leveled_up")
    }

    @Test
    fun `broken router rejects events that have no topic in the broken topology`() {
        val router = BrokenEventRouter()
        assertThatThrownBy { router.topicFor(questAccepted("a", "Slay the rat king")) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { router.topicFor(died("a", "rocks fell")) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `fixed router sends every event type to the aggregate topic`() {
        val router = FixedEventRouter()
        assertThat(router.topicFor(registered("a", "Bob", "Fighter"))).isEqualTo("guild.adventurers")
        assertThat(router.topicFor(leveledUp("a", 2))).isEqualTo("guild.adventurers")
        assertThat(router.topicFor(questAccepted("a", "Slay the rat king"))).isEqualTo("guild.adventurers")
        assertThat(router.topicFor(died("a", "rocks fell"))).isEqualTo("guild.adventurers")
    }
}
