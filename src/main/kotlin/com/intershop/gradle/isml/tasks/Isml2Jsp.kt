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

import com.intershop.gradle.isml.utils.getValue
import com.intershop.gradle.isml.utils.setValue
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
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
import javax.inject.Inject

/**
 * Task for compiling isml to jsp files.
 *
 * @constructor creates an instance with worker
 * @param workerExecutor    run the workload of this task
 * @param objectFactory     creates all configurations of this task
 */
open class Isml2Jsp @Inject constructor(
    objectFactory: ObjectFactory,
    @Internal val workerExecutor: WorkerExecutor) : DefaultTask() {

    private val outputDirProperty: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Output directory for generated files.
     *
     * @property outputDir
     */
    @get:OutputDirectory
    var outputDir: File
        get() = outputDirProperty.get().asFile
        set(value) = outputDirProperty.set(value)

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
     * Classpath files for Java code generation (see ISML configuration).
     *
     * @property ismlClasspathfiles
     */
    @get:Classpath
    val ismlClasspathfiles: FileCollection by lazy {
        val returnFiles = project.files()
        returnFiles.from(project.configurations.findByName("isml"))
        returnFiles
    }

    /**
     * This is the task action and processes ISML files.
     */
    @TaskAction
    fun runIsml2Jsp() {
        //prepare output director
        prepareFolder(outputDir)

        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(ismlClasspathfiles)
        }

        // start runner
        workQueue.submit(Isml2JspRunner::class.java) {
            it.outputDir.set(outputDir)
            it.inputDir.set(inputDir)
        }

        workerExecutor.await()
    }

    private fun prepareFolder(folder: File) {
        folder.deleteRecursively()
        folder.mkdirs()
    }
}
