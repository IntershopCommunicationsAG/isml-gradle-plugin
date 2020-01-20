package com.intershop.gradle.isml.tasks

import org.apache.jasper.compiler.JarScannerFactory
import org.apache.jasper.compiler.Localizer
import org.apache.log4j.Category
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.apache.tomcat.Jar
import org.apache.tomcat.JarScanType
import org.apache.tomcat.JarScannerCallback
import org.apache.tomcat.util.descriptor.tld.TldParser
import org.apache.tomcat.util.descriptor.tld.TldResourcePath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import javax.servlet.ServletContext

/**
 * Project specific TldScanner for Jasper Compiler.
 */
class TldScanner(var context: ServletContext?, namespaceAware: Boolean, validation: Boolean, blockExternal: Boolean)
    : org.apache.jasper.servlet.TldScanner(context, namespaceAware, validation, blockExternal) {

    companion object {
        /**
         * Logger instance for logging.
         */
        val log: Logger = LoggerFactory.getLogger(this::class.java.name)

        /**
         * TLD file extension.
         */
        private const val TLD_EXT = ".tld"

        /**
         * Default WEB_INF folder.
         */
        private const val WEB_INF = "/WEB-INF/"
    }

    private var logLevel: Level = Level.ERROR
    private var tldParser: TldParser = TldParser(namespaceAware, validation, blockExternal)

    /**
     * TldScan can be configured with this
     * property. This extends the names for TLD files.
     *
     * @property includeNames
     */
    var includeNames = mutableListOf<String>()

    /**
     * TldScan can be configured with this
     * property. This reduces the names for TLD files.
     *
     * @property excludeNames
     */
    var excludeNames = mutableListOf<String>()

    /**
     * Set log level for TLD scanner.
     * @param level
     */
    fun setLogging(level: Level) {
        logLevel = level

        LogManager.getCurrentLoggers().iterator().forEach {
            if(it is Category) {
                it.level = level
            }
        }
    }

    override fun scanJars() {
        val scanner = JarScannerFactory.getJarScanner(context)
        val callback = TldScannerCallback()
        log.debug("Init scan filter with {} and {}.", includeNames, excludeNames)

        scanner.jarScanFilter = ListJarScanFilter(includeNames, excludeNames, logLevel)
        scanner.scan(JarScanType.TLD, context, callback)
        if (callback.scanFoundNoTLDs()) {
            log.info(Localizer.getMessage("jsp.tldCache.noTldSummary"))
        }
    }

    @Throws(IOException::class, SAXException::class)
    override fun parseTld(resourcePath: String?) {
        val tldResourcePath = TldResourcePath(context!!.getResource(resourcePath), resourcePath)
        parseTld(tldResourcePath)
    }

    @Throws(IOException::class, SAXException::class)
    override fun parseTld(path: TldResourcePath?) {
        if (tldResourcePathTaglibXmlMap.containsKey(path)) {
            // TLD has already been parsed as a result of processing web.xml
            return
        }
        val tld = tldParser.parse(path)
        val uri = tld.uri
        if (uri != null) {
            if (!uriTldResourcePathMap.containsKey(uri)) {
                uriTldResourcePathMap[uri] = path
            }
        }
        tldResourcePathTaglibXmlMap[path] = tld
        if (tld.listeners != null) {
            listeners.addAll(tld.listeners)
        }
    }

    /**
     * Inner class for project specific TLDScanner.
     */
    inner class TldScannerCallback : JarScannerCallback {
        private var foundJarWithoutTld = false
        private var foundFileWithoutTld = false

        /**
         * Scan for Tlds in ...
         * @param jar
         * @param webappPath
         * @param isWebapp
         */
        @Throws(IOException::class)
        override fun scan(jar: Jar, webappPath: String?, isWebapp: Boolean) {
            var found = false
            val jarFileUrl = jar.jarFileURL
            jar.nextEntry()
            var entryName = jar.entryName
            while (entryName != null) {
                if (!(entryName.startsWith("META-INF/") &&
                                entryName.endsWith(TLD_EXT))) {
                    jar.nextEntry()
                    entryName = jar.entryName
                    continue
                }
                found = true
                val tldResourcePath = TldResourcePath(jarFileUrl, webappPath, entryName)
                try {
                    parseTld(tldResourcePath)
                } catch (e: SAXException) {
                    throw IOException(e)
                }
                jar.nextEntry()
                entryName = jar.entryName
            }
            if (found) {
                if (log.isDebugEnabled) {
                    log.debug(Localizer.getMessage("jsp.tldCache.tldInJar", jarFileUrl.toString()))
                }
            } else {
                foundJarWithoutTld = true
                if (log.isDebugEnabled) {
                    log.debug(Localizer.getMessage(
                            "jsp.tldCache.noTldInJar", jarFileUrl.toString()))
                }
            }
        }

        /**
         * Scan for Tlds in ...
         * @param file
         * @param webappPath
         * @param isWebapp
         */
        @Throws(IOException::class)
        override fun scan(file: File, webappPath: String?, isWebapp: Boolean) {
            val metaInf = File(file, "META-INF")
            if (!metaInf.isDirectory) {
                return
            }
            foundFileWithoutTld = false
            val filePath = file.toPath()
            Files.walkFileTree(metaInf.toPath(), object : SimpleFileVisitor<Path>() {
                @Throws(IOException::class)
                override fun visitFile(file: Path,
                                       attrs: BasicFileAttributes): FileVisitResult {
                    val fileName = file.fileName
                    if (fileName == null || !fileName.toString().toLowerCase(
                                    Locale.ENGLISH).endsWith(TLD_EXT)) {
                        return FileVisitResult.CONTINUE
                    }
                    foundFileWithoutTld = true
                    val resourcePath: String?
                    if (webappPath == null) {
                        resourcePath = null
                    } else {
                        var subPath = file.subpath(
                                filePath.nameCount, file.nameCount).toString()
                        if ('/' != File.separatorChar) {
                            subPath = subPath.replace(File.separatorChar, '/')
                        }
                        resourcePath = "$webappPath/$subPath"
                    }
                    try {
                        val url = file.toUri().toURL()
                        val path = TldResourcePath(url, resourcePath)
                        parseTld(path)
                    } catch (e: SAXException) {
                        throw IOException(e)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
            if (foundFileWithoutTld) {
                if (log.isDebugEnabled) {
                    log.debug(Localizer.getMessage("jsp.tldCache.tldInDir",
                            file.absolutePath))
                }
            } else {
                if (log.isDebugEnabled) {
                    log.debug(Localizer.getMessage("jsp.tldCache.noTldInDir",
                            file.absolutePath))
                }
            }
        }

        /**
         * Scan for Tlds in WebInfClasses.
         */
        @Throws(IOException::class)
        override fun scanWebInfClasses() { // This is used when scanAllDirectories is enabled and one or more
        // JARs have been unpacked into WEB-INF/classes as happens with some
        // IDEs.
            val paths: Set<String> = context?.getResourcePaths(WEB_INF + "classes/META-INF") ?: return
            for (path in paths) {
                if (path.endsWith(TLD_EXT)) {
                    try {
                        parseTld(path)
                    } catch (e: SAXException) {
                        throw IOException(e)
                    }
                }
            }
        }

        /**
         * Return true or false if TLDs not found.
         */
        fun scanFoundNoTLDs(): Boolean {
            return foundJarWithoutTld
        }
    }
}
