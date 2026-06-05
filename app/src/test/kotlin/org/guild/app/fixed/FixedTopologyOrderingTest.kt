package org.guild.app.fixed

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.guild.app.ledger.GuildLedgerState
import org.guild.app.producer.ScenarioRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.kafka.test.utils.ContainerTestUtils
import org.springframework.test.context.ActiveProfiles
import java.time.Duration

@SpringBootTest
@ActiveProfiles("fixed", "test")
@EmbeddedKafka(partitions = 3, topics = ["guild.adventurers"])
class FixedTopologyOrderingTest {

    @Autowired lateinit var runner: ScenarioRunner
    @Autowired lateinit var ledger: GuildLedgerState
    @Autowired lateinit var noticeBoard: TavernNoticeBoard
    @Autowired lateinit var registry: KafkaListenerEndpointRegistry

    @BeforeEach
    fun waitForConsumers() {
        // Both listener containers (guild-ledger + tavern-notice-board) must have
        // all 3 partitions assigned before we produce — otherwise early records
        // could be processed before the rebalance completes.
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, 3)
        }
    }

    @Test
    fun `same flood, same latency - the hero's lifecycle processes in order`() {
        val heroId = runner.run()

        await.atMost(Duration.ofSeconds(60)) untilAsserted {
            // The hero died LAST in his lifecycle — once processed he's off the roster…
            assertThat(ledger.levelOf(heroId)).isNull()
            // …and the tavern heard about both his level-up AND his death, in order
            assertThat(noticeBoard.gossip.filter { heroId.take(8) in it }).hasSize(2)
        }
        // the race NEVER happened: no unknown-adventurer errors, despite identical load
        assertThat(ledger.unknownAdventurerErrors).isEmpty()
    }
}
