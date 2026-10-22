import java.io.FileInputStream
import java.util.Properties
import java.security.MessageDigest
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.net.URI

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

// Android SDK keys are scoped to package + signing certificate. Never reuse the debug
// key for release/validation or commit a real value to source control.
fun amapKey(variant: String): String = (localProperties.getProperty("amap.apiKey.$variant")
    ?: System.getenv("AMAP_API_KEY_${variant.uppercase()}") ?: "").trim().also {
    require(it.isEmpty() || it.matches(Regex("[0-9a-fA-F]{32}"))) { "Invalid AMap key format ($variant)" }
}

val officialSigningCertificateSha256 = "A8F8EAC6FF810CAC0B21CBD59A08D5525E1ACA9C225F86F516FBD1172BECDF6D"

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
val campusToolsApiBaseUrlRelease = (
    localProperties.getProperty("campusTools.apiBaseUrl.release")
        ?: System.getenv("MY_CHUANNONG_CAMPUS_TOOLS_API_BASE_URL_RELEASE")
        ?: "https://chuannong-api.mychangliapp.cn/api/v1/"
    ).trim()
// Explicitly authorized predeployment delivery: keep real release signing/optimization,
// but leave unavailable backend/map services disabled until their configuration exists.
val releaseReviewWithoutServices = providers.gradleProperty("releaseReviewWithoutServices")
    .orElse("false").get().toBooleanStrict()

fun isReleaseAccountEndpoint(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase() ?: return false
    return uri.scheme == "https" && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && uri.rawPath == "/api/v1/" &&
        (uri.port == -1 || uri.port in 1..65535) &&
        host != "localhost" && !host.endsWith(".localhost") &&
        host != "[::1]" && !host.startsWith("127.") &&
        host !in setOf("0.0.0.0", "10.0.2.2", "10.0.3.2") &&
        !host.endsWith(".invalid") && !host.endsWith(".test") &&
        !host.endsWith(".example") &&
        listOf("example.com", "example.net", "example.org").none {
            host == it || host.endsWith(".$it")
        }
}

tasks.register("verifyReleaseEndpointSafety") {
    notCompatibleWithConfigurationCache("Release endpoint configuration validation")
    doLast {
        // The disabled sentinel permits source tests/lint without private configuration.
        // APK/AAB delivery additionally requires validateReleaseConfiguration below.
        check(campusToolsApiBaseUrlRelease == "https://campus-tools.invalid/api/v1/" ||
            isReleaseAccountEndpoint(campusToolsApiBaseUrlRelease)) {
            "Release requires a HTTPS account service ending in /api/v1/, without local/debug hosts, credentials, query or fragment."
        }
        check(legacyDisabledBaseUrl == "https://legacy-disabled.invalid/")
    }
}

