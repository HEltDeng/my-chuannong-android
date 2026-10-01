import java.util.Properties
plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.compose) }
val sign = Properties().apply { System.getenv("MY_CHUANNONG_SIGNING_CONFIG")?.let { file(it).inputStream().use(::load) } }
android {
 namespace = "com.mychuannong.app"
 compileSdk = 36
 compileSdkMinor = 1
 defaultConfig { applicationId = "com.mychuannong.app"; minSdk = 24; targetSdk = 36; versionCode = 1; versionName = "0.1.0-preview.1" }
 buildFeatures { compose = true; buildConfig = true }
 signingConfigs { create("preview") { if(sign.isNotEmpty()) { storeFile=file(sign.getProperty("storeFile")); storePassword=sign.getProperty("storePassword"); keyAlias=sign.getProperty("keyAlias"); keyPassword=sign.getProperty("keyPassword") } } }
 buildTypes { release { isMinifyEnabled=false; signingConfig=signingConfigs.getByName("preview") } }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
}
dependencies {
 implementation(libs.androidx.activity.compose)
 implementation(platform(libs.androidx.compose.bom))
 implementation(libs.androidx.ui)
 implementation(libs.androidx.ui.tooling.preview)
 implementation(libs.androidx.material3)
 implementation(libs.androidx.material.icons.extended)
 implementation(libs.androidx.core.ktx)
}
