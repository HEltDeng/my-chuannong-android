import java.io.FileInputStream
import java.util.Properties
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
    id("kotlin-parcelize")
}
configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

val releaseSigningProperties = Properties()
val releaseSigningConfigFile = System.getenv("MY_CHUANNONG_SIGNING_CONFIG")
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?.let(::file)
releaseSigningConfigFile
    ?.takeIf { it.isFile }
    ?.inputStream()
    ?.use(releaseSigningProperties::load)

val releaseSigningKeys = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword",
)
val hasReleaseSigningConfig = releaseSigningConfigFile?.isFile == true &&
    releaseSigningKeys.all { key -> releaseSigningProperties.getProperty(key).orEmpty().isNotBlank() }

// 历史兼容接口只允许 Debug 显式配置。Release 统一指向同域禁用哨兵，
// 并由客户端在网络栈前拦截，避免访问不存在的旧接口。
// 值需以 "/" 结尾（Retrofit BaseUrl 要求）。
val legacyDisabledBaseUrl = "https://legacy-disabled.invalid/"
val planetApiBaseUrlDebug: String = localProperties.getProperty("planet.apiBaseUrl.debug")
    ?: System.getenv("PLANET_API_BASE_URL_DEBUG")
    ?: "https://planet-api-debug.invalid/v1/"
val planetApiBaseUrlRelease = legacyDisabledBaseUrl
val adsEnabled: Boolean = (
    localProperties.getProperty("ads.enabled")
        ?: System.getenv("ADS_ENABLED")
        ?: "false"
    ).trim().equals("true", ignoreCase = true)
val communityApiBaseUrlDebug: String = localProperties.getProperty("community.apiBaseUrl")
    ?: System.getenv("MY_CHUANNONG_COMMUNITY_API_BASE_URL")
    ?: "https://community-api.invalid/app/"
val communityCommentsBaseUrlDebug: String = localProperties.getProperty("community.commentsBaseUrl")
    ?: System.getenv("MY_CHUANNONG_COMMUNITY_COMMENTS_BASE_URL")
    ?: "https://community-api.invalid/app/fresh_news/"
val communityUserBaseUrlDebug: String = localProperties.getProperty("community.userBaseUrl")
    ?: System.getenv("MY_CHUANNONG_COMMUNITY_USER_BASE_URL")
    ?: "https://community-api.invalid/users/"
val toolsApiBaseUrlDebug: String = localProperties.getProperty("tools.apiBaseUrl")
    ?: System.getenv("MY_CHUANNONG_TOOLS_API_BASE_URL")
    ?: "https://tools-api.invalid/app/tools/"
val legacyToolsEnabledDebug = !toolsApiBaseUrlDebug.contains(".invalid", ignoreCase = true)
val campusToolsApiBaseUrlDebug: String =
    localProperties.getProperty("campusTools.apiBaseUrl.debug")
        ?: System.getenv("MY_CHUANNONG_CAMPUS_TOOLS_API_BASE_URL_DEBUG")
        ?: "http://127.0.0.1:8792/api/v1/"
val campusToolsApiBaseUrlRelease = "http://127.0.0.1:8792/api/v1/"

tasks.register("verifyReleaseEndpointSafety") {
    notCompatibleWithConfigurationCache("Independent preview endpoint gate")
    doLast {
        check(campusToolsApiBaseUrlRelease == "http://127.0.0.1:8792/api/v1/")
        check(legacyDisabledBaseUrl == "https://legacy-disabled.invalid/")
    }
}

