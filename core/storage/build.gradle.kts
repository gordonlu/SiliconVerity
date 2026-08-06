plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:benchmark"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
