plugins {
    kotlin("jvm")
    id("io.github.androa.gradle.plugin.avro")
}

dependencies {
    api("org.apache.avro:avro:1.12.1")
    implementation("io.confluent:kafka-schema-registry-client:8.2.1")
}
