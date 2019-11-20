package com.intershop.gradle.isml.tasks

import org.apache.jasper.Constants
import org.apache.jasper.JasperException
import org.apache.jasper.compiler.JspConfig
import org.apache.jasper.compiler.JspRuntimeContext
import org.apache.jasper.compiler.TagPluginManager
import org.apache.jasper.compiler.TldCache
import org.apache.jasper.servlet.JspCServletContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.net.URL
import java.net.URLClassLoader
import java.util.*

class JspC: org.apache.jasper.JspC() {

    companion object {
        val logger: Logger = LoggerFactory.getLogger("IntershopJspC")
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

        try {
            scanner.setClassLoader(ucl)
            scanner.scan()
        } catch (e: SAXException) {
            throw JasperException(e)
        }
        tldCache = TldCache(context, scanner.uriTldResourcePathMap,
                scanner.tldResourcePathTaglibXmlMap)
        context.setAttribute(TldCache.SERVLET_CONTEXT_ATTRIBUTE_NAME, tldCache)
        rctxt = JspRuntimeContext(context, this)
        jspConfig = JspConfig(context)
        tagPluginManager = TagPluginManager(context)
    }

    private fun setUpClassPath(classpath: String) : List<URL> {
        val urls = ArrayList<URL>()
        val paths = classpath.split(":")

        paths.forEach {
            if(IsmlCompileRunner.log.isDebugEnabled) {
                IsmlCompileRunner.log.debug("Adding to classpath {}" , it)
            }
            urls.add(File(it).toURI().toURL())
        }

        return urls
    }
}