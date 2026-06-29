import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    java
}

abstract class GeneratePluginIconTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceIcon: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val sourceFile = sourceIcon.get().asFile
        val image = ImageIO.read(sourceFile)
            ?: error("Unable to read plugin icon: ${sourceFile.absolutePath}")
        val iconSize = 80
        val scaled = BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB)
        val graphics = scaled.createGraphics()

        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.drawImage(image, 0, 0, iconSize, iconSize, null)
        } finally {
            graphics.dispose()
        }

        val pngBytes = ByteArrayOutputStream()
        ImageIO.write(scaled, "png", pngBytes)
        val encoded = Base64.getEncoder().encodeToString(pngBytes.toByteArray())
        val metaInfDir = outputDir.get().dir("META-INF").asFile
        metaInfDir.mkdirs()
        metaInfDir.resolve("pluginIcon.svg").writeText(
            """
            <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40">
              <image width="40" height="40" preserveAspectRatio="xMidYMid meet" href="data:image/png;base64,$encoded"/>
            </svg>
            """.trimIndent(),
            Charsets.UTF_8
        )
    }
}

val pluginName = "alfred"

val clionHome = providers.gradleProperty("clionHome")
    .orElse(providers.environmentVariable("CLION_HOME"))
    .orElse(providers.provider {
        listOf(
            "D:/JetBrains/CLion",
            "C:/Program Files/JetBrains/CLion",
            "/Applications/CLion.app/Contents",
            "/opt/clion",
            "/snap/clion/current"
        ).firstOrNull { file(it).isDirectory }
            ?: throw GradleException(
                "CLion SDK not found. Set -PclionHome=<CLion install dir> or CLION_HOME. " +
                    "On macOS, use /Applications/CLion.app/Contents."
            )
    })

dependencies {
    compileOnly(fileTree(clionHome.map { "$it/lib" }) { include("*.jar") })
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

tasks.jar {
    archiveBaseName = pluginName
}

val pluginIconSource = layout.projectDirectory.file("src/main/resources/META-INF/Alfred.png")
val generatedPluginIconDir = layout.buildDirectory.dir("generated/plugin-icon")

val generatePluginIcon = tasks.register<GeneratePluginIconTask>("generatePluginIcon") {
    sourceIcon.set(pluginIconSource)
    outputDir.set(generatedPluginIconDir)
}

tasks.processResources {
    dependsOn(generatePluginIcon)
    from(generatedPluginIconDir)
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
