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

import com.intershop.beehive.isml.capi.ISMLCompilerConfiguration
import com.intershop.beehive.isml.capi.ISMLTemplateConstants
import com.intershop.beehive.isml.internal.TemplatePrecompileUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import javax.servlet.ServletException

val log = LoggerFactory.getLogger(ISML2JSP::class.java.name)!!

/**
 * This class is an offline ISML to JSP compiler. A running server is NOT required.
 */
class ISML2JSP(private val srcDir: File,
               private val destdir: File,
               val contentEncoding: String,
               val encodingMap : Map<String, String>) {

    /*
     * Creates the compiler configuration.
     * @return Compiler condifuration
     */
    private val compilerConfiguration: ISMLCompilerConfiguration
        get() {
            return object : ISMLCompilerConfiguration {
                override fun getJspEncoding(mimeType: String): String? {
                    val encoding = encodingMap[mimeType]
                    return encoding ?: defaultContentEncoding
                }

                override fun getDefaultContentEncoding(): String? {
                    return contentEncoding
                }
            }
        }

    /*
     * This internal helper collects all template file names out of the given directory
     * recursively. The names are built to match the requirements of building template IDs, i.e.
     * containing a sub directory (optionally) and a template file name (w/ extension) separated
     * by slashes.
     *
     * @param dir The directory. Initially (1st recursion) it is the language directory.
     * @param subDirPath The sub directory path
     * @param result The resulting template names.
     */
    private fun getAllTemplateFileNames(dir: File, subDirPath: String?, result: MutableCollection<String>) {
        // get all isml files and subdirs
        val files = dir.listFiles(ISMLTemplateConstants.ismlFilter)

        // iterate results, recurse dirs, add files to result
        files.forEach {f ->
            if(f.isDirectory) {
                val sd = if (subDirPath == null) { "${f.name}/" } else { "$subDirPath${f.name}/" }
                getAllTemplateFileNames(f, sd, result)
            } else {
                if (subDirPath == null) {
                    result.add(f.name)
                } else {
                    result.add(subDirPath.plus(f.name))
                }
            }
        }
    }

    /**
     * Manages the compilation of *.isml to *.jsp files.
     */
    @Throws(IOException::class, ServletException::class)
    fun execute() {
        val compilePathList = ArrayList<Array<File>>()

        // Create compile configuration
        val configuration = compilerConfiguration
        val precompUtils = TemplatePrecompileUtils(configuration)

        // get all language subdirectories
        val langDirs = srcDir.listFiles(ISMLTemplateConstants.directoryFilter)
        if (langDirs != null) {
            for (i in langDirs.indices) {
                // get all template names
                val ismlFiles = ArrayList<String>()
                getAllTemplateFileNames(langDirs[i], null, ismlFiles)

                // iterate all templates and check if we should compile
                for (ismlFileName in ismlFiles) {
                    // scan for files to compile
                    val ismlSubPathName = langDirs[i].name.plus(File.separatorChar).plus(ismlFileName)
                    val jspSubPathName = ismlSubPathName.
                            substring(0, ismlSubPathName.length - ISMLTemplateConstants.TEMPLATE_EXTENSION.length).
                            plus(ISMLTemplateConstants.TEMPLATE_PAGECOMPILE_EXTENSION)

                    val sourceFile = File(srcDir, ismlSubPathName)
                    val jspFile = File(destdir, jspSubPathName)

                    if (!(sourceFile.isFile && sourceFile.canRead())) {
                        continue
                    }

                    // check, if compilation is required
                    if (!jspFile.exists() || jspFile.lastModified() < sourceFile.lastModified()) {
                        compilePathList.add(arrayOf(sourceFile, jspFile))
                    } else {
                        log.debug("Skipping file: {}. Target is up to date.", sourceFile.absolutePath)
                    }
                }
            }
        }

        // check whether there is something to compile at all
        if (compilePathList.size == 0) {
            return
        }

        log.info("Compiling {} source files to {}", compilePathList.size, destdir.absolutePath)

        for (entry in compilePathList) {
            val sourceFile = entry[0]
            val jspFile = entry[1]

            // compile isml -> jsp
            log.debug("Compiling isml file: {}", sourceFile.absolutePath)

            // remove .jsp file
            if (jspFile.exists() && jspFile.isFile) {
                jspFile.delete()
            }

            val outDir = jspFile.parentFile

            if (!outDir.exists()) {
                outDir.mkdirs()
            }

            precompUtils.compileISML(sourceFile, jspFile)
        }
    }
}
