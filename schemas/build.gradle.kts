import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.avro.Schema
import org.apache.avro.idl.IdlReader
import java.net.ConnectException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // IdlReader + Schema are in avro-idl and avro; jackson-databind comes transitively.
        // Pinned to the androa avro plugin's avro version (1.12.0) so the parse side
        // can never drift from codegen.
        classpath("org.apache.avro:avro-idl:1.12.0")
        classpath("org.apache.avro:avro:1.12.0")
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
    registryUrl: String,
    body: String? = null,
): HttpResponse<String> {
    val builder = HttpRequest.newBuilder(URI.create(url))
    if (body != null) {
        builder.header("Content-Type", "application/vnd.schemaregistry.v1+json")
        builder.method(method, HttpRequest.BodyPublishers.ofString(body))
    } else {
        builder.method(method, HttpRequest.BodyPublishers.noBody())
    }
    return try {
        client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    } catch (e: ConnectException) {
        error("Schema registry unreachable at $registryUrl — is the stack up? (make infra-up)")
    } catch (e: HttpConnectTimeoutException) {
        error("Schema registry unreachable at $registryUrl — is the stack up? (make infra-up)")
    }
}

fun listSubjects(client: HttpClient, registryUrl: String, mapper: ObjectMapper): Set<String> {
    val resp = registryRequest(client, "GET", "$registryUrl/subjects", registryUrl)
    if (resp.statusCode() != 200) error("GET /subjects failed: ${resp.statusCode()} ${resp.body()}")
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

    // The logic lives in build-script functions (deliberate: no buildSrc), which the
    // doLast lambda captures — incompatible with the configuration cache. Declare it
    // honestly so the task runs (CC disabled for it) instead of failing.
    notCompatibleWithConfigurationCache("registry tasks use build-script functions")

    // Effect is external registry state, not file outputs — never up-to-date.
    outputs.upToDateWhen { false }

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
                registryUrl,
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

    // The logic lives in build-script functions (deliberate: no buildSrc), which the
    // doLast lambda captures — incompatible with the configuration cache. Declare it
    // honestly so the task runs (CC disabled for it) instead of failing.
    notCompatibleWithConfigurationCache("registry tasks use build-script functions")

    // Effect is external registry state, not file outputs — never up-to-date.
    outputs.upToDateWhen { false }

    doLast {
        // Deletion is two-phase because the registry requires it: a permanent (hard)
        // delete is only allowed on a subject that was already soft-deleted. Soft delete
        // alone would work for re-registering, but leaves tombstoned versions behind —
        // the hard delete keeps rehearsal state truly clean.
        val registryUrl = registryUrlProvider.getOrElse("http://localhost:8081")
        val schemas = parseAvdlSchemas(avdlDir)
        val subjects = buildSubjectMap(schemas)
        val client = HttpClient.newHttpClient()
        val mapper = ObjectMapper()
        val existing = listSubjects(client, registryUrl, mapper)

        subjects.keys.forEach { subject ->
            if (subject in existing) {
                // Soft delete first (required before permanent delete)
                val soft = registryRequest(client, "DELETE", "$registryUrl/subjects/$subject", registryUrl)
                if (soft.statusCode() !in 200..299) {
                    error("Failed to soft-delete $subject: ${soft.statusCode()} ${soft.body()}")
                }
                // Permanent delete — keeps rehearsal state truly clean
                val permanent = registryRequest(
                    client,
                    "DELETE",
                    "$registryUrl/subjects/$subject?permanent=true",
                    registryUrl,
                )
                if (permanent.statusCode() !in 200..299) {
                    error("Failed to permanent-delete $subject: ${permanent.statusCode()} ${permanent.body()}")
                }
                println("unregistered $subject")
            } else {
                println("skipped $subject (not registered)")
            }
        }
    }
}
