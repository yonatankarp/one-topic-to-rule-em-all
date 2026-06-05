package org.guild.schemas

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient

/**
 * Deletes all demo subjects from the schema registry (soft delete, then permanent),
 * so rehearsals can reset registry state without restarting the stack.
 */
fun main(args: Array<String>) {
    val url = args.firstOrNull() ?: "http://localhost:8081"
    val client = CachedSchemaRegistryClient(url, 100)
    val demoSubjects = TOPIC_NAME_SUBJECTS.keys + topicRecordSubjects().keys
    val existing = client.allSubjects.toSet()
    demoSubjects.forEach { subject ->
        if (subject in existing) {
            client.deleteSubject(subject)            // soft delete
            client.deleteSubject(subject, true)      // permanent — allows clean re-register
            println("unregistered $subject")
        } else {
            println("skipped $subject (not registered)")
        }
    }
}
