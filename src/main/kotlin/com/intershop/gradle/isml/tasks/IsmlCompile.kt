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
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.process.JavaForkOptions
import org.gradle.workers.ForkMode
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import org.jetbrains.annotations.NotNull
import java.io.File
import javax.inject.Inject

open class IsmlCompile @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask(){

    companion object {
        const val PAGECOMPILE_FOLDER = "pagecompile"
        const val FILTER_JSP = "**/**/*.jsp"
    }

    @Internal
    val outputDirProperty: DirectoryProperty = this.newOutputDirectory()

    var outputDir: File
        @OutputDirectory
        get() {
            return outputDirProperty.get().asFile
        }
        set(value) {
            outputDirProperty.set(value)
        }

    // folder with taglibs
    @Internal
    val tagLibsInputDirProperty: DirectoryProperty = this.newInputDirectory()

    val tagLibsInputDir: File?
        @Optional
        @InputDirectory
        get() {
            return if(tagLibsInputDirProperty.orNull != null) {
                tagLibsInputDirProperty.get().asFile
            } else {
                null
            }
        }

    // folder with isml sources
    @Internal
    val inputDirProperty: DirectoryProperty = this.newInputDirectory()

    var inputDir: File
        @SkipWhenEmpty
        @InputDirectory
        get() {
            return inputDirProperty.get().asFile
        }
        set(value) {
            inputDirProperty.set(value)
        }

    // (java) configuration name for isml compilation
    @Internal
    val ismlConfigurationProperty: Property<String> = project.objects.property(String::class.java)

    var ismlConfiguration: String
        @Input
        get() {
            return ismlConfigurationProperty.get()
        }
        set(value) {
            ismlConfigurationProperty.set(value)
        }

    // jsp package path
    @Internal
    val jspPackageProperty: Property<String> = project.objects.property(String::class.java)

    var jspPackage: String
        @Input
        get() {
            return jspPackageProperty.get()
        }
        set(value) {
            jspPackageProperty.set(value)
        }

    // java source set name
    @Internal
    val soureSetNameProperty: Property<String> = project.objects.property(String::class.java)

    var sourceSetName: String
        @Optional
        @Input
        get() {
            return soureSetNameProperty.get()
        }
        set(value) {
            soureSetNameProperty.set(value)
        }

    // java source compatibility
    @Internal
    val sourceCompatibilityProperty: Property<String> = project.objects.property(String::class.java)

    var sourceCompatibility: String
        @Input
        get() {
            return sourceCompatibilityProperty.get()
        }
        set(value) {
            sourceCompatibilityProperty.set(value)
        }

    // java target compatibility
    @Internal
    val targetCompatibilityProperty: Property<String> = project.objects.property(String::class.java)

    var targetCompatibility: String
        @Input
        get() {
            return targetCompatibilityProperty.get()
        }
        set(value) {
            targetCompatibilityProperty.set(value)
        }

    // encoding for files
    @Internal
    val encodingProperty: Property<String> = project.objects.property(String::class.java)

    var encoding: String
        @Input
        get() {
            return encodingProperty.get()
        }
        set(value) {
            encodingProperty.set(value)
        }

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
        returnFiles.from(project.configurations.findByName(ismlConfiguration)?.filter { it.name.endsWith(".jar") })

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

    /**
     * Java fork options for the Java task.
     */
    private var internalForkOptionsAction: Action<in JavaForkOptions>? = null

    fun forkOptions(forkOptionsAction: Action<in JavaForkOptions>) {
        internalForkOptionsAction = forkOptionsAction
    }

    @TaskAction
    fun ismlcompile() {
        //prepare output directory
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
                    webinf.parentFile)
            it.classpath(classpathCollection)
            it.isolationMode = IsolationMode.CLASSLOADER
            it.forkMode = ForkMode.AUTO
            if(internalForkOptionsAction != null) {
                project.logger.debug("ISML compile runner Add configured JavaForkOptions.")
                (internalForkOptionsAction as Action<in JavaForkOptions>).execute(it.forkOptions)
            }
        })

        workerExecutor.await()
    }


    private fun prepareFolder(folder: File) {
        folder.deleteRecursively()
        folder.mkdirs()
    }

}