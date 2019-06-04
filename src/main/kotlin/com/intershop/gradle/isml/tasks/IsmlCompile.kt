/*
 * Copyright 2018 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intershop.gradle.isml.tasks

import com.intershop.gradle.isml.extension.IsmlExtension
import org.apache.log4j.Level
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.process.JavaForkOptions
import org.gradle.workers.ForkMode
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject
import kotlin.reflect.KProperty

/**
 * Add a set function to a String property.
 */
operator fun <T> Property<T>.setValue(receiver: Any?, property: KProperty<*>, value: T) = set(value)
/**
 * Add a get function to a String property.
 */
operator fun <T> Property<T>.getValue(receiver: Any?, property: KProperty<*>): T = get()

/**
 * Task for compiling isml to class files.
 */
open class IsmlCompile @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask(){

    companion object {
        const val PAGECOMPILE_FOLDER = "pagecompile"
        const val FILTER_JSP = "**/**/*.jsp"
    }

    private val outputDirProperty: DirectoryProperty = project.objects.directoryProperty()

    /**
     * Output directory for generated files.
     *
     * @property outputDir
     */
    @get:OutputDirectory
    var outputDir: File
        get() = outputDirProperty.get().asFile
        set(value)= outputDirProperty.set(value)

    /**
     * Add provider for outputDir.
     */
    fun provideOutputDir(outputDir: Provider<Directory>) = outputDirProperty.set(outputDir)

    val tagLibsInputDirProperty: DirectoryProperty = project.objects.directoryProperty()

    /**
     * Input directory for TagLibs.
     *
     * @property tagLibsInputDir
     */
    @get:Optional
    @get:InputDirectory
    val tagLibsInputDir: File?
        get() {
            return if(tagLibsInputDirProperty.orNull != null) {
                tagLibsInputDirProperty.get().asFile
            } else {
                null
            }
        }

    /**
     * Add provider for taglibs dir.
     */
    fun provideTagLibsInputDir(tagLibsInputDir: Provider<Directory>) = tagLibsInputDirProperty.set(tagLibsInputDir)

    private val inputDirProperty: DirectoryProperty = project.objects.directoryProperty()

    /**
     * Input directory for ISML files.
     *
     * @property inputDir
     */
    @get:SkipWhenEmpty
    @get:InputDirectory
    var inputDir: File
        get() = inputDirProperty.get().asFile
        set(value) = inputDirProperty.set(value)

    /**
     * Add provider for inputDir.
     */
    fun provideInputDir(inputDir: Provider<Directory>) = inputDirProperty.set(inputDir)

    // (java) configuration name for isml compilation
    private val ismlConfigurationProperty: Property<String> = project.objects.property(String::class.java)

    /**
     * ISMl configuration property.
     *
     * @property ismlConfiguration
     */
    @get:Input
    var ismlConfiguration by ismlConfigurationProperty

    /**
     * Add provider for ismlConfiguration.
     */
    fun provideIsmlConfiguration(ismlConfiguration: Provider<String>) = ismlConfigurationProperty.set(ismlConfiguration)

    private val jspPackageProperty: Property<String> = project.objects.property(String::class.java)

    /**
     * JSP package configuration property.
     *
     * @property jspPackage
     */
    @get:Input
    var jspPackage by jspPackageProperty

    /**
     * Add provider for jspPackage.
     */
    fun provideJspPackage(jspPackage: Provider<String>) = jspPackageProperty.set(jspPackage)

    // java source set name
    private val soureSetNameProperty: Property<String> = project.objects.property(String::class.java)

    /**
     * Java source set name.
     *
     * @property sourceSetName
     */
    @get:Optional
    @get:Input
    var sourceSetName by soureSetNameProperty

    /**
     * Add provider for sourceSetName.
     */
    fun provideSourceSetName(sourceSetName: Provider<String>) = soureSetNameProperty.set(sourceSetName)

    private val sourceCompatibilityProperty: Property<String> = project.objects.property(String::class.java)

    /**
     * Source compatibility configuration for ISML processing.
     *
     * @property sourceCompatibility
     */
    @get:Input
    var sourceCompatibility by sourceCompatibilityProperty

    /**
     * Add provider for sourceCompatibility.
     */
    fun provideSourceCompatibility(sourceCompatibility: Provider<String>) =
            sourceCompatibilityProperty.set(sourceCompatibility)

