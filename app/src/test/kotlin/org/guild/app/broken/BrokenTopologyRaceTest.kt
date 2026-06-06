package org.guild.app.broken

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
@ActiveProfiles("broken", "test")
@EmbeddedKafka(
    partitions = 1,
    topics = ["guild.adventurer_registered", "guild.adventurer_leveled_up"],
)
class BrokenTopologyRaceTest {

    /** Must match @EmbeddedKafka(partitions = ...) above. */
    private val partitionsPerTopic = 1

    @Autowired lateinit var runner: ScenarioRunner
    @Autowired lateinit var ledger: GuildLedgerState
    @Autowired lateinit var registry: KafkaListenerEndpointRegistry

    @BeforeEach
    fun waitForConsumers() {
        // Both listener containers must have their partitions assigned before
        // we produce - otherwise the consumers may pick up all messages in a
        // single poll batch before the rebalance gives the level-up container
        // its partition, defeating the race.
        registry.listenerContainers.forEach { container ->
            ContainerTestUtils.waitForAssignment(container, partitionsPerTopic)
        }
    }

    @Test
    fun `the level-up ALWAYS overtakes the registration`() {
        val heroId = runner.run()

        // Worst-case drain of the flooded topic: flood-size × latency-ms ≈ 1 s.
        // 30 s gives a ~30× margin for GC pauses and slow CI runners.
        await.atMost(Duration.ofSeconds(30)) untilAsserted {
            assertThat(ledger.unknownAdventurerErrors)
                .describedAs("level-up must arrive before the flooded registration consumer reaches the hero")
                .contains(heroId)
        }
    }
}
