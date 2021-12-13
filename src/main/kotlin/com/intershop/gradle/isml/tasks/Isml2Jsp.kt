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
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
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
 * @param fileSystemOperations
 * @param workerExecutor    run the workload of this task
 * @param objectFactory     creates all configurations of this task
 */
open class Isml2Jsp @Inject constructor(
    objectFactory: ObjectFactory,
    @Internal val fileSystemOperations: FileSystemOperations,
    @Internal val workerExecutor: WorkerExecutor) : DefaultTask() {

    companion object {
        /**
         * Variable for default JSP filter string.
         */
        const val FILTER_JSP = "**/**/*.jsp"
    }

    /**
     * Output directory for this task.
     *
     * @property outputDir
     */
    @get:OutputDirectory
    val outputDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Input directory for ISML files.
     *
     * @property inputDir
     */
    @get:SkipWhenEmpty
    @get:InputDirectory
    val inputDir: DirectoryProperty = objectFactory.directoryProperty()

    /**
     * Encoding configuration for ISML processing.
     *
     * @property encodingProperty
     */
    @get:Input
    val encoding: Property<String> = objectFactory.property(String::class.java)

    /**
     * Classpath files for Java code generation (see ISML configuration).
     *
     * @property ismlClasspathfiles
     */
    @get:Classpath
    val ismlClasspathfiles: FileCollection by lazy {
        val returnFiles = project.files()
        returnFiles.from(project.configurations.findByName(IsmlExtension.ISMLCOMPILER_CONFIGURATION_NAME))
        returnFiles
    }

    /**
     * This is the task action and processes ISML files.
     */
    @TaskAction
    fun runIsml2Jsp() {
        val outputFolder = outputDir.get().asFile
        //prepare output director
        prepareFolder(outputFolder)

        val workQueue = workerExecutor.classLoaderIsolation {
            it.classpath.setFrom(ismlClasspathfiles)
        }

        // copy source jsp files
        fileSystemOperations.copy {
            it.from(inputDir)
            it.include(FILTER_JSP)
            it.into(outputFolder)
        }

        val jspEncoding = mutableMapOf("text/html" to encoding.get())

        // start runner
        workQueue.submit(Isml2JspRunner::class.java) {
            it.outputDir.set(outputDir)
            it.inputDir.set(inputDir)
            it.encoding.set(encoding)
            it.jspEncoding.putAll(jspEncoding)
        }

        workerExecutor.await()
    }

    private fun prepareFolder(folder: File) {
        folder.deleteRecursively()
        folder.mkdirs()
    }
}
