// The example of the android module: an application that opens the light wallet of a
// regtest account on a light wallet server and reads its balance, through the AAR of
// the android module — the JNA layer of the library into libmonero_c.so, and the
// light wallet REST API of the server behind it.
//
// The server, the account and its view key are build fields, so the example runs
// against a real stack without mock data: the defaults are the throwaway regtest
// account of the integration tests (MoneroWalletLightIntegrationTest) and the
// light wallet server of the compose of src/test/integration, which an emulator
// reaches with `adb reverse tcp:8443 tcp:8443`. Point -Plws.uri, -Pwallet.address
// and -Pwallet.viewKey at another stack to run it elsewhere.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

repositories {
    google()
    mavenCentral()
}

android {
    namespace = "org.libmonero.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.libmonero.example"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField("String", "LWS_URI", "\"${providers.gradleProperty("lws.uri").getOrElse("http://127.0.0.1:8443")}\"")
        buildConfigField(
            "String", "WALLET_ADDRESS",
            "\"${providers.gradleProperty("wallet.address").getOrElse("49MNnnrtLzj5vsWsHya28tKFPTcYfEnEYLfCqogA6Dw1TeqvHv5uRPTgjXSyD9pSoPVae3oZe3R5xHsGeC4VapjRV2m6gGj")}\""
        )
        buildConfigField(
            "String", "WALLET_VIEW_KEY",
            "\"${providers.gradleProperty("wallet.viewKey").getOrElse("6cb8586d26fc0b5526035f1e39566d6aee6ff246dfc55bb49c6c99ea2643380f")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":android"))
}
