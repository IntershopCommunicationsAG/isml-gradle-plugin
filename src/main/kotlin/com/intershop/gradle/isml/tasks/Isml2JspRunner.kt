package com.intershop.gradle.isml.tasks

import com.intershop.beehive.parser.ISML2JSP
import org.gradle.workers.WorkAction

abstract class Isml2JspRunner : WorkAction<Isml2JspRunnerParameters> {

    override fun execute() {
        val parser = ISML2JSP()
        parser.srcdir = parameters.inputDir.get()
        parser.destdir = parameters.outputDir.get()

        parser.contentEncoding = "UTF-8"

        parser.execute()
    }
}