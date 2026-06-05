import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.avro.Schema
import org.apache.avro.idl.IdlReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // IdlReader + Schema are in avro-idl and avro; jackson-databind comes transitively.
        classpath("org.apache.avro:avro-idl:1.12.1")
        classpath("org.apache.avro:avro:1.12.1")
        classpath("com.fasterxml.jackson.core:jackson-databind:2.18.3")
    }
}

plugins {
    `java-library`
    id("io.github.androa.gradle.plugin.avro")
}

dependencies {
    api("org.apache.avro:avro:1.12.1")
}

// ---------------------------------------------------------------------------
// Schema-registry helpers — plain JDK HttpClient, no Confluent SDK needed.
// ---------------------------------------------------------------------------

/**
 * Parse all .avdl files in the given directory and return a map of
 * recordName (simple) -> Schema for every named schema found.
 */
fun parseAvdlSchemas(avroSrcDir: File): Map<String, Schema> {
    val result = mutableMapOf<String, Schema>()
    val reader = IdlReader()
    avroSrcDir.walkTopDown()
        .filter { it.extension == "avdl" }
        .forEach { avdlFile ->
            val idlFile = reader.parse(avdlFile.toPath())
            idlFile.namedSchemas.values.forEach { schema ->
                result[schema.name] = schema
            }
        }
    return result
}

/**
 * Build a subject → Schema map that mirrors the two naming strategies used in the demo.
 *
 * Act 1 (TopicNameStrategy): subject = "<topic>-value", covers only the two racing events.
 * Act 2 (TopicRecordNameStrategy): subject = "<aggregate-topic>-<record FQN>", all four.
 */
fun buildSubjectMap(schemas: Map<String, Schema>): Map<String, Schema> {
    val aggregateTopic = "guild.adventurers"

    val topicNameSubjects = mapOf(
        "guild.adventurer_registered-value" to schemas.getValue("AdventurerRegistered"),
        "guild.adventurer_leveled_up-value" to schemas.getValue("AdventurerLeveledUp"),
    )

    val topicRecordSubjects = schemas.values.associateBy { "$aggregateTopic-${it.fullName}" }

    return topicNameSubjects + topicRecordSubjects
}

fun registryRequest(
    client: HttpClient,
    method: String,
    url: String,
    body: String? = null,
): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI.create(url))
    if (body != null) {
        builder.header("Content-Type", "application/vnd.schemaregistry.v1+json")
        builder.method(method, HttpRequest.BodyPublishers.ofString(body))
    } else {
        builder.method(method, HttpRequest.BodyPublishers.noBody())
    }
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
}

fun listSubjects(client: HttpClient, registryUrl: String): Set<String> {
    val resp = registryRequest(client, "GET", "$registryUrl/subjects")
    if (resp.statusCode() != 200) error("GET /subjects failed: ${resp.statusCode()} ${resp.body()}")
    val mapper = ObjectMapper()
    val node = mapper.readTree(resp.body())
    return node.map { it.asText() }.toSet()
}

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------

val avdlDir = layout.projectDirectory.dir("src/main/avro").asFile
val registryUrlProvider = providers.gradleProperty("schemaRegistryUrl")

tasks.register("registerAllSchemas") {
    group = "schema registry"
    description = "Registers all event schemas under both naming strategies"

    inputs.dir(avdlDir)

    doLast {
        val registryUrl = registryUrlProvider.getOrElse("http://localhost:8081")
        val schemas = parseAvdlSchemas(avdlDir)
        val subjects = buildSubjectMap(schemas)
        val client = HttpClient.newHttpClient()
        val mapper = ObjectMapper()

        subjects.forEach { (subject, schema) ->
            val schemaJson = schema.toString()
            // Escape the schema JSON as a string value inside the outer JSON object.
            val escapedSchema = mapper.writeValueAsString(schemaJson)
            val requestBody = """{"schema":${escapedSchema}}"""
            val resp = registryRequest(
                client,
                "POST",
                "$registryUrl/subjects/$subject/versions",
                requestBody,
            )
            if (resp.statusCode() !in 200..299) {
                error("Failed to register $subject: ${resp.statusCode()} ${resp.body()}")
            }
            val id = mapper.readTree(resp.body())["id"].asInt()
            println("registered $subject -> schema id $id")
        }
    }
}

tasks.register("unregisterAllSchemas") {
    group = "schema registry"
    description = "Deletes all event schema subjects (soft + permanent) for a clean re-register"

    inputs.dir(avdlDir)

    doLast {
        // Deletion is two-phase because the registry requires it: a permanent (hard)
        // delete is only allowed on a subject that was already soft-deleted. Soft delete
        // alone would work for re-registering, but leaves tombstoned versions behind —
        // the hard delete keeps rehearsal state truly clean.
        val registryUrl = registryUrlProvider.getOrElse("http://localhost:8081")
        val schemas = parseAvdlSchemas(avdlDir)
        val subjects = buildSubjectMap(schemas)
        val client = HttpClient.newHttpClient()
        val existing = listSubjects(client, registryUrl)

        subjects.keys.forEach { subject ->
            if (subject in existing) {
                // Soft delete first (required before permanent delete)
                registryRequest(client, "DELETE", "$registryUrl/subjects/$subject")
                // Permanent delete — keeps rehearsal state truly clean
                registryRequest(client, "DELETE", "$registryUrl/subjects/$subject?permanent=true")
                println("unregistered $subject")
            } else {
                println("skipped $subject (not registered)")
            }
        }
    }
}
