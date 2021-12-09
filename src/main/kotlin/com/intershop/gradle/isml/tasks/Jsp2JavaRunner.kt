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

import org.gradle.workers.WorkAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Runner for Jsp2Java.
 */
abstract class Jsp2JavaRunner : WorkAction<Jsp2JavaRunnerParameters> {

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
        val fileList = mutableListOf<String>()

        if (parameters.enableTldScan.get() == true &&
            parameters.tldScanIncludes.get().size >= 0 &&
            parameters.tldScanExcludes.get().size == 0 &&
            parameters.classpath.get().isNotEmpty()) {
            val cpList = parameters.classpath.get().split(":")
            cpList.forEach {
                if (!it.endsWith(File.pathSeparator)) {
                    fileList.add(File(it).name)
                    log.debug("Add file name {} to list", File(it).name)
                }
            }

            fileList.addAll(parameters.tldScanIncludes.get())
        }

        // run JSP compiler
        val jspc = JspC()
        jspc.setLogging(parameters.logLevel.get())

        jspc.enableTldScan = parameters.enableTldScan.get()
        jspc.tldScanIncludes = fileList
        jspc.tldScanExcludes = parameters.tldScanExcludes.get()

        jspc.classPath = parameters.classpath.get()
        jspc.setUriroot(parameters.inputDir.get().asFile.absolutePath)
        jspc.setPackage(makeJavaPackageFromPackage(parameters.jspPackage.get()))
        jspc.setOutputDir(parameters.outputDir.get().asFile.absolutePath)
        jspc.javaEncoding = parameters.encoding.get()
        jspc.compilerSourceVM = parameters.sourceCompatibility.get()
        jspc.compilerTargetVM = parameters.targetCompatibility.get()

        jspc.execute()
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
        result[1] = Character.forDigit(ch.code shr 12 and 0xf, 16)
        result[2] = Character.forDigit(ch.code shr 8 and 0xf, 16)
        result[3] = Character.forDigit(ch.code shr 4 and 0xf, 16)
        result[4] = Character.forDigit(ch.code and 0xf, 16)
        return String(result)
    }
}