android {
    namespace = "com.mychuannong.app"
    compileSdk = 36
    compileSdkMinor = 1
    buildFeatures {
        buildConfig = true
        compose = true
    }
    
    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }
    
    defaultConfig {
        applicationId = "com.mychuannong.app"
        minSdk = 24
        targetSdk = 36
        // V1.0.4/36 is the immutable production baseline. V1.0.5/37 adds
        // announcement delivery, consent-flow, branding and widget-preview work.
        versionCode = 2
        versionName = "0.1.0-preview.2"

        ndk {
            // 设置支持的SO库架构
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }

        // 兜底值：保证任何未显式覆盖的 variant 都拿得到 BaseUrl。
        // debug / release / benchmark 会在各自 buildType 内再覆盖为对应环境值。
        buildConfigField(
            "String",
            "PLANET_API_BASE_URL",
            "\"$planetApiBaseUrlRelease\""
        )
        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
        buildConfigField("String", "COMMUNITY_API_BASE_URL", "\"$legacyDisabledBaseUrl\"")
        buildConfigField("String", "COMMUNITY_COMMENTS_BASE_URL", "\"$legacyDisabledBaseUrl\"")
        buildConfigField("String", "COMMUNITY_USER_BASE_URL", "\"$legacyDisabledBaseUrl\"")
        buildConfigField("String", "TOOLS_API_BASE_URL", "\"$legacyDisabledBaseUrl\"")
        buildConfigField(
            "String",
            "CAMPUS_TOOLS_API_BASE_URL",
            "\"$campusToolsApiBaseUrlRelease\""
        )
        buildConfigField("boolean", "RELEASE_USER_SURFACE", "false")
        buildConfigField("boolean", "LEGACY_SERVICES_ENABLED", "false")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField(
                "String",
                "PLANET_API_BASE_URL",
                "\"$planetApiBaseUrlDebug\""
            )
            buildConfigField(
                "String",
                "CAMPUS_TOOLS_API_BASE_URL",
                "\"$campusToolsApiBaseUrlDebug\""
            )
            buildConfigField("String", "COMMUNITY_API_BASE_URL", "\"$communityApiBaseUrlDebug\"")
            buildConfigField("String", "COMMUNITY_COMMENTS_BASE_URL", "\"$communityCommentsBaseUrlDebug\"")
            buildConfigField("String", "COMMUNITY_USER_BASE_URL", "\"$communityUserBaseUrlDebug\"")
            buildConfigField("String", "TOOLS_API_BASE_URL", "\"$toolsApiBaseUrlDebug\"")
            buildConfigField("boolean", "LEGACY_SERVICES_ENABLED", legacyToolsEnabledDebug.toString())
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "ADS_ENABLED", "false")
            buildConfigField("boolean", "RELEASE_USER_SURFACE", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField(
                "String",
                "PLANET_API_BASE_URL",
                "\"$planetApiBaseUrlRelease\""
            )
            buildConfigField(
                "String",
                "CAMPUS_TOOLS_API_BASE_URL",
                "\"$campusToolsApiBaseUrlRelease\""
            )
            buildConfigField("boolean", "LEGACY_SERVICES_ENABLED", "false")
        }

        create("benchmark") {
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "ADS_ENABLED", "false")
            buildConfigField("boolean", "RELEASE_USER_SURFACE", "true")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // benchmark 贴近线上环境，走 release 域名
            buildConfigField(
                "String",
                "PLANET_API_BASE_URL",
                "\"$planetApiBaseUrlRelease\""
            )
            buildConfigField(
                "String",
                "CAMPUS_TOOLS_API_BASE_URL",
                "\"$campusToolsApiBaseUrlRelease\""
            )
            buildConfigField("boolean", "LEGACY_SERVICES_ENABLED", "false")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    viewBinding {
        enable = true
    }
    packaging {
        resources {
            // 排除重复的文件
            excludes += "mozilla/public-suffix-list.txt"
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        viewBinding = true
    }
}

