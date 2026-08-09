plugins {
    id("com.android.application")
}

android {
    namespace = "com.yuholt.storagespoof"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yuholt.storagespoof"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*"
            )
        }
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")

    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
