plugins { `kotlin-dsl` }

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
    google()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
}

kotlin { jvmToolchain(21) }
