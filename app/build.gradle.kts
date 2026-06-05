plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":schemas")) {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-kafka")
    implementation("io.confluent:kafka-avro-serializer:8.2.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test") {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }
    testImplementation("org.awaitility:awaitility-kotlin:4.3.0")
}
