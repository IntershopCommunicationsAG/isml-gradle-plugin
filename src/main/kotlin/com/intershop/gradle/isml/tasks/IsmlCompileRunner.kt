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

import org.apache.jasper.JspC
import org.eclipse.jdt.internal.compiler.batch.Main
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import javax.inject.Inject

/**
 * Work class for tasks to generate class files from ISML files.
 */
open class IsmlCompileRunner @Inject constructor(private val sourceDir: File,
                                                 private val outputDir: File,
                                                 private val encoding: String,
                                                 private val jspPackage: String,
                                                 private val sourceCompatibility: String,
                                                 private val targetCompatibility: String,
                                                 private val eclipseConfFile: File,
                                                 private val compilerOut: File,
                                                 private val compilerError: File,
                                                 private val classpath: String,
                                                 private val tempWebInfFolder: File): Runnable {

    companion object {
        val log: Logger = LoggerFactory.getLogger(IsmlCompileRunner::class.java.name)

        // necessary for jsp path change made by Intershop
        val JAVA_KEYWORDS = arrayOf("abstract", "assert", "boolean", "break", "byte", "case", "catch",
                "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
                "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
                "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
                "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
                "volatile", "while")
    }

    override fun run() {
        log.info("Start ISML compilation in source: {}", sourceDir.absolutePath)
        // run ISML compiler
        val ismlCompiler = ISML2JSP(sourceDir, outputDir, encoding, mutableMapOf("text/html" to encoding) )
        ismlCompiler.execute()

        // run JSP compiler
        val jspc = JspC()
        jspc.setArgs(arrayOf("-p", makeJavaPackageFromPackage(jspPackage),
                "-uriroot", outputDir.absolutePath,
                "-d", outputDir.absolutePath,
                "-javaEncoding", encoding,
                "-source", sourceCompatibility,
                "-target", targetCompatibility))
        jspc.execute()

        // run eclipse compileer
        if(eclipseConfFile.exists()) {
            eclipseConfFile.delete()
        }

        eclipseConfFile.writeText("-g -nowarn -encoding $encoding -target $targetCompatibility -source $sourceCompatibility -classpath $classpath")

        val compiler = Main( compilerOut.printWriter(), compilerError.printWriter(), false, null, null)
        compiler.compile(arrayOf("@${eclipseConfFile.absolutePath}", outputDir.absolutePath))

        // remove WEB-INF folder
        tempWebInfFolder.deleteRecursively()

        // set same timestamp for all files
        unifyTimestamps(outputDir)
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

        (0 until identifier.length)
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
