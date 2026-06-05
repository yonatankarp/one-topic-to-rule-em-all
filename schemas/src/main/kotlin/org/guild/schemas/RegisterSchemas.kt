package org.guild.schemas

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import org.guild.adventurer.AdventurerDied
import org.guild.adventurer.AdventurerLeveledUp
import org.guild.adventurer.AdventurerRegistered
import org.guild.adventurer.QuestAccepted

const val AGGREGATE_TOPIC = "guild.adventurers"

val EVENT_SCHEMAS = listOf(
    AdventurerRegistered.getClassSchema(),
    AdventurerLeveledUp.getClassSchema(),
    QuestAccepted.getClassSchema(),
    AdventurerDied.getClassSchema(),
)

/** Act 1 (broken): TopicNameStrategy — subject is "<topic>-value", one schema per topic. */
val TOPIC_NAME_SUBJECTS = mapOf(
    "guild.adventurer_registered-value" to AdventurerRegistered.getClassSchema(),
    "guild.adventurer_leveled_up-value" to AdventurerLeveledUp.getClassSchema(),
)

/** Act 2 (fixed): TopicRecordNameStrategy — subject is "<topic>-<record FQN>". */
fun topicRecordSubjects() = EVENT_SCHEMAS.associateBy { "$AGGREGATE_TOPIC-${it.fullName}" }

fun main(args: Array<String>) {
    val url = args.firstOrNull() ?: "http://localhost:8081"
    val client = CachedSchemaRegistryClient(url, 100)
    (TOPIC_NAME_SUBJECTS + topicRecordSubjects()).forEach { (subject, schema) ->
        val id = client.register(subject, AvroSchema(schema))
        println("registered $subject -> schema id $id")
    }
}
