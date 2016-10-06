/*
 * Copyright 2015 Intershop Communications AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.intershop.gradle.isml.tasks

import com.intershop.gradle.isml.IsmlExtension
import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.tasks.*
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.process.internal.JavaExecHandleBuilder

import javax.inject.Inject

class IsmlCompile extends DefaultTask {

    // patterns
    final static String FILTER_JSP = '**/**/*.jsp'
    
    // template encoding
    private static final String  DEFAULT_CONTENT_ENCODING = 'UTF-8'
    
    // necessary for jsp path change made by Intershop
    private static final String[] javaKeywords = ['abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch',
                                                  'char', 'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum', 'extends', 'final', 'finally',
                                                  'float', 'for', 'goto', 'if', 'implements', 'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new',
                                                  'package', 'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp', 'super', 'switch',
                                                  'synchronized', 'this', 'throw', 'throws', 'transient', 'try', 'void', 'volatile', 'while' ]
    
    static final String PAGECOMPILE_FOLDER = 'pagecompile'
    
    @Input
    String jspPackage = "ish.cartridges.${project.getName()}"
    
    @Input
    sourceCompatibility = '1.6'
    
    @Input
    targetCompatibility = '1.6'

    ConfigurableFileTree sourceDir
    
    @SkipWhenEmpty
    @InputFiles
    ConfigurableFileTree getSourceDir() {
        return sourceDir
    }
    
    @OutputFiles
    ConfigurableFileTree outputDirectory
    
    @Input
    Set<File> classpath

    @TaskAction
    void generate() {
        File outputDir = getOutputDirectory().dir

        outputDir.deleteDir()
        outputDir.mkdir()

        File tempPagecompileDir = new File(getTemporaryDir(), PAGECOMPILE_FOLDER)

        File pageCompileFolder = new File(outputDir, PAGECOMPILE_FOLDER)
        File srcInternalDir =  getSourceDir().getDir()

        File webInf = createWebInf(tempPagecompileDir)
        prepareTagLibs(webInf)

        generateJSP(srcInternalDir, tempPagecompileDir)
        generateJava(tempPagecompileDir)
        compile(tempPagecompileDir)

        project.copy {
            from(tempPagecompileDir) {
                exclude "${webInf.getName()}/**/**"
            }
            into pageCompileFolder
        }

        unifyTimestamps(pageCompileFolder)
    }

    private File createWebInf(File pageCompileDir) {
        File webXml = new File(pageCompileDir, "WEB-INF/web.xml")
        if(! webXml.exists()) {
            webXml.getParentFile().mkdirs()
        }

        webXml << """<?xml version="1.0" encoding="ISO-8859-1"?>
        <web-app xmlns="http://java.sun.com/xml/ns/javaee"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
                              http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
          version="3.0">

        </web-app>
        """.stripIndent()

        return webXml.getParentFile()
    }

    private void prepareTagLibs(File webInfFolder) {
        //Identify first own tags
        File projectTagsFolder = project.file('staticfiles/cartridge/tags')
        File porjectWebInfTagFolder = new File(webInfFolder, "tags/${project.name}")
        if(projectTagsFolder.exists() && projectTagsFolder.listFiles().size() > 0) {
            project.copy {
                from(projectTagsFolder)
                into(porjectWebInfTagFolder)
            }
        }

        //Identify tag of multiproject dependencies
        project.configurations.collectMany { it.allDependencies }.findAll { it instanceof ProjectDependency } .unique().each { ProjectDependency pd ->
            projectTagsFolder = new File(pd.dependencyProject.projectDir, 'staticfiles/cartridge/tags')
            porjectWebInfTagFolder = new File(webInfFolder, "tags/${pd.dependencyProject.name}")
            if(projectTagsFolder.exists() && projectTagsFolder.listFiles().size() > 0) {
                project.copy {
                    from(projectTagsFolder)
                    into(porjectWebInfTagFolder)
                }
            }
        }

        //Identify tag of dependencies
        project.configurations.getByName('compile').getResolvedConfiguration().getResolvedArtifacts().each { ResolvedArtifact artifact ->
            if (artifact.type == 'cartridge') {
                porjectWebInfTagFolder = new File(webInfFolder, "tags/${artifact.name}")
                File tempTagsDir = new File(getTemporaryDir(), "tmpTagLib/${artifact.name}")
                project.copy {
                    from project.zipTree(artifact.getFile())
                    include '*/release/tags/**/**'
                    into tempTagsDir
                }
                projectTagsFolder = new File(tempTagsDir,"${artifact.name}/release/tags")
                println projectTagsFolder
                if(projectTagsFolder.exists() && projectTagsFolder.listFiles().size() > 0) {
                    project.copy {
                        from(projectTagsFolder)
                        into(porjectWebInfTagFolder)
                    }
                }
            }
        }
    }

    private void generateJSP(File ismlSrcDir, File pageCompileDir) {
        project.logger.info('Compile isml templates to jsp')
        
        //intialize ant task
        ant.taskdef (name : 'ISML2JSP',
            classname : 'com.intershop.beehive.isml.capi.ISML2JSP',
            classpath : project.files(getClasspath()).asPath)
        
        //run anttask
        ant.ISML2JSP(
                srcdir:  ismlSrcDir,
                destdir: pageCompileDir,
                contentEncoding : DEFAULT_CONTENT_ENCODING) {
            JspEncoding(
                mimeType : 'text/html',
                encoding : DEFAULT_CONTENT_ENCODING
            )
        }

        // copy jsp files from the original folder to the jsp folder
        ant.copy(todir: pageCompileDir) {
            fileset(dir: ismlSrcDir,
                    includes: FILTER_JSP)
        }
    }
    
    private void generateJava(File pageCompileDir) {
        project.logger.info('Compile jsp templates to java')

        FileCollection jspCompilerConfiguration = getProject().getConfigurations().getAt(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME)

        //intialize ant task
        ant.taskdef (
                name : 'JASPER',
                classname : 'org.apache.jasper.JspC',
                classpath : jspCompilerConfiguration.asPath
        )
        
        if(! pageCompileDir.exists()) {
            throw new GradleException("The directory ${pageCompileDir.absolutePath} does not exists! Check location of your isml templates!")
        }

        //run anttask
        ant.JASPER(
                uriroot:  pageCompileDir,
                outputDir: pageCompileDir,
                package: makeJavaPackageFromPackage(getJspPackage()),
                classpath: project.files(getClasspath()).asPath,
                //verbose: '0',
                javaEncoding: DEFAULT_CONTENT_ENCODING,
                compilerTargetVM: targetCompatibility,
                compilerSourceVM: sourceCompatibility)
    }

    @CompileStatic
    private void compile(File pageCompileDir) {
        project.logger.info('Compile java templates to class')

        JavaExecHandleBuilder exechandler = prepareCompilerExec(pageCompileDir)
        if (exechandler) {
            exechandler.build().start().waitForFinish().assertNormalExitValue()
        }
    }

    @CompileStatic
    private JavaExecHandleBuilder prepareCompilerExec(File pageCompileDir) {
        FileCollection eclipseCompilerConfiguration = getProject().getConfigurations().getAt(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME)

        File confFile = new File(getTemporaryDir(), "eclipsecompiler.config")
        confFile << "-g -nowarn -encoding ${DEFAULT_CONTENT_ENCODING} -target ${getTargetCompatibility()} -source ${getSourceCompatibility()} -classpath ${project.files(getClasspath() + pageCompileDir).asPath}"

        JavaExecHandleBuilder javaExec = new JavaExecHandleBuilder(getFileResolver())

        return javaExec
                .setClasspath(eclipseCompilerConfiguration)
                .setMain('org.eclipse.jdt.internal.compiler.batch.Main')
                .setArgs(["@${confFile.absolutePath}", pageCompileDir.absolutePath] )
    }

    // Unifies the timestamps of the compiled *.jsp, *.java and *.class files.
    // This is necessary so the Jasper compiler will not re-compile them on
    // first access.
    @CompileStatic
    private void unifyTimestamps(File pageCompileDir) {
        project.logger.info('Unifying compiled template timestamps.')
        unifyTimestamps(pageCompileDir, System.currentTimeMillis())
    }

    @CompileStatic
    private static void unifyTimestamps(File parent, long timestamp) {
        if (parent.exists()) {
            if (parent.isDirectory()) {
                File[] files = parent.listFiles()
                for (File file : files)
                {
                    unifyTimestamps(file, timestamp)
                }
            }
            parent.lastModified = timestamp
        }
    }
    
    
    // code from org.apache.jasper.compiler.JspUtil to
    // solve issue with non intershop patched Jasper
    @CompileStatic
    private static String makeJavaPackageFromPackage(String packageName) {
        String[] classNameComponents = packageName.split('\\.')
        StringBuffer legalClassNames = new StringBuffer();
        for (int i = 0; i < classNameComponents.length; i++) {
            legalClassNames.append(makeJavaIdentifier(classNameComponents[i], true));
            if (i < classNameComponents.length - 1) {
                legalClassNames.append('.');
            }
        }
        return legalClassNames.toString();
    }
    
    @CompileStatic
    private static String makeJavaIdentifier(String identifier, boolean periodToUnderscore) {
        StringBuilder modifiedIdentifier = new StringBuilder(identifier.length());
        
        if (!Character.isJavaIdentifierStart(identifier.charAt(0))) {
            modifiedIdentifier.append('_');
        }
        
        for (int i = 0; i < identifier.length(); i++) {
            char ch = identifier.charAt(i);
            if (Character.isJavaIdentifierPart(ch) &&
                    (ch != '_' || !periodToUnderscore)) {
                modifiedIdentifier.append(ch);
            } else if (ch == '.' && periodToUnderscore) {
                modifiedIdentifier.append('_');
            } else {
                modifiedIdentifier.append(mangleChar(ch));
            }
        }
        if (isJavaKeyword(modifiedIdentifier.toString())) {
            modifiedIdentifier.append('_');
        }
        return modifiedIdentifier.toString();
    }

    private static String mangleChar(char ch) {
        char[] result = new char[5];
        result[0] = '_';
        result[1] = Character.forDigit((ch >> 12) & 0xf, 16);
        result[2] = Character.forDigit((ch >> 8) & 0xf, 16);
        result[3] = Character.forDigit((ch >> 4) & 0xf, 16);
        result[4] = Character.forDigit(ch & 0xf, 16);
        return new String(result);
    }

    private static boolean isJavaKeyword(String key) {
        int i = 0;
        int j = javaKeywords.length;
        while (i < j) {
            int k = (i + j) / 2;
            int result = javaKeywords[k].compareTo(key);
            if (result == 0) {
                return true;
            }
            if (result < 0) {
                i = k + 1;
            } else {
                j = k;
            }
        }
        return false;
    }

    /**
     * Set Java fork options.
     *
     * @return JavaForkOptions
     */

    public JavaForkOptions getForkOptions() {
        if (forkOptions == null) {
            forkOptions = new DefaultJavaForkOptions(getFileResolver());
        }

        return forkOptions;
    }

    @CompileStatic
    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }
}
