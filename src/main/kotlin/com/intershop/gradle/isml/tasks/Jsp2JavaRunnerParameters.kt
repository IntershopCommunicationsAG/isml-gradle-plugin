package com.intershop.gradle.isml.tasks

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters
import java.io.File
import org.apache.log4j.Level

/**
 * Parameter interface for Isml2Jsp Runner.
 */
interface Jsp2JavaRunnerParameters  : WorkParameters {

    /**
     * File encoding used by ISML compilation.
     * @property encoding
     */
    val encoding: Property<String>

    /**
     * Configuration for tld scan includes.
     * @property tldScanIncludes
     */
    val tldScanIncludes: ListProperty<String>

    /**
     * Configuration for tld scan excludes.
     * @property tldScanExcludes
     */
    val tldScanExcludes: ListProperty<String>

    /**
     * With this property tld scan of jsp
     * compiler can be disabled or enabled.
     *
     * @property enableTldScan
     */
    val enableTldScan: Property<Boolean>

    /**
     * This jsp package is used by the isml compiler.
     * @property jspPackage
     */
    val jspPackage: Property<String>

    /**
     * This source compatibility is used by the isml compiler.
     * @property sourceCompatibility
     */
    val sourceCompatibility: Property<String>

    /**
     * This target compatibility is used by the isml compiler.
     * @property targetCompatibility
     */
    val targetCompatibility: Property<String>

    /**
     * Source directory for JSP compilation.
     * @property inputDir
     */
    val inputDir: Property<File>

    /**
     * Target directory for ISML compilation.
     * @property outputDir
     */
    val outputDir: Property<File>

    /**
     * Loglevel for JSP and Java compilation.
     *
     * @property logLevel
     */
    val logLevel: Property<Level>

    /**
     * Classpath for JSP and Java compilation.
     *
     * @property classpath
     */
    val classpath: Property<String>
}
