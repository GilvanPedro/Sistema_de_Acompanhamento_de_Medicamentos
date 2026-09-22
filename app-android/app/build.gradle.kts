import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// O push (Firebase) só liga se o google-services.json estiver na pasta app/ (ele não vai para o Git: cada
// desenvolvedor baixa o dele no console do Firebase). Sem o arquivo, o app compila e funciona normalmente, sem push.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// A chave de assinatura fica fora do Git: keystore.properties (ignorado) aponta para o .jks e guarda as senhas.
// Sem esse arquivo o app ainda compila (debug); só o release sai sem assinatura.
val chaveDeAssinatura = Properties().apply {
    val arquivo = rootProject.file("keystore.properties")
    if (arquivo.exists()) arquivo.inputStream().use { load(it) }
}

android {
    namespace = "br.com.cuidamed"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "br.com.cuidamed"
        minSdk = 26
        targetSdk = 37
        versionCode = 9
        versionName = "1.3.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (chaveDeAssinatura.containsKey("storeFile")) {
            create("release") {
                storeFile = file(chaveDeAssinatura.getProperty("storeFile"))
                storePassword = chaveDeAssinatura.getProperty("storePassword")
                keyAlias = chaveDeAssinatura.getProperty("keyAlias")
                keyPassword = chaveDeAssinatura.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8 liga a remoção de código não usado, a otimização e a ofuscação dos nomes; encolhe o pacote e
            // dificulta a leitura de quem tentar ler o APK. As regras próprias ficam em proguard-rules.pro.
            isShrinkResources = true
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // Os testes rodam sem Android: chamadas como Log.w devolvem o valor padrão em vez de falhar.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}