plugins {
    kotlin("jvm")
    id("io.github.androa.gradle.plugin.avro")
}

dependencies {
    api("org.apache.avro:avro:1.12.1")
    implementation("io.confluent:kafka-schema-registry-client:8.2.1")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.register<JavaExec>("registerAllSchemas") {
    group = "schema registry"
    description = "Registers all event schemas under both naming strategies"
    mainClass.set("org.guild.schemas.RegisterSchemasKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(providers.gradleProperty("schemaRegistryUrl").getOrElse("http://localhost:8081"))
}

tasks.register<JavaExec>("unregisterAllSchemas") {
    group = "schema registry"
    description = "Deletes all event schema subjects (soft + permanent) for a clean re-register"
    mainClass.set("org.guild.schemas.UnregisterSchemasKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(providers.gradleProperty("schemaRegistryUrl").getOrElse("http://localhost:8081"))
}
