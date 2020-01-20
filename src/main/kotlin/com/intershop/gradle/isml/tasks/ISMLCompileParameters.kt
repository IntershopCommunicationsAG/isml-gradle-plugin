package com.intershop.gradle.isml.tasks

import org.apache.log4j.Level
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters
import java.io.File

/**
 * Interface for worker parameters of ISML compile.
 */
interface ISMLCompileParameters : WorkParameters {

    /**
     * Source directory for ISML compilation.
     * @property sourceDir
     */
    val sourceDir: Property<File>

    /**
     * Target directory for ISML compilation.
     * @property outputDir
     */
    val outputDir: Property<File>

    /**
     * File encoding used by ISML compilation.
     * @property encoding
     */
    val encoding: Property<String>

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
     * Configuration file for eclipse compiler.
     *
     * @property eclipseConfFile
     */
    val eclipseConfFile: Property<File>

    /**
     * Standard output for eclipse compiler.
     *
     * @property compilerOut
     */
    val compilerOut: Property<File>

    /**
     * Error output for eclipse compiler.
     *
     * @property compilerError
     */
    val compilerError: Property<File>

    /**
     * Classpath for ISML and JSP compilation.
     *
     * @property classpath
     */
    val classpath: Property<String>

    /**
     * Temporary webinf folder for JSP compilation.
     *
     * @property tempWebInfFolder
     */
    val tempWebInfFolder: Property<File>

    /**
     * Loglevel for ISML and JSP compilation.
     *
     * @property logLevel
     */
    val logLevel: Property<Level>
}
