plugins {
    id("application")
}

repositories { mavenCentral() }

val lwjglVersion = "3.3.4"
val gsonVersion = "2.11.0"

// use the LWJGL BOM to keep versions aligned
dependencies {
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))

    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-stb")

    // pick correct natives
    val natives = when {
        System.getProperty("os.name").startsWith("Windows", true) -> "natives-windows"
        System.getProperty("os.name").startsWith("Linux", true)   -> "natives-linux"
        System.getProperty("os.name").startsWith("Mac", true)     -> "natives-macos"
        else -> error("Unsupported OS: ${System.getProperty("os.name")}")
    }

    runtimeOnly("org.lwjgl:lwjgl::$natives")
    runtimeOnly("org.lwjgl:lwjgl-glfw::$natives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$natives")
    runtimeOnly("org.lwjgl:lwjgl-stb::$natives")

    implementation("com.google.code.gson:gson:$gsonVersion")
}


application {
    mainClass.set("app.Main")

    val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
    applicationDefaultJvmArgs = if (isMac) listOf("-XstartOnFirstThread") else emptyList()
}


java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}
