import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

// 不再用 jvmToolchain(17) 锁定 JDK 版本：用运行 Gradle 的 JDK 编译，产物仍是 17 字节码，和其他模块保持一致
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // RouteEngine 读写配置 JSON：Android 自带 org.json，这里只在编译和单元测试时引入
    compileOnly(libs.org.json)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
