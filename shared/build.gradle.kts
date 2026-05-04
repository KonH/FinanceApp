plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.koin.core)
            implementation(libs.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android)
            implementation(libs.coroutines.android)
            implementation(libs.datastore.prefs)
            implementation(libs.koin.android)
            implementation(libs.gms.auth)
            implementation(libs.google.drive.sdk)
            implementation(libs.google.auth.library)
            implementation(libs.google.http.android)
            implementation(libs.google.api.client.android)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.turbine)
            implementation(libs.kotest.assertions)
        }
        val androidInstrumentedTest by getting {
            kotlin.srcDirs("src/androidTest/kotlin")
            dependencies {
                implementation(libs.junit)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.junit)
                implementation(libs.koin.test)
                implementation(libs.koin.test.junit4)
            }
        }
    }
}

android {
    namespace = "com.konhit.financeapp.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDir("src/androidTest/assets")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("MmexDatabase") {
            packageName = "com.konhit.financeapp.db"
            schemaOutputDirectory = file("src/commonMain/sqldelight/schema")
            verifyMigrations = false
        }
    }
}
