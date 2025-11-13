import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.hpp.daftree"
    compileSdk = 35

    packagingOptions {
        pickFirst("META-INF/DEPENDENCIES")
        // إضافة لتحسين معالجة الملفات الأصلية
        jniLibs {
            keepDebugSymbols += listOf("**/*.so")
        }
        resources {
            excludes += listOf("**/lib/*/libgpuimage.so")
        }
    }

    defaultConfig {
        applicationId = "com.hpp.daftree"
        minSdk = 24
        targetSdk = 35
        versionCode = 17
        versionName = "1.1.7"
        multiDexEnabled = true
        buildConfigField("int", "VERSION_CODE", versionCode.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    applicationVariants.all {
        outputs.all {
            val outputImpl = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            // تنسيق التاريخ والوقت (yyyyMMdd-HHmm)
            val dateFormat = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault())
            val formattedDate = dateFormat.format(Date())
            outputImpl.outputFileName = "MySmartWallet-v${versionName}-${formattedDate}.apk"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isShrinkResources = true
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // إضافة Native Debug Symbols لحل التحذير
            isDebuggable = false
            isJniDebuggable = false
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            // لتجربة حجم التطبيق أثناء التطوير
            isMinifyEnabled = false
            isShrinkResources = false
//            isDebuggable = true
//            proguardFiles(
//                getDefaultProguardFile("proguard-android-optimize.txt"),
//                "proguard-rules.pro"
//            )
            // إضافة Native Debug Symbols للإصدار التجريبي أيضاً
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    packaging {
        jniLibs.keepDebugSymbols.add("**/*.so")
    }
    buildFeatures {
        viewBinding = true
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // إضافة مهمة لإنشاء Native Debug Symbols تلقائياً
    afterEvaluate {
        tasks.forEach { task ->
            if (task.name.contains("assemble") && task.name.contains("Release")) {
                task.doLast {
                    val symbolDir = File("$buildDir/native-debug-symbols/release")
                    symbolDir.mkdirs()
                    println("Native debug symbols directory created at: ${symbolDir.absolutePath}")
                }
            }
        }
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

// 🔐 Custom Gradle task to encrypt HTML files in assets/
//        tasks.register("encryptHtmlAssets") {
//            doLast {
//                val assetsDir = File("${projectDir}/src/main/assets")
//                val key = "MyS3cureKey12345" // نفس المفتاح في SecureAssetLoader.java
//
//                val filesToEncrypt = listOf("index.html", "account_details.html")
//
//                val secretKeySpec = SecretKeySpec(key.toByteArray(), "AES")
//                val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
//                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
//
//                filesToEncrypt.forEach { fileName ->
//                    val file = File(assetsDir, fileName)
//                    if (file.exists()) {
//                        println("Encrypting asset: $fileName")
//
//                        val inputBytes = file.readBytes()
//                        val encryptedBytes = cipher.doFinal(inputBytes)
//                        val encoded = Base64.getEncoder().encode(encryptedBytes)
//
//                        // استبدال الملف الأصلي بالنسخة المشفرة
//                        file.writeBytes(encoded)
//                        println("✅ Encrypted: ${file.name}")
//                    } else {
//                        println("⚠️ File not found: ${file.name}")
//                    }
//                }
//            }
//        }
//
//// ⚙️ ربط المهمة تلقائيًا بعملية البناء
//tasks.whenTaskAdded {
//    if (name.contains("assemble", ignoreCase = true)) {
//        dependsOn("encryptHtmlAssets")
//    }
//}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")

    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")

    // Navigation Component
    implementation("androidx.navigation:navigation-ui:2.9.0")
    implementation("androidx.navigation:navigation-fragment:2.9.0")
    implementation("androidx.preference:preference:1.2.1")

    // Glide (لتحميل وعرض الصور)
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // Google APIs
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0") {
        exclude(group = "org.apache.httpcomponents")
    }
    implementation("com.google.http-client:google-http-client-gson:1.44.1")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("de.hdodenhof:circleimageview:3.1.0")

    // Retrofit و Gson لتحويل JSON
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")

    // FirebaseUI for Firestore
    implementation("com.firebaseui:firebase-ui-firestore:8.0.2")

    val room_version = "2.7.2"
    val lifecycle_version = "2.9.1"

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:$room_version")

    // ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")

    val work_version = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$work_version")
    implementation ("androidx.work:work-runtime:$work_version")
    implementation("androidx.work:work-multiprocess:$work_version")
    // Firebase BoM (Bill of Materials)
    implementation(platform("com.google.firebase:firebase-bom:33.14.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-crashlytics")

    // Google Sign-In
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    implementation("org.mozilla:rhino:1.7.14")
    implementation("com.itextpdf:itextpdf:5.5.13.2")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // Security
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("com.facebook.android:facebook-android-sdk:18.1.3")
    implementation("com.facebook.android:facebook-login:18.1.3")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.android.billingclient:billing:7.0.0")
    implementation("com.google.android.play:review:2.0.2")
   // implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")
}