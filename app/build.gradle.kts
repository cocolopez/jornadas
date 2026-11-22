plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}
android {
    namespace = "com.perez.jornadas"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.perez.jornadas"
        minSdk = 26
        targetSdk = 34
        // versionCode sube solo con cada compilación (minutos desde 1970) para poder actualizar sobre la versión anterior
        versionCode = (System.currentTimeMillis() / 60000L).toInt()
        versionName = "1.8"
    }
    // Clave fija: así todas las versiones se firman igual y Android las instala como actualización
    signingConfigs {
        create("fixed") {
            storeFile = file("jornadas.keystore")
            storePassword = "jornadas123"
            keyAlias = "jornadas"
            keyPassword = "jornadas123"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("fixed") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
