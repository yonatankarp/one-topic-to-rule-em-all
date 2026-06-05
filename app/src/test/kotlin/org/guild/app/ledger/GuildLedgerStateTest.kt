package org.guild.app.ledger

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GuildLedgerStateTest {

    private val ledger = GuildLedgerState()

    @Test
    fun `registering an adventurer makes them known at level 1`() {
        ledger.register("id-1", "Bob the Brave", "Fighter")
        assertThat(ledger.levelOf("id-1")).isEqualTo(1)
    }

    @Test
    fun `level-up for a known adventurer updates their level`() {
        ledger.register("id-1", "Bob the Brave", "Fighter")
        val ok = ledger.levelUp("id-1", 2)
        assertThat(ok).isTrue()
        assertThat(ledger.levelOf("id-1")).isEqualTo(2)
    }

    @Test
    fun `level-up for an UNKNOWN adventurer is rejected and recorded as an error`() {
        val ok = ledger.levelUp("ghost-id", 2)
        assertThat(ok).isFalse()
        assertThat(ledger.unknownAdventurerErrors).containsExactly("ghost-id")
    }

    @Test
    fun `death removes the adventurer from the active roster`() {
        ledger.register("id-1", "Bob the Brave", "Fighter")
        ledger.die("id-1", "rocks fell")
        assertThat(ledger.levelOf("id-1")).isNull()
    }
}
