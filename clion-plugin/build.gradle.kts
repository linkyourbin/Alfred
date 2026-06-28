plugins {
    java
}

val clionHome = providers.gradleProperty("clionHome").orElse("D:/JetBrains/CLion")
val pluginName = "alfred"

dependencies {
    compileOnly(fileTree(clionHome.map { "$it/lib" }) { include("*.jar") })
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.jar {
    archiveBaseName = pluginName
}

val buildPlugin = tasks.register<Zip>("buildPlugin") {
    group = "build"
    description = "Builds an installable CLion plugin ZIP."
    archiveFileName = "$pluginName.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    into(pluginName) {
        from(rootProject.file("LICENSE"))
        from(rootProject.file("NOTICE"))
        from(rootProject.file("README.md"))
        from(rootProject.file("CHANGELOG.md"))
        into("lib") {
            from(tasks.jar)
        }
    }
}

tasks.assemble {
    dependsOn(buildPlugin)
}
