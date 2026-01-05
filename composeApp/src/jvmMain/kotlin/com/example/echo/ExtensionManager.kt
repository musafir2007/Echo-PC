package com.example.echo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.mutableStateListOf
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.net.URLClassLoader

object ExtensionManager {
    private val extensionDir = File(System.getProperty("user.home"), ".echo_extensions").apply { mkdirs() }
    val installedExtensions = mutableStateListOf<Extension>()

    fun installFromFile(file: File): Boolean {
        return try {
            if (file.extension.lowercase() != "jar") return false
            val target = File(extensionDir, file.name)
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            loadExtension(target)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun installFromLink(url: String): Boolean {
        return try {
            val fileName = url.substringAfterLast("/").substringBefore("?")
            if (!fileName.endsWith(".jar")) return false
            val target = File(extensionDir, fileName)
            URI(url).toURL().openStream().use { input ->
                Files.copy(input, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            loadExtension(target)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadExtension(file: File) {
        try {
            val jarFile = JarFile(file)
            val entries = jarFile.entries()
            var mainClass: String? = null

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".class")) {
                    val className = entry.name.replace("/", ".").removeSuffix(".class")
                    if (className.contains("Extension") && !className.contains("$")) {
                        mainClass = className
                        break
                    }
                }
            }

            if (mainClass != null) {
                val loader = URLClassLoader(arrayOf(file.toURI().toURL()), this.javaClass.classLoader)
                val clazz = loader.loadClass(mainClass)
                
                if (Extension::class.java.isAssignableFrom(clazz)) {
                    val instance = clazz.getDeclaredConstructor().newInstance() as Extension
                    installedExtensions.add(instance)
                }
            } else {
                val newExt = object : Extension(
                    name = file.nameWithoutExtension,
                    description = "Dynamic extension from ${file.name}",
                    icon = Icons.Default.Extension,
                    category = ExtensionCategory.Misc,
                    version = "v1.0.0",
                    type = "Installed",
                    initialEnabled = true
                ) {}
                installedExtensions.add(newExt)
            }
            jarFile.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadAll() {
        extensionDir.listFiles { f -> f.extension == "jar" }?.forEach {
            loadExtension(it)
        }
    }
}
