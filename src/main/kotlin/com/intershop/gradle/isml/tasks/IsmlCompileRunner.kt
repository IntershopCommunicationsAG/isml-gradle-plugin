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

import org.apache.log4j.Category
import org.apache.log4j.LogManager
import org.eclipse.jdt.internal.compiler.batch.Main
import org.gradle.workers.WorkAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File


/**
 * Work class for tasks to generate class files from ISML files.
 */
abstract class IsmlCompileRunner : WorkAction<ISMLCompileParameters> {

    companion object {
        /**
         * Logger instance for logging.
         */
        val log: Logger = LoggerFactory.getLogger(this::class.java.name)
        /**
         * These is necessary for jsp path change made by Intershop.
         */
        val JAVA_KEYWORDS = arrayOf("abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
                "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
                "volatile", "while")
    }

    override fun execute() {

        LogManager.getCurrentLoggers().iterator().forEach {
            if(it is Category) {
                it.level = parameters.logLevel.get()
            }
        }

        log.info("Start ISML compilation in source: {}", parameters.sourceDir.get().absolutePath)
        // run ISML compiler
        val ismlCompiler = ISML2JSP(parameters.sourceDir.get(), parameters.outputDir.get(),
                parameters.encoding.get(), mutableMapOf("text/html" to parameters.encoding.get()), log)
        ismlCompiler.execute()

        val fileList = mutableListOf<String>()

        if(parameters.enableTldScan.get() == true) {
            if (parameters.tldScanIncludes.get().size >= 0 && parameters.tldScanExcludes.get().size == 0) {
                if (parameters.classpath.get().isNotEmpty()) {
                    val cpList = parameters.classpath.get().split(":")
                    cpList.forEach {
                        if (!it.endsWith(File.pathSeparator)) {
                            fileList.add(File(it).name)
                            log.debug("Add file name {} to list", File(it).name)
                        }
                    }

                    fileList.addAll(parameters.tldScanIncludes.get())
                }
            }
        }

        // run JSP compiler
        val jspc = JspC()
        jspc.setLogging(parameters.logLevel.get())

        jspc.enableTldScan = parameters.enableTldScan.get()
        jspc.tldScanIncludes = fileList
        jspc.tldScanExcludes = parameters.tldScanExcludes.get()

        jspc.classPath = parameters.classpath.get()
        jspc.setUriroot(parameters.outputDir.get().absolutePath)
        jspc.setPackage(makeJavaPackageFromPackage(parameters.jspPackage.get()))
        jspc.setOutputDir(parameters.outputDir.get().absolutePath)
        jspc.javaEncoding = parameters.encoding.get()
        jspc.compilerSourceVM = parameters.sourceCompatibility.get()
        jspc.compilerTargetVM = parameters.targetCompatibility.get()

        jspc.execute()

        val eclipseConfFile = parameters.eclipseConfFile.get()
        // run eclipse compiler
        if(eclipseConfFile.exists()) {
            eclipseConfFile.delete()
        }

        eclipseConfFile.writeText("-g -nowarn -encoding ${parameters.encoding.get()} " +
                "-target ${parameters.targetCompatibility.get()} " +
                "-source ${parameters.sourceCompatibility.get()} " +
                "-classpath ${parameters.classpath.get()}")

        val compiler = Main( parameters.compilerOut.get().printWriter(),
                parameters.compilerError.get().printWriter(),
                false, null, null)
        compiler.compile(arrayOf("@${eclipseConfFile.absolutePath}",  parameters.outputDir.get().absolutePath))

        // remove WEB-INF folder
        parameters.tempWebInfFolder.get().deleteRecursively()

        // set same timestamp for all files
        unifyTimestamps(parameters.outputDir.get())
    }

    private fun makeJavaPackageFromPackage(packageName: String) : String {
        val classNameComponents = packageName.split('.')
        val legalClassNames = StringBuffer()
        for(cnc in classNameComponents) {
            legalClassNames.append(makeJavaIdentifier(cnc, true))
            if(classNameComponents.indexOf(cnc) < (classNameComponents.size - 1)) {
                legalClassNames.append('.')
            }
        }
        return legalClassNames.toString()
    }

    private fun makeJavaIdentifier(identifier: String, periodToUnderscore: Boolean): String {
        val modifiedIdentifier = StringBuilder(identifier.length)

        if (!Character.isJavaIdentifierStart(identifier[0])) {
            modifiedIdentifier.append('_')
        }

        (identifier.indices)
                .asSequence()
                .map { identifier[it] }
                .forEach {
                    if (Character.isJavaIdentifierPart(it) && (it != '_' || !periodToUnderscore)) {
                        modifiedIdentifier.append(it)
                    } else if (it == '.' && periodToUnderscore) {
                        modifiedIdentifier.append('_')
                    } else {
                        modifiedIdentifier.append(mangleChar(it))
                    }
                }
        if (JAVA_KEYWORDS.contains(modifiedIdentifier.toString())) {
            modifiedIdentifier.append('_')
        }
        return modifiedIdentifier.toString()
    }

    private fun mangleChar(ch: Char): String {
        val result = CharArray(5)
        result[0] = '_'
        result[1] = Character.forDigit(ch.toInt() shr 12 and 0xf, 16)
        result[2] = Character.forDigit(ch.toInt() shr 8 and 0xf, 16)
        result[3] = Character.forDigit(ch.toInt() shr 4 and 0xf, 16)
        result[4] = Character.forDigit(ch.toInt() and 0xf, 16)
        return String(result)
    }

    private fun unifyTimestamps(pageCompileDir: File) {
        log.info("Unifying compiled template timestamps in {}.", pageCompileDir)
        unifyTimestamps(pageCompileDir, System.currentTimeMillis())
    }

    private fun unifyTimestamps(parent: File, timestamp: Long) {
        if (parent.exists()) {
            if (parent.isDirectory) {
                val files = parent.listFiles()
                for (file in files) {
                    unifyTimestamps(file, timestamp)
                }
            }
            parent.setLastModified(timestamp)
        }
    }
}
