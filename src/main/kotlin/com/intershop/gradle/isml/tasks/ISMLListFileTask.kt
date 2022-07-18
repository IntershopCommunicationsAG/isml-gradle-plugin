/*
 * Copyright 2022 Intershop Communications AG.
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

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.IOException
import javax.inject.Inject

abstract class ISMLListFileTask
    @Inject constructor(objectFactory: ObjectFactory,
                                       private val fileSystemOps: FileSystemOperations) : DefaultTask() {

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
     * List of excludes patterns for input directory.
     * @property excludes
     */
    @get:Input
    val excludes: ListProperty<String> = objectFactory.listProperty(String::class.java)

    /**
     * Add an exclude pattern to the list of excludes.
     * @param exclude file pattern in ant style
     */
    fun exclude(exclude: String) {
        excludes.add(exclude)
    }

    /**
     * List of includes patterns for input directory.
     *
     * @property includes
     */
    @get:Input
    val includes: ListProperty<String> = objectFactory.listProperty(String::class.java)

    /**
     * Add an include pattern to the list of includes.
     * @param include file pattern in ant style
     */
    fun include(include: String) {
        includes.add(include)
    }

    /**
     * This is the filename of the artifact with
     * the created resource list.
     *
     * @property resourceListFileName
     */
    @get:Input
    val listFileName: Property<String> = objectFactory.property(String::class.java)

    /**
     * File extension is used for the creation of the
     * resource list from resource files.
     *
     * @property fileExtension
     */
    @get:Input
    val fileExtension: Property<String> = objectFactory.property(String::class.java)

    /**
     * This is logic of the task, that creates
     * the resource list artifact.
     */
    @TaskAction
    fun create() {
        val targetFile = File(outputDir.get().asFile, listFileName.get())

        if(targetFile.exists()) {
            fileSystemOps.delete {
                it.delete(targetFile)
            }
        }

        try {
            val setFilePaths = hashSetOf<String>()
            val dir = inputDir.asFile.get()
            //set content

            project.fileTree(dir) {
                it.includes.addAll(includes.get())
                it.excludes.addAll(excludes.get())
            }.files.filter { !it.isDirectory }.forEach { file ->
                setFilePaths.add(file.path.substring(dir.path.length + 1))
            }



            if (setFilePaths.isNotEmpty()) {
                targetFile.parentFile.mkdirs()
                targetFile.createNewFile()
                File(targetFile.absolutePath).printWriter().use { out ->
                    setFilePaths.forEach {
                        val entry = it.replace("\\", "/").replaceFirst(".${fileExtension.get()}", "").replace("/", ".")
                        if (logger.isDebugEnabled) {
                            logger.debug("'{}' will be added to list.", entry)
                        }

                        out.println(entry)
                    }
                }
            } else {
                project.logger.quiet("Collection of files is empty for {}", project.name)
            }
        } catch (ex: IOException) {
            throw GradleException("File operation for ${this.name} failed (${ex.message}).")
        }
    }
}