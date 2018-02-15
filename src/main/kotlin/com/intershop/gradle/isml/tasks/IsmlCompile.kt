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
import com.intershop.gradle.isml.tasks.IsmlCompile.Companion.FILTER_JSP
import com.intershop.gradle.isml.tasks.IsmlCompile.Companion.PAGECOMPILE_FOLDER
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.process.JavaForkOptions
import org.gradle.workers.ForkMode
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject
import kotlin.reflect.KProperty

operator fun <T> Property<T>.setValue(receiver: Any?, property: KProperty<*>, value: T) = set(value)
operator fun <T> Property<T>.getValue(receiver: Any?, property: KProperty<*>): T = get()

open class IsmlCompile @Inject constructor(private val workerExecutor: WorkerExecutor) : DefaultTask(){

    companion object {
        const val PAGECOMPILE_FOLDER = "pagecompile"
        const val FILTER_JSP = "**/**/*.jsp"
    }

    val outputDirProperty: DirectoryProperty = this.newOutputDirectory()

    @get:OutputDirectory
    var outputDir: File
        get() = outputDirProperty.get().asFile
        set(value)= outputDirProperty.set(value)

    fun provideOutputDir(outputDir: Provider<Directory>) = outputDirProperty.set(outputDir)

    // folder with taglibs
    val tagLibsInputDirProperty: DirectoryProperty = this.newInputDirectory()

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

    fun provideTagLibsInputDir(tagLibsInputDir: Provider<Directory>) = tagLibsInputDirProperty.set(tagLibsInputDir)

    // folder with isml sources
    private val inputDirProperty: DirectoryProperty = this.newInputDirectory()

    @get:SkipWhenEmpty
    @get:InputDirectory
    var inputDir: File
        get() = inputDirProperty.get().asFile
        set(value) = inputDirProperty.set(value)

    fun provideInputDir(inputDir: Provider<Directory>) = inputDirProperty.set(inputDir)

    // (java) configuration name for isml compilation
    private val ismlConfigurationProperty: Property<String> = project.objects.property(String::class.java)

    @get:Input
    var ismlConfiguration by ismlConfigurationProperty

    fun provideIsmlConfiguration(ismlConfiguration: Provider<String>) = ismlConfigurationProperty.set(ismlConfiguration)

    // jsp package path
    private val jspPackageProperty: Property<String> = project.objects.property(String::class.java)

    @get:Input
    var jspPackage by jspPackageProperty

    fun provideJspPackage(jspPackage: Provider<String>) = jspPackageProperty.set(jspPackage)

    // java source set name
    private val soureSetNameProperty: Property<String> = project.objects.property(String::class.java)

    @get:Optional
    @get:Input
    var sourceSetName by soureSetNameProperty

    fun provideSourceSetName(sourceSetName: Provider<String>) = soureSetNameProperty.set(sourceSetName)

    // java source compatibility
    private val sourceCompatibilityProperty: Property<String> = project.objects.property(String::class.java)

    @get:Input
    var sourceCompatibility by sourceCompatibilityProperty

    fun provideSourceCompatibility(sourceCompatibility: Provider<String>) = sourceCompatibilityProperty.set(sourceCompatibility)

    // java target compatibility
    private val targetCompatibilityProperty: Property<String> = project.objects.property(String::class.java)

    @get:Input
    var targetCompatibility by targetCompatibilityProperty

    fun provideTargetCompatibility(targetCompatibility: Provider<String>) = targetCompatibilityProperty.set(targetCompatibility)

    // encoding for files
    private val encodingProperty: Property<String> = project.objects.property(String::class.java)

    @get:Input
    var encoding by encodingProperty

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