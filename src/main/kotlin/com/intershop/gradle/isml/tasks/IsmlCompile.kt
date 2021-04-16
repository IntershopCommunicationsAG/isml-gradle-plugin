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
@file:Suppress("UnstableApiUsage")
package com.intershop.gradle.isml.tasks

import com.intershop.gradle.isml.extension.IsmlExtension
import com.intershop.gradle.isml.utils.getValue
import com.intershop.gradle.isml.utils.setValue
import org.apache.log4j.Level
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.process.JavaForkOptions
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

/**
 * Task for compiling isml to class files.
 *
 * @constructor creates an instance with worker
 * @param workerExecutor
 */
open class IsmlCompile @Inject constructor(
        objectFactory: ObjectFactory,
        @Internal val fileSystemOperations: FileSystemOperations,
        @Internal val workerExecutor: WorkerExecutor) : DefaultTask(){

    companion object {
        /**
         * Variable for default page compile folder name.
         */
        const val PAGECOMPILE_FOLDER = "pagecompile"

        /**
         * Variable for default JSP filter string.
         */
        const val FILTER_JSP = "**/**/*.jsp"
    }

    private val outputDirProperty: DirectoryProperty = objectFactory.directoryProperty()

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

    /**
     * Input directory for TagLibs.
     *
     * @property tagLibsInputDir
     */
    @get:Optional
    @get:InputDirectory
    val tagLibsInputDir: DirectoryProperty = objectFactory.directoryProperty()

    private val inputDirProperty: DirectoryProperty = objectFactory.directoryProperty()

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
    private val ismlConfigurationProperty: Property<String> = objectFactory.property(String::class.java)

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

    private val jspPackageProperty: Property<String> = objectFactory.property(String::class.java)

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
    private val soureSetNameProperty: Property<String> = objectFactory.property(String::class.java)

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

    private val sourceCompatibilityProperty: Property<String> = objectFactory.property(String::class.java)

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

    private val targetCompatibilityProperty: Property<String> = objectFactory.property(String::class.java)

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

    private val encodingProperty: Property<String> = objectFactory.property(String::class.java)

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

    /**
     * Classpath files of the isml configuration.
     *
     * @property classpathfiles
     */
    @get:Classpath
    val classpathfiles : FileCollection by lazy {
        val returnFiles = project.files()

        // search all files for classpath
        if(project.convention.findPlugin(JavaPluginConvention::class.java) != null) {
            val javaConvention = project.convention.getPlugin(JavaPluginConvention::class.java)
            val mainSourceSet = javaConvention.sourceSets.getByName(sourceSetName)

            returnFiles.from(mainSourceSet.output.classesDirs, mainSourceSet.output.resourcesDir)
        }

        returnFiles.from(project.configurations.findByName(ismlConfiguration)?.filter {
            it.name.endsWith(".jar") && ! (it.name.startsWith("logback-classic") && ! it.path.contains("wrapper"))
        })

        returnFiles
    }

    /**
     * Classpath files of the tools configuration.
     *
     * @property classpathfiles
     */
    @get:Classpath
    val toolsclasspathfiles : FileCollection by lazy {
        val returnFiles = project.files()
        // find files of original JASPER and Eclipse compiler
        returnFiles.from(project.configurations.findByName(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME))
        returnFiles.from(project.configurations.findByName(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME))
        returnFiles
    }

    private val tldScanExcludesListProperty: ListProperty<String> = objectFactory.listProperty(String::class.java)

    /**
     * List of filenames, that should be excluded from the JspC file scan.
     * The file name is check for the beginning of the name.
     *
     * @property tldScanExcludes
     */
    @get:Input
    var tldScanExcludes by tldScanExcludesListProperty

    /**
     * Add provider for encoding.
     */
    fun provideTldScanExcludes(excludeList: Provider<List<String>>) =
            tldScanExcludesListProperty.set(excludeList)

    private val tldScanIncludesListProperty: ListProperty<String> = objectFactory.listProperty(String::class.java)

    /**
     * List of filenames, that should be included in the JspC file scan.
     * The file name is check for the beginning of the name.
     *
     * The default value will be generated from the classpath if exclude list is empty.
     *
     * @property tldScanIncludes
     */
    @get:Input
    var tldScanIncludes by tldScanIncludesListProperty

    /**
     * Add provider for tldScanIncludes.
     */
    fun provideTldScanIncludes(includeList: Provider<List<String>>) =
            tldScanExcludesListProperty.set(includeList)

    private val enableTldScanProperty: Property<Boolean> = objectFactory.property(Boolean::class.java)

    /**
     * This will enable the TLD file scan of Jsp Compiler. It is only necessary if
     * TLDs are used and available.
     * Default value is false.
     *
     * @property enableTldScan
     */
    @get:Input
    var enableTldScan by enableTldScanProperty

    /**
     * Add provider for enable TLD scan.
     */
    fun provideEnableTldScan(enableTldScan: Provider<Boolean>) = enableTldScanProperty.set(enableTldScan)

    init {
        enableTldScanProperty.convention(false)
    }

    /**
     * This is the task action and processes ISML files.
     */
    @TaskAction
    fun runIsmlCompile() {
        //prepare output director
        prepareFolder(outputDir)
        val pageCompileFolder = File(outputDir, PAGECOMPILE_FOLDER)
        val webinf = File(pageCompileFolder, IsmlExtension.WEB_XML_PATH)

        if(tagLibsInputDir.isPresent) {
            // copy taglib conf files with web-inf to the uriroot
            fileSystemOperations.copy {
                it.from(tagLibsInputDir)
                it.into(pageCompileFolder)
            }
        } else {
            // create web-inf in uriroot
            webinf.parentFile.mkdirs()
            webinf.writeText(IsmlExtension.WEB_XML_CONTENT)
        }

        // copy source jsp files
        fileSystemOperations.copy {
            it.from(inputDir)
            it.include(FILTER_JSP)
            it.into(pageCompileFolder)
        }

        // tool specific files must be in front of all other to override the server specific files
        val classpathCollection = project.files(toolsclasspathfiles, classpathfiles, pageCompileFolder)

        val runLoggerLevel = when(project.logging.level) {
            LogLevel.INFO -> Level.INFO
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.ERROR -> Level.ERROR
            else -> Level.WARN
        }

        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(classpathCollection)
        }

        workQueue.submit(IsmlCompileRunner::class.java) {
            it.sourceDir.set(inputDir)
            it.outputDir.set(pageCompileFolder)
            it.encoding.set(encoding)
            it.jspPackage.set(jspPackage)
            it.sourceCompatibility.set(sourceCompatibility)
            it.targetCompatibility.set(targetCompatibility)
            it.eclipseConfFile.set(File(temporaryDir, "eclipsecompiler.config"))
            it.compilerOut.set(File(temporaryDir, "compiler-out.log"))
            it.compilerError.set(File(temporaryDir, "compiler-error.log"))
            it.classpath.set(classpathCollection.asPath)
            it.tempWebInfFolder.set(webinf.parentFile)
            it.tldScanIncludes.set(tldScanIncludes)
            it.tldScanExcludes.set(tldScanExcludes)
            it.enableTldScan.set(enableTldScan)
            it.logLevel.set(runLoggerLevel)
        }

        workerExecutor.await()
    }

    private fun prepareFolder(folder: File) {
        folder.deleteRecursively()
        folder.mkdirs()
    }
}