val validateReleaseSigning by tasks.registering {
    inputs.property("releaseSigningConfigured", hasReleaseSigningConfig)
    doLast {
        check(inputs.properties["releaseSigningConfigured"] == true) {
            "Release signing is not configured. Set MY_CHUANNONG_SIGNING_CONFIG to a complete local properties file outside the repository."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn("verifyReleaseEndpointSafety")
}

tasks.matching { task ->
    task.name == "assembleRelease" ||
        task.name == "bundleRelease" ||
        task.name == "installRelease"
}.configureEach {
    dependsOn(validateReleaseSigning)
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variantBuilder ->
        (variantBuilder as com.android.build.api.variant.HasHostTestsBuilder)
            .hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]
            ?.enable = true
    }
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.orNull ?: ""
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set("MyChuannong_${variant.name}_v${versionName}.apk")
        }
    }
}
dependencies {
    implementation(libs.androidx.browser)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.compose.ui.graphics)
    "baselineProfile"(project(":baselineprofile"))
    // leakcanary
    debugImplementation(libs.leakcanary.android)
    // Material Design
    //RxJava
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.androidx.room.rxjava3)
    //Glide
    implementation(libs.glide)
    ksp(libs.glide.ksp)
    //MMKV
    implementation(libs.mmkv)
    // OkHttp
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp.urlconnection)
    //Retrofit
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.converter.scalars)
    // Gson
    implementation(libs.gson)
    //TimetableView
    implementation(libs.timetableview)
    //Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    // 图片裁剪库
    implementation(libs.ucrop)
    // PhotoView

    // SubsamplingScaleImageView
    implementation(libs.subsampling.scale.image.view)
    // PhotoView
    implementation(libs.photoview)
    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    // Activity KTX for viewModels()
    implementation(libs.androidx.activity.ktx)
    // Fragment KTX 提供了 viewModels() 扩展函数
    implementation(libs.androidx.fragment.ktx)
    //滚轮
    implementation(libs.common)
    implementation(libs.wheelpicker)
    //FlexboxLayout
    implementation(libs.flexbox)
    //lottie
    implementation(libs.lottie)
    // 缺省页
    implementation(libs.statelayout)
    // jsoup
    implementation(libs.jsoup)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // workmanager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.rxjava2)
    implementation(project(":CP_Common"))
    //CodeLocator
    debugImplementation(libs.codelocator.core)
    debugImplementation(libs.codelocator.lancet.all)
    // Coil for image loading
    implementation(libs.coil.compose)
    //csustDataGet
    // Local 1.1.4 security build: authentication and request behavior are unchanged;
    // dependency-internal logs that could expose credentials/cookies are disabled.
//    implementation(libs.csustdataget)
//    implementation(files("libs/csust_spider-release.aar"))
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.haze)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    // Campus map remains excluded from V1: no Lite3DMap/AMap dependency.
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Gradle 9 writes the forked test worker classpath through an argument file. On Windows that
// argument file is decoded with the system code page, so a project path containing Chinese
// characters becomes unreadable to the worker even though test discovery succeeds. Stage only
// the compiled test classes in an ASCII-safe temporary directory; production outputs stay put.
val stagedDebugUnitTestClasses = layout.dir(
    providers.systemProperty("java.io.tmpdir").map { temporaryRoot ->
        file("$temporaryRoot/my-chuannong/debug-unit-test-classes")
    }
)
val stageDebugUnitTestClasses by tasks.registering(Sync::class) {
    dependsOn(
        "compileDebugUnitTestKotlin",
        "compileDebugUnitTestJavaWithJavac",
        "bundleDebugClassesToRuntimeJar",
        ":CP_Common:bundleLibRuntimeToJarDebug"
    )
    from(layout.buildDirectory.dir(
        "intermediates/built_in_kotlinc/debugUnitTest/compileDebugUnitTestKotlin/classes"
    ))
    from(layout.buildDirectory.dir(
        "intermediates/javac/debugUnitTest/compileDebugUnitTestJavaWithJavac/classes"
    ))
    from(layout.buildDirectory.file(
        "intermediates/runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar"
    )) {
        into("runtime")
        rename { "app-runtime.jar" }
    }
    from(project(":CP_Common").layout.buildDirectory.file(
        "intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar"
    )) {
        into("runtime")
        rename { "cp-common-runtime.jar" }
    }
    into(stagedDebugUnitTestClasses)
}

afterEvaluate {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        if (name == "testDebugUnitTest") {
            dependsOn(stageDebugUnitTestClasses)
            classpath = files(
                stagedDebugUnitTestClasses,
                stagedDebugUnitTestClasses.map { it.file("runtime/app-runtime.jar") },
                stagedDebugUnitTestClasses.map { it.file("runtime/cp-common-runtime.jar") },
                classpath
            )
        }
    }
}
