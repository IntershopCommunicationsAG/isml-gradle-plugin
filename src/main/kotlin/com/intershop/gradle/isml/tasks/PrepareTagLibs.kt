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
import com.intershop.gradle.isml.tasks.data.TagLibConf
import com.intershop.gradle.isml.tasks.data.TagLibConfDir
import com.intershop.gradle.isml.tasks.data.TagLibConfZip
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.util.stream.Collectors
import java.util.zip.ZipFile

open class PrepareTagLibs : DefaultTask() {

    companion object {

        // static files of a cartridge
        const val CARTRIDGE_STATIC_FOLDER = "staticfiles/cartridge"

        // released static files of a cartridge
        const val RELEASE_STATIC_FOLDER = "release"

        // folder with tag lib definitions of a cartridge
        const val TAGLIB_FOLDER = "tags"
    }

    @Internal
    val outputDirProperty: DirectoryProperty = newOutputDirectory()

    val outputDir: File
        @OutputDirectory
        get() {
            return outputDirProperty.get().asFile
        }

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

    @get:Nested
    private val taglibConfigurations: List<TagLibConf> by lazy {
        val returnList = arrayListOf<TagLibConf>()

        with(project.file("$CARTRIDGE_STATIC_FOLDER/$TAGLIB_FOLDER")) {
            if(exists() && listFiles().isNotEmpty()) {
                returnList.add(TagLibConfDir(this, project.name))
            }
        }

        project.configurations.findByName(ismlConfiguration)?.let {
            it.dependencies.withType(ProjectDependency::class.java).forEach {

                project.logger.debug("Project dependency found: {}", it.dependencyProject.name)
                with(it.dependencyProject.file("$CARTRIDGE_STATIC_FOLDER/$TAGLIB_FOLDER")) {
                    if(exists() && listFiles().isNotEmpty()) {
                        returnList.add(TagLibConfDir(this, it.dependencyProject.name))
                    }
                }
            }
        }

        project.configurations.findByName(ismlConfiguration)?.let {
            if(it.isCanBeResolved) {
                it.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    if (artifact.type == "cartridge") {
                        val zipFile = ZipFile(artifact.file)
                        val list = zipFile.stream().filter { zipEntry -> zipEntry.name.matches(".*/$RELEASE_STATIC_FOLDER/$TAGLIB_FOLDER.+".toRegex()) }
                                .map { entry -> entry.name }
                                .collect(Collectors.toList())
                        if (list.size > 0) {
                            returnList.add(TagLibConfZip(artifact.file, artifact.name))
                        }
                    }
                }
            } else {
                project.logger.warn("Configuration '{}' can not be resolved!", ismlConfiguration)
            }
        }

        returnList
    }

    @TaskAction
    fun runTagLibPreparation() {
        val webinf = File(outputDir, IsmlExtension.WEB_XML_PATH)
        webinf.parentFile.mkdirs()
        webinf.writeText(IsmlExtension.WEB_XML_CONTENT)

        val targetFile = File(webinf.parentFile, TAGLIB_FOLDER)

        project.logger.info("Copy {} project tag libs to project '{}'", taglibConfigurations.size, project.name)
        taglibConfigurations.forEach { taglibConf ->
            if(taglibConf is TagLibConfZip) {
                project.copy {
                    it.from(project.zipTree(taglibConf.conffile))
                    it.include("*/$RELEASE_STATIC_FOLDER/$TAGLIB_FOLDER/**/**")
                    it.eachFile { details ->
                        details.path = details.path.removePrefix("${taglibConf.projectName}/$RELEASE_STATIC_FOLDER/$TAGLIB_FOLDER/")
                    }
                    it.into(File(targetFile, taglibConf.projectName))
                }
            } else {
                project.copy {
                    it.from(taglibConf.conffile)
                    it.into(File(targetFile, taglibConf.projectName))
                }
            }
        }
    }
}