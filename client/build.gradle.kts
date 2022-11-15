val ktorVersion: String by project

plugins{
    application
    id("io.ktor.plugin") version "2.1.1"
    kotlin("jvm") version "1.7.10"
}

group = "com.poisonedyouth"
version = "0.0.3"

application{
    mainClass.set("com.poisonedyouth.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // ktor
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-client-auth:$ktorVersion")


    // cli
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")

    implementation(project(":common"))
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}