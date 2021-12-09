/*
 * Copyright 2018 Intershop Communications AG.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intershop.gradle.isml.tasks

import com.intershop.gradle.isml.extension.IsmlExtension
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import org.apache.log4j.Level
import javax.inject.Inject

/**
 * Jsp2Java task class.
 * @param fileSystemOperations
 * @param workerExecutor
 */
open class Jsp2Java  @Inject constructor(
    objectFactory: ObjectFactory,
    @Internal val fileSystemOperations: FileSystemOperations,
    @Internal val workerExecutor: WorkerExecutor ) : DefaultTask() {

    /**
     * Output directory for generated files.
     *
     * @property outputDir
     */
    @get:OutputDirectory
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Configuration, that is used for the the jsp2java compiilation.
     *
     * @property jspConfigurationName
     */
    @get:Optional
    @get:Input
    val jspConfigurationName: Property<String> = objectFactory.property(String::class.java)

    /**
     * Source compatibility configuration for ISML processing.
     *
     * @property sourceCompatibility
     */
    @get:Input
    val sourceCompatibility: Property<String> = objectFactory.property(String::class.java)

    /**
     * Target compatibility configuration for ISML processing.
     *
     * @property targetCompatibility
     */
    @get:Input
    val targetCompatibility: Property<String> = objectFactory.property(String::class.java)

    /**
     * Encoding configuration for ISML processing.
     *
     * @property encodingProperty
     */
    @get:Input
    val encoding: Property<String> = objectFactory.property(String::class.java)

    /**
     * Input directory for TagLibs.
     *
     * @property tagLibsInputDir
     */
    @get:Optional
    @get:InputDirectory
    val tagLibsInputDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Input directory for ISML files.
     *
     * @property inputDir
     */
    @get:SkipWhenEmpty
    @get:InputDirectory
    val inputDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * JSP package configuration property.
     *
     * @property jspPackage
     */
    @get:Input
    val jspPackage: Property<String> = objectFactory.property(String::class.java)

    /**
     * Java source set name.
     *
     * @property sourceSetName
     */
    @get:Optional
    @get:Input
    val sourceSetName: Property<String> = objectFactory.property(String::class.java)

    /**
     * Classpath files of the tools configuration.
     *
     * @property classpathfiles
     */
    @get:Classpath
    val jspClasspathfiles : FileCollection by lazy {
        val returnFiles = project.files()
        // find files of original JASPER and Eclipse compiler
        returnFiles.from(project.configurations.findByName(IsmlExtension.JASPERCOMPILER_CONFIGURATION_NAME))
        returnFiles
    }

    /**
     * Classpath files of the isml configuration.
     *
     * @property classpathfiles
     */
    @get:Classpath
    val classpathfiles : FileCollection by lazy {
        val returnFiles = project.files()

        returnFiles.from(project.configurations.findByName(jspConfigurationName.get())?.filter {
            it.name.endsWith(".jar") && ! (it.name.startsWith("logback-classic") && ! it.path.contains("wrapper"))
        })

        returnFiles
    }

    /**
     * List of filenames, that should be excluded from the JspC file scan.
     * The file name is check for the beginning of the name.
     *
     * @property tldScanExcludes
     */
    @get:Input
    val tldScanExcludes: ListProperty<String> = objectFactory.listProperty(String::class.java)

    /**
     * List of filenames, that should be included in the JspC file scan.
     * The file name is check for the beginning of the name.
     *
     * The default value will be generated from the classpath if exclude list is empty.
     *
     * @property tldScanIncludes
     */
    @get:Input
    val tldScanIncludes: ListProperty<String> = objectFactory.listProperty(String::class.java)

    /**
     * This will enable the TLD file scan of Jsp Compiler. It is only necessary if
     * TLDs are used and available.
     * Default value is false.
     *
     * @property enableTldScan
     */
    @get:Input
    val enableTldScan: Property<Boolean> = objectFactory.property(Boolean::class.java)

    init {
        enableTldScan.convention(false)
    }

    /**
     * This is the task action and processes ISML files.
     */
    @TaskAction
    fun runJsp2Java() {
        //prepare output director
        val outputFolder = outputDir.get().asFile
        val webinf = File(outputFolder, IsmlExtension.WEB_XML_PATH)

        prepareFolder(outputFolder.parentFile)

        if(tagLibsInputDir.isPresent) {
            // copy taglib conf files with web-inf to the uriroot
            fileSystemOperations.copy {
                it.from(tagLibsInputDir)
                it.into(outputFolder)
            }
        } else {
            // create web-inf in uriroot
            webinf.parentFile.mkdirs()
            webinf.writeText(IsmlExtension.WEB_XML_CONTENT)
        }

        val classpathCollection = project.files(outputFolder, classpathfiles)

        val runLoggerLevel = when(project.logging.level) {
            LogLevel.INFO -> Level.INFO
            LogLevel.DEBUG -> Level.DEBUG
            LogLevel.ERROR -> Level.ERROR
            else -> Level.WARN
        }

        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(jspClasspathfiles)
        }

        // start runner
        workQueue.submit(Jsp2JavaRunner::class.java) {
            it.outputDir.set(outputDir)
            it.inputDir.set(inputDir)

            it.encoding.set(encoding)
            it.jspPackage.set(jspPackage)
            it.sourceCompatibility.set(sourceCompatibility)
            it.targetCompatibility.set(targetCompatibility)

            it.tldScanIncludes.set(tldScanIncludes)
            it.tldScanExcludes.set(tldScanExcludes)
            it.enableTldScan.set(enableTldScan)
            it.logLevel.set(runLoggerLevel)
            it.classpath.set(classpathCollection.asPath)
        }

        workerExecutor.await()

        fileSystemOperations.delete {
            it.delete(webinf.parentFile)
        }
    }

    private fun prepareFolder(folder: File) {
        folder.deleteRecursively()
        folder.mkdirs()
    }
}
