plugins { id("com.android.application") }
android {
 namespace = "dev.opd2515.audiomanager"
 compileSdk = 36
 defaultConfig { applicationId = "dev.opd2515.audiomanager"; minSdk = 31; targetSdk = 36; versionCode = 1; versionName = "0.1-test" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
