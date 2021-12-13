package com.intershop.gradle.isml.tasks

import org.apache.jasper.Constants
import org.apache.jasper.JasperException
import org.apache.jasper.compiler.JspConfig
import org.apache.jasper.compiler.JspRuntimeContext
import org.apache.jasper.compiler.TagPluginManager
import org.apache.jasper.compiler.TldCache
import org.apache.jasper.servlet.JspCServletContext
import org.apache.log4j.Category
import org.apache.log4j.Level
import org.apache.log4j.LogManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.net.URL
import java.net.URLClassLoader
import java.util.*

/**
 * Project specific jasper compiler.
 * Overwrites original jasper compiler.
 */
class JspC: org.apache.jasper.JspC() {

    private var ishScanner: TldScanner? = null
    private var logLevel: Level = Level.ERROR

    companion object {
        /**
         * Logger instance for logging.
         * @property logger
         */
        val logger: Logger = LoggerFactory.getLogger(this::class.java.name)
    }

    /**
     * TldScan can be enabled with this property.
     * @property enableTldScan
     */
    var enableTldScan = false

    /**
     * TldScan can be configured with this
     * property. This extends the search path for Tlds.
     *
     * @property tldScanIncludes
     */
    var tldScanIncludes = mutableListOf<String>()

    /**
     * TldScan can be configured with this
     * property. This reduces the search path for Tlds.
     *
     * @property tldScanIncludes
     */
    var tldScanExcludes = mutableListOf<String>()

    /**
     * This configures the log level of the JspC.
     * @param level log level for logging.
     */
    fun setLogging(level: Level) {
        logLevel = level
        LogManager.getCurrentLoggers().iterator().forEach {
            if(it is Category) {
                it.level = level
            }
        }
    }

    override fun initTldScanner(context: JspCServletContext?, classLoader: ClassLoader?) {
        if (ishScanner != null) {
            return
        }
        ishScanner = newTldScanner(context, true, isValidateTld, isBlockExternal)
        ishScanner!!.setLogging(logLevel)
        ishScanner?.setClassLoader(classLoader)
    }

    override fun newTldScanner(context: JspCServletContext?, namespaceAware: Boolean,
                               validate: Boolean, blockExternal: Boolean): TldScanner? {
        return TldScanner(context, namespaceAware, validate, blockExternal)
    }

    @Throws(IOException::class, JasperException::class)
    override fun initServletContext(classLoader: ClassLoader?) {

        val currentClassLoader = Thread.currentThread().contextClassLoader
        val urls = setUpClassPath(getClassPath())
        val ucl = URLClassLoader(urls.toTypedArray<URL?>(), currentClassLoader)

        val log = PrintWriter(System.out)
        val resourceBase = File(uriRoot).canonicalFile.toURI().toURL()
        context = JspCServletContext(log, resourceBase, ucl, isValidateXml, isBlockExternal)


        if (isValidateTld) {
            context.setInitParameter(Constants.XML_VALIDATION_TLD_INIT_PARAM, "true")
        }

        initTldScanner(context, ucl)

        if(enableTldScan) {
            logger.info("TLD scan is enabled.")
            logger.info("TLD scan includes: {}", tldScanIncludes)
            logger.info("TLD scan excludes: {}", tldScanExcludes)
            try {
                ishScanner?.includeNames = tldScanIncludes
                ishScanner?.excludeNames = tldScanExcludes
                ishScanner?.setClassLoader(ucl)
                ishScanner?.scan()
            } catch (e: SAXException) {
                throw JasperException(e)
            }
        }

        tldCache = TldCache(context, ishScanner?.uriTldResourcePathMap,
                    ishScanner?.tldResourcePathTaglibXmlMap)

        context.setAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME, tldCache)
        rctxt = JspRuntimeContext(context, this)
        jspConfig = JspConfig(context)
        tagPluginManager = TagPluginManager(context)
    }

    private fun setUpClassPath(classpath: String) : List<URL> {
        val urls = ArrayList<URL>()
        val paths = classpath.split(":")

        paths.forEach {
            urls.add(File(it).toURI().toURL())
        }

        return urls
    }
}