android {
    namespace = "com.mychuannong.app"
    // Default remains isolated validation; debug smoke tests verify the exact delivered APK.
    testBuildType = providers.gradleProperty("deviceTestBuildType").orElse("validation").get().also {
        require(it in setOf("validation", "debug"))
    }
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
        val academicUrl = providers.gradleProperty("sicauAcademicUrl").orElse("").get()
        require(academicUrl.isEmpty() || academicUrl.startsWith("https://") || academicUrl.startsWith("http://127.0.0.1:"))
        buildConfigField("String", "SICAU_ACADEMIC_URL", "\"$academicUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 24
        targetSdk = 36
        // Frozen 1.0.0 release; keep the Android upgrade counter above preview.9.
        versionCode = 10
        versionName = "1.0.0"
        buildConfigField("String", "OFFICIAL_SIGNING_CERTIFICATE_SHA256", "\"$officialSigningCertificateSha256\"")

        ndk {
            // The current AMap vendor artifact ships ARM libraries only. A mixed x86/ARM
            // APK would select x86 on emulators and fail to load its map engine.
            abiFilters.add("arm64-v8a")
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
        buildConfigField("String", "AMAP_API_KEY", "\"${amapKey("release")}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = providers.gradleProperty("cacheTestSuffix").orElse(".debug").get()
            buildConfigField("String", "AMAP_API_KEY", "\"${amapKey("debug")}\"")
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
            isDebuggable = false
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

        create("validation") {
            initWith(getByName("release"))
            applicationIdSuffix = ".validation"
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            matchingFallbacks += listOf("release")
            buildConfigField("String", "AMAP_API_KEY", "\"\"")
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
        checkReleaseBuilds = true
        abortOnError = true
    }
    buildFeatures {
        viewBinding = true
    }
}

val validateReleaseSigning by tasks.registering {
    notCompatibleWithConfigurationCache("Reads the original release signing identity outside the repository")
    doLast {
        check(hasReleaseSigningConfig) {
            "Release signing is not configured. Set MY_CHUANNONG_SIGNING_CONFIG to a complete local properties file outside the repository."
        }
        val repositoryPath = rootProject.projectDir.canonicalFile.toPath()
        val configPath = checkNotNull(releaseSigningConfigFile).canonicalFile.toPath()
        val keyFile = file(releaseSigningProperties.getProperty("storeFile")).canonicalFile
        check(!configPath.startsWith(repositoryPath) && !keyFile.toPath().startsWith(repositoryPath)) {
            "Release signing configuration and keystore must remain outside the repository."
        }
        check(keyFile.isFile) { "The frozen release keystore is missing. Restore its backup; do not regenerate it." }
        val storePassword = releaseSigningProperties.getProperty("storePassword").toCharArray()
        val keyPassword = releaseSigningProperties.getProperty("keyPassword").toCharArray()
        try {
            val keyStore = runCatching { KeyStore.getInstance(keyFile, storePassword) }.getOrNull()
            checkNotNull(keyStore) { "Cannot open the release keystore. Check the local signing configuration." }
            val alias = releaseSigningProperties.getProperty("keyAlias")
            check(runCatching { keyStore.getKey(alias, keyPassword) is PrivateKey }.getOrDefault(false)) {
                "The release private key is unavailable. Check the alias and local key password."
            }
            val certificate = keyStore.getCertificate(alias) as? X509Certificate
            checkNotNull(certificate) { "The release signing certificate is unavailable." }
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
                .joinToString("") { "%02x".format(it) }
            // The user found the original JKS and requested that we retain it.
            // Match the historical release certificate before signing 1.0.0.
            check(fingerprint.equals(officialSigningCertificateSha256, ignoreCase = true)) {
                "Release certificate differs from the original MyChuannong signing identity. Delivery is blocked."
            }
            check(runCatching { certificate.checkValidity() }.isSuccess) {
                "The frozen release certificate is outside its validity period."
            }
            logger.lifecycle("MyChuannong 1.0.0 release certificate verified (SHA-256: $fingerprint).")
        } finally {
            storePassword.fill('\u0000')
            keyPassword.fill('\u0000')
        }
    }
}

val validateReleaseConfiguration by tasks.registering {
    notCompatibleWithConfigurationCache("Checks local release service configuration")
    dependsOn(validateReleaseSigning, "verifyReleaseEndpointSafety")
    doLast {
        if (releaseReviewWithoutServices) {
            logger.lifecycle("Authorized local release review: account backend and/or AMap may be disabled until configured. Not for public distribution.")
            return@doLast
        }
        check(isReleaseAccountEndpoint(campusToolsApiBaseUrlRelease)) {
            "Set campusTools.apiBaseUrl.release or MY_CHUANNONG_CAMPUS_TOOLS_API_BASE_URL_RELEASE to the approved HTTPS account service before delivering a release."
        }
        check(amapKey("release").isNotEmpty()) {
            "Configure a separate AMap release key bound to com.mychuannong.app and its frozen release certificate."
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
    dependsOn(validateReleaseConfiguration)
}

androidComponents {
    beforeVariants(selector()) { variantBuilder ->
        (variantBuilder as com.android.build.api.variant.HasHostTestsBuilder)
            .hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]
            ?.enable = true
    }
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val versionName = output.versionName.orNull ?: ""
            val apkName = if (variant.name == "release") {
                "my川农_chuannong_${versionName}.apk"
            } else {
                "MyChuannong_${variant.name}_v${versionName}.apk"
            }
            (output as? com.android.build.api.variant.impl.VariantOutputImpl)
                ?.outputFileName?.set(apkName)
        }
    }
}
dependencies {
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.2.0")
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
    implementation("net.zetetic:sqlcipher-android:4.19.1@aar")
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
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation(libs.androidx.core.ktx)
    implementation("androidx.webkit:webkit:1.13.0")
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
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // workmanager
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.work.rxjava2)
    implementation(project(":CP_Common"))
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

    // Map + POI search; location is bundled by the vendor but never initialized by this app.
    implementation("com.amap.api:3dmap-location-search:11.3.100_loc11.3.000_sea9.8.1")
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Gradle 9 writes the forked test worker classpath through an argument file. On Windows that
// argument file is decoded with the system code page, so a project path containing Chinese
// characters becomes unreadable to the worker even though test discovery succeeds. Stage only
// the compiled test classes in an ASCII-safe temporary directory; production outputs stay put.
listOf("debug", "release").forEach { variant ->
    val capitalized = variant.replaceFirstChar { it.uppercase() }
    val stagedUnitTestClasses = layout.dir(
        providers.systemProperty("java.io.tmpdir").map { temporaryRoot ->
            file("$temporaryRoot/my-chuannong/$variant-unit-test-classes")
        }
    )
    val stageUnitTestClasses = tasks.register<Sync>("stage${capitalized}UnitTestClasses") {
        dependsOn(
            "compile${capitalized}UnitTestKotlin",
            "compile${capitalized}UnitTestJavaWithJavac",
            "bundle${capitalized}ClassesToRuntimeJar",
            ":CP_Common:bundleLibRuntimeToJar$capitalized"
        )
        from(layout.buildDirectory.dir(
            "intermediates/built_in_kotlinc/${variant}UnitTest/compile${capitalized}UnitTestKotlin/classes"
        ))
        from(layout.buildDirectory.dir(
            "intermediates/javac/${variant}UnitTest/compile${capitalized}UnitTestJavaWithJavac/classes"
        ))
        from(layout.buildDirectory.file(
            "intermediates/runtime_app_classes_jar/$variant/bundle${capitalized}ClassesToRuntimeJar/classes.jar"
        )) {
            into("runtime")
            rename { "app-runtime.jar" }
        }
        from(project(":CP_Common").layout.buildDirectory.file(
            "intermediates/runtime_library_classes_jar/$variant/bundleLibRuntimeToJar$capitalized/classes.jar"
        )) {
            into("runtime")
            rename { "cp-common-runtime.jar" }
        }
        from("src/test/resources")
        into(stagedUnitTestClasses)
    }

    afterEvaluate {
        tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
            if (name == "test${capitalized}UnitTest") {
                dependsOn(stageUnitTestClasses)
                classpath = files(
                    stagedUnitTestClasses,
                    stagedUnitTestClasses.map { it.file("runtime/app-runtime.jar") },
                    stagedUnitTestClasses.map { it.file("runtime/cp-common-runtime.jar") },
                    classpath
                )
            }
        }
    }
}
