package org.guild.app.config

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class TopicsConfig {

    @Bean
    @Profile("broken")
    fun registeredTopic(): NewTopic = NewTopic("guild.adventurer_registered", 1, 1.toShort())

    @Bean
    @Profile("broken")
    fun leveledUpTopic(): NewTopic = NewTopic("guild.adventurer_leveled_up", 1, 1.toShort())

    @Bean
    @Profile("fixed")
    fun adventurersTopic(): NewTopic = NewTopic("guild.adventurers", 3, 1.toShort())
}
