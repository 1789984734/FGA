plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        minSdk = 24
    }

    buildTypes {
        getByName("release") {
        }
        create("ci") {
            initWith(getByName("release"))
        }
    }
    namespace = "io.github.fate_grand_automata.prefs"
}

dependencies {
    implementation(project(":scripts"))
    implementation(libs.androidx.core.ktx)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.dagger.hilt.android)
    ksp(libs.dagger.hilt.compiler)

    api(libs.fredporciuncula.flow.preferences)
}