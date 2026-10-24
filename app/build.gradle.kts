import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.swaystack.app"
  compileSdk = 35

  defaultConfig {
    applicationId = "com.swaystack.app"
    minSdk = 24
    targetSdk = 35
    versionCode = 4
    versionName = "1.1.0"
    buildConfigField("String", "ADMOB_APP_ID", "\"ca-app-pub-3940256099942544~3347511713\"")
    buildConfigField("String", "ADMOB_REWARDED_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
    buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
    buildConfigField("String", "PRIVACY_POLICY_URL", "\"https://example.com/privacy\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val props = Properties().apply { val f = rootProject.file("keystore.properties"); if (f.exists()) f.inputStream().use { input -> load(input) } }
      val keystorePath = props.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
      if (keystorePath != null) storeFile = rootProject.file(keystorePath)
      storePassword = props.getProperty("storePassword") ?: System.getenv("STORE_PASSWORD")
      keyAlias = props.getProperty("keyAlias") ?: "upload"
      keyPassword = props.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (rootProject.file("keystore.properties").exists() || System.getenv("KEYSTORE_PATH") != null) signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = false
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Robolectric 4.13 with SDK 34 needs JDK 17+. Use an installed JDK for tests only.
tasks.withType<Test>().configureEach {
  javaLauncher.set(
    javaToolchains.launcherFor {
      languageVersion.set(JavaLanguageVersion.of(21))
    }
  )
}

kotlin {
  jvmToolchain(11)
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation("com.google.android.gms:play-services-ads:23.6.0")
  implementation("com.google.android.ump:user-messaging-platform:3.1.0")
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
}