    private val targetCompatibilityProperty: Property<String> = project.objects.property(String::class.java)

    /**
     * Target compatibility configuration for ISML processing.
     *
     * @property targetCompatibility
     */
    @get:Input
    var targetCompatibility by targetCompatibilityProperty

    /**
     * Add provider for targetCompatibility.
     */
    fun provideTargetCompatibility(targetCompatibility: Provider<String>) =
            targetCompatibilityProperty.set(targetCompatibility)

    private val encodingProperty: Property<String> = project.objects.property(String::class.java)

    /**
     * Encoding configuration for ISML processing.
     *
     * @property encodingProperty
     */
    @get:Input
    var encoding by encodingProperty

    /**
     * Add provider for encoding.
     */
    fun provideEncoding(encoding: Provider<String>) = encodingProperty.set(encoding)

    // internal properties
    @get:InputFiles
    private val classpathfiles : FileCollection by lazy {
        val returnFiles = project.files()

        // search all files for classpath
        if(project.convention.findPlugin(JavaPluginConvention::class.java) != null) {
            val javaConvention = project.convention.getPlugin(JavaPluginConvention::class.java)
            val mainSourceSet = javaConvention.sourceSets.getByName(sourceSetName)

            returnFiles.from(mainSourceSet.output.classesDirs, mainSourceSet.output.resourcesDir)
        }
        returnFiles.from(project.configurations.findByName(ismlConfiguration)?.filter {
            it.name.endsWith(".jar")
        })

        returnFiles
    }

    @get:InputFiles
    private val toolsclasspathfiles : FileCollection by lazy {
        val returnFiles = project.files()
        // find files of original JASPER and Eclipse compiler
        returnFiles.from(project.configurations.findByName(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME))
        returnFiles.from(project.configurations.findByName(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME))
        returnFiles
    }

    private var internalForkOptionsAction: Action<in JavaForkOptions>? = null

    /**
     * Adds additional fork options.
     */
    fun forkOptions(forkOptionsAction: Action<in JavaForkOptions>) {
        internalForkOptionsAction = forkOptionsAction
    }

    /**
     * This is the task action and processes ISML files.
     */
    @TaskAction
    fun ismlcompile() {
        //prepare output director
        prepareFolder(outputDir)
        val pageCompileFolder = File(outputDir, PAGECOMPILE_FOLDER)
        val webinf = File(pageCompileFolder, IsmlExtension.WEB_XML_PATH)

        if(tagLibsInputDir != null) {
            // copy taglib conf files with web-inf to the uriroot
            project.copy {
                it.from(tagLibsInputDir)
                it.into(pageCompileFolder)
            }
        } else {
            // create web-inf in uriroot
            webinf.parentFile.mkdirs()
            webinf.writeText(IsmlExtension.WEB_XML_CONTENT)
        }

        // copy source jsp files
        project.copy {
            it.from(inputDir) {
                it.include(FILTER_JSP)
            }
            it.into(pageCompileFolder)
        }

        // tool specific files must be in front of all other to override the server specific files
        val classpathCollection = project.files(toolsclasspathfiles, classpathfiles, pageCompileFolder)

        var runLoggerLevel = when(project.logging.level) {
            LogLevel.INFO -> Level.INFO
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.ERROR -> Level.ERROR
            else -> Level.WARN
        }

        // start compiler runner
        workerExecutor.submit(IsmlCompileRunner::class.java, {
            it.displayName = "Worker compiles ISML files to class files."
            it.setParams(
                    inputDir,
                    pageCompileFolder,
                    encoding,
                    jspPackage,
                    sourceCompatibility,
                    targetCompatibility,
                    File(temporaryDir, "eclipsecompiler.config"),
                    File(temporaryDir, "compiler-out.log"),
                    File(temporaryDir, "compiler-error.log"),
                    classpathCollection.asPath,
                    webinf.parentFile,
                    runLoggerLevel)
            it.classpath(classpathCollection)
            it.isolationMode = IsolationMode.PROCESS
            it.forkMode = ForkMode.AUTO
            if(internalForkOptionsAction != null) {
                project.logger.debug("ISML compile runner Add configured JavaForkOptions.")
                internalForkOptionsAction?.execute(it.forkOptions)
            }

        })

        workerExecutor.await()
    }

    private fun prepareFolder(folder: File) {
        folder.deleteRecursively()
        folder.mkdirs()
    }
}
