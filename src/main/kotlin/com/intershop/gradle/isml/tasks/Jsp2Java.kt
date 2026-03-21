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
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.logging.LogLevel
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
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
@CacheableTask
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
     * Configuration, that is used for the jsp2java compilation.
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
     * @property encoding
     */
    @get:Input
    val encoding: Property<String> = objectFactory.property(String::class.java)

    /**
     * Input directory for ISML files.
     *
     * @property inputDir
     */
    @get:SkipWhenEmpty
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
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
     * @property jspClasspathfiles
     */
    @get:Classpath
    val jspClasspathfiles: ConfigurableFileCollection = objectFactory.fileCollection()

    /**
     * Classpath files of the isml configuration.
     *
     * @property classpathfiles
     */
    @get:Classpath
    val classpathfiles: ConfigurableFileCollection = objectFactory.fileCollection()

    /**
     * This is the task action and processes ISML files.
     */
    @TaskAction
    fun runJsp2Java() {
        //prepare output director
        val outputFolder = outputDir.get().asFile
        val webinf = File(outputFolder, IsmlExtension.WEB_XML_PATH)

        prepareFolder(outputFolder.parentFile)

        webinf.parentFile.mkdirs()
        webinf.writeText(IsmlExtension.WEB_XML_CONTENT)

        val classpathCollection = (listOf(outputFolder) + classpathfiles.files)

        val runLoggerLevel = when(logging.level) {
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

            it.logLevel.set(runLoggerLevel)
            it.classpath.set(classpathCollection.joinToString(File.pathSeparator))
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
