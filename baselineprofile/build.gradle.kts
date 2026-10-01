import java.util.Properties

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

val baselineLocalProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val environmentStudentId = providers.environmentVariable("BASELINE_STUDENT_ID").orNull.orEmpty()
val environmentStudentPassword =
    providers.environmentVariable("BASELINE_STUDENT_PASSWORD").orNull.orEmpty()
val localStudentId = baselineLocalProperties.getProperty("baseline.studentId").orEmpty()
val localStudentPassword = baselineLocalProperties.getProperty("baseline.studentPassword").orEmpty()

val baselineCredentials = when {
    environmentStudentId.isNotBlank() && environmentStudentPassword.isNotBlank() ->
        environmentStudentId to environmentStudentPassword
    localStudentId.isNotBlank() && localStudentPassword.isNotBlank() ->
        localStudentId to localStudentPassword
    else -> null
}

android {
    namespace = "com.dcelysia.baselineprofile"
    compileSdk = 36
    compileSdkMinor = 1

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        baselineCredentials?.let { (studentId, studentPassword) ->
            testInstrumentationRunnerArguments["baseline.studentId"] = studentId
            testInstrumentationRunnerArguments["baseline.studentPassword"] = studentPassword
        }
    }

    targetProjectPath = ":app"

}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
