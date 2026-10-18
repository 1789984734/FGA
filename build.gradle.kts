import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

tasks.register<Delete>("clean") {
    description = "Cleans the build directory of the root project."
    delete(rootProject.layout.buildDirectory)
}

/**
 * Google and androidx publish alphas to the same channel as stable releases, so
 * `dependencyUpdates` reports them as available updates unless they are filtered out here.
 *
 * A version is stable if it is digits and separators only, or has release, final, or GA in its
 * version.
 */
fun isStable(version: String): Boolean {
    val stableSuffix = listOf("RELEASE", "FINAL", "GA").any { version.uppercase().contains(it) }
    return stableSuffix || Regex("^[0-9,.v-]+(-r)?$").matches(version)
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    rejectVersionIf { !isStable(candidate.version) }
}
