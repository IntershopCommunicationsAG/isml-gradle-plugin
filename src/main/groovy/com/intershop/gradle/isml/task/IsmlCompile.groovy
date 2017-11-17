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

package com.intershop.gradle.isml.task

import com.intershop.gradle.isml.extension.IsmlExtension
import groovy.transform.CompileStatic
import org.codehaus.groovy.transform.sc.StaticCompileTransformation
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.process.JavaForkOptions
import org.gradle.process.internal.DefaultJavaForkOptions
import org.gradle.process.internal.JavaExecHandleBuilder

import javax.inject.Inject

class IsmlCompile extends DefaultTask {

    // main class names
    static final String JSP_ANTTASK_CLASSNAME = 'org.apache.jasper.JspC'

    static final String ISML_ANTTASK_CLASSNAME = 'com.intershop.beehive.isml.capi.ISML2JSP'

    static final String ECLIPSE_COMPILER_CLASSNAME = 'org.eclipse.jdt.internal.compiler.batch.Main'

    // folder names
    static final String TAGLIB_SOURCE_FOLDER = 'staticfiles/cartridge/tags'

    static final String PAGECOMPILE_FOLDER = 'pagecompile'

    static final String TAGLIB_FOLDER = 'tags'

    static final String RELEASE_TAGLIB_FOLDER = "release/${TAGLIB_FOLDER}"

    // patterns
    final static String FILTER_TAGLIB = "*/${RELEASE_TAGLIB_FOLDER}/**/**"

    final static String FILTER_WEBINF = 'WEB-INF/**/**'

    // path of web.xml
    private static final String WEB_XML_PATH = 'WEB-INF/web.xml'

    // simple web.xml
    private static final String WEB_XML_CONTENT = '''<?xml version="1.0" encoding="ISO-8859-1"?>
        <web-app xmlns="http://java.sun.com/xml/ns/javaee"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://java.sun.com/xml/ns/javaee
                              http://java.sun.com/xml/ns/javaee/web-app_3_0.xsd"
          version="3.0">

        </web-app>
        '''.stripIndent()
    
    // necessary for jsp path change made by Intershop
    private static final String[] JAVA_KEYWORDS = ['abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch',
                                                   'char', 'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum', 'extends', 'final', 'finally',
                                                   'float', 'for', 'goto', 'if', 'implements', 'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new',
                                                   'package', 'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp', 'super', 'switch',
                                                   'synchronized', 'this', 'throw', 'throws', 'transient', 'try', 'void', 'volatile', 'while' ]

    /**
     * Java fork options for the Java task.
     */
    JavaForkOptions eclipseCompilerJavaOptions

    /**
     * JSP package name
     */
    @Input
    String jspPackage = "ish.cartridges.${project.getName()}"

    /**
     * Source compatibility of java files (result of Jsp2Java)
     */
    @Input
    sourceCompatibility = '1.6'

    /**
     * Target compatibility of java files (result of Jsp2Java)
     */
    @Input
    targetCompatibility = '1.6'

    /**
     * sourceSet name of java files
     */
    @Input
    String sourceSetName

    /**
     * Configuration for isml compilation to class files
     */
    @Input
    String ismlConfigurationName

    /**
     * Input Dependency
     */
    @InputFiles
    @Classpath
    FileCollection getClasspathFiles() {
        FileCollection ismlCompileClasspath = null

        if(project.convention.findPlugin(JavaPluginConvention.class)) {
            JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention.class)
            SourceSet main = javaConvention.sourceSets.getByName(getSourceSetName())

            ismlCompileClasspath = project.files(main.output.classesDir,
                    main.output.resourcesDir,
                    new File(getTemporaryDir(), PAGECOMPILE_FOLDER),
                    project.getConfigurations().getAt(getIsmlConfigurationName()).filter({ File itFile -> itFile.name.endsWith('.jar') }))
        } else {
            ismlCompileClasspath = project.files(
                    new File(getTemporaryDir(), PAGECOMPILE_FOLDER),
                    project.getConfigurations().getAt(getIsmlConfigurationName()).filter({ File itFile -> itFile.name.endsWith('.jar') }))
        }
        return ismlCompileClasspath
    }

    /**
     * File encoding
     */
    @Optional
    @Input
    String encoding

    /**
     * Input files (ISML files)
     */
    @SkipWhenEmpty
    @InputDirectory
    File srcDirectory

    /**
     * Output of this task is a source structure with
     * jsp, java and class files
     */
    @OutputDirectory
    File outputDirectory

    @TaskAction
    void generate() {
        // prepare output directory
        File outputDir = getOutputDirectory()
        prepareDirectory(outputDir)

        // prepare temporary output directory
        File tempPagecompileDir = new File(getTemporaryDir(), PAGECOMPILE_FOLDER)
        prepareDirectory(tempPagecompileDir)

        // prepare web-inf directory
        File webInf = createWebInf(tempPagecompileDir)
        // prepare tag libs configuration
        prepareTagLibs(webInf)

        // create classpath of the project
        String classpath = getClasspathFiles().asPath

        // isml2jsp
        generateJSP(getSrcDirectory(), tempPagecompileDir, classpath)
        // jsp2java
        generateJava(tempPagecompileDir, classpath)
        // compile
        compile(tempPagecompileDir, classpath)

        // copy result
        File pageCompileFolder = new File(outputDir, PAGECOMPILE_FOLDER)
        project.copy {
            from(tempPagecompileDir) {
                exclude FILTER_WEBINF
            }
            into pageCompileFolder
        }

        // unify time stamp - all files must have the same timestamp
        unifyTimestamps(pageCompileFolder)
    }

    /**
     * Create temporary web.xml for JSP task
     *
     * @param rootWebDir     root dir for jsp compile
     * @return web-inf directory of root web directory
     */
    private File createWebInf(File rootWebDir) {
        File webXml = new File(rootWebDir, WEB_XML_PATH)
        if(! webXml.exists()) {
            webXml.getParentFile().mkdirs()
        } else {
            webXml.delete()
        }
        webXml << WEB_XML_CONTENT

        return webXml.getParentFile()
    }

    /**
     * Copy taglibs to the correct folder for JSP compile
     *
     * @param webInfFolder web-inf directory of root web directory
     */
    private void prepareTagLibs(File webInfFolder) {
        //1. Identify own taglibs
        File projectTagsFolder = project.file(TAGLIB_SOURCE_FOLDER)
        File projectWebInfTagFolder = new File(webInfFolder, "${TAGLIB_FOLDER}/${project.name}")
        if(projectTagsFolder.exists() && projectTagsFolder.listFiles().size() > 0) {
            project.logger.debug('prepare taglibs of the current project')
            project.copy {
                from(projectTagsFolder)
                into(projectWebInfTagFolder)
            }
        }

        //2. Identify taglibs of multiproject dependencies
        project.configurations.collectMany { it.allDependencies }.findAll { it instanceof ProjectDependency } .unique().each { ProjectDependency pd ->
            projectTagsFolder = new File(pd.dependencyProject.projectDir, TAGLIB_SOURCE_FOLDER)
            projectWebInfTagFolder = new File(webInfFolder, "${TAGLIB_FOLDER}/${pd.dependencyProject.name}")
            if(projectTagsFolder.exists() && projectTagsFolder.listFiles().size() > 0) {
                project.logger.debug('prepare taglibs of project {}', pd.dependencyProject.name)
                project.copy {
                    from(projectTagsFolder)
                    into(projectWebInfTagFolder)
                }
            }
        }

        //3. Identify taglibs of dependencies
        project.configurations.getByName(getIsmlConfigurationName()).getResolvedConfiguration().getResolvedArtifacts().each { ResolvedArtifact artifact ->
            if (artifact.type == 'cartridge') {
                projectWebInfTagFolder = new File(webInfFolder, "${TAGLIB_FOLDER}/${artifact.name}")
                File tempTagsDir = new File(getTemporaryDir(), "tmp${TAGLIB_FOLDER}/${artifact.name}")
                project.copy {
                    from project.zipTree(artifact.getFile())
                    include FILTER_TAGLIB
                    into tempTagsDir
                }
                projectTagsFolder = new File(tempTagsDir,"${artifact.name}/${RELEASE_TAGLIB_FOLDER}")
                if(projectTagsFolder.exists() && projectTagsFolder.listFiles().size() > 0) {
                    project.logger.debug('prepare taglibs of project {}', artifact.name)
                    project.copy {
                        from(projectTagsFolder)
                        into(projectWebInfTagFolder)
                    }
                }
            }
        }
    }

    /**
     * Removes the dir and creates the directory
     * @param dir
     */
    @CompileStatic
    private static void prepareDirectory(File dir) {
        dir.deleteDir()
        dir.mkdirs()
    }

    /**
     * ISML to JSP compilation
     * This step uses the compile classpath of the component.
     *
     * @param ismlSrcDir source directory with ISML files
     * @param pageCompileDir output directory of this step
     * @param classpath of the project extended with build files and pagecompile dir
     */
    private void generateJSP(File ismlSrcDir, File pageCompileDir, String classpath) {
        project.logger.info('Compile isml templates to jsp from {} into {}', ismlSrcDir, pageCompileDir)

        //intialize ant task
        ant.taskdef (name : 'ISML2JSP',
            classname : ISML_ANTTASK_CLASSNAME,
            classpath : classpath)
        
        //run anttask
        ant.ISML2JSP(
                srcdir:  ismlSrcDir,
                destdir: pageCompileDir,
                contentEncoding : getEncoding()) {
            JspEncoding(
                mimeType : 'text/html',
                encoding : getEncoding()
            )
        }
    }

    /**
     * Generate java files from jsp files.
     *
     * @param pageCompileDir directory with jsp files
     * @param classpath of the project extended with build files and pagecompile dir
     */
    private void generateJava(File pageCompileDir, String classpath) {
        project.logger.info('Compile jsp templates to java in {}', pageCompileDir)

        FileCollection jspCompilerConfiguration = project.getConfigurations().getAt(IsmlExtension.JSPJASPERCOMPILER_CONFIGURATION_NAME)

        //intialize ant task
        ant.taskdef (
                name : 'JASPER',
                classname : JSP_ANTTASK_CLASSNAME,
                classpath : jspCompilerConfiguration.asPath
        )

        //run anttask
        ant.JASPER(
                uriroot:  pageCompileDir,
                outputDir: pageCompileDir,
                package: makeJavaPackageFromPackage(getJspPackage()),
                classpath: classpath,
                verbose: '0',
                javaEncoding: getEncoding(),
                compilerTargetVM: targetCompatibility,
                compilerSourceVM: sourceCompatibility)
    }

    @CompileStatic
    private void compile(File pageCompileDir, String classpath) {
        project.logger.info('Compile java templates to class in {}', pageCompileDir)

        JavaExecHandleBuilder execHandler = prepareCompilerExec(pageCompileDir, classpath)
        if (execHandler) {
            execHandler.build().start().waitForFinish().assertNormalExitValue()
        }
    }

    @CompileStatic
    private JavaExecHandleBuilder prepareCompilerExec(File pageCompileDir, String classpath) {
        FileCollection eclipseCompilerConfiguration = getProject().getConfigurations().getAt(IsmlExtension.ECLIPSECOMPILER_CONFIGURATION_NAME)

        File confFile = new File(getTemporaryDir(), "eclipsecompiler.config")

        // confFile must be handled like input parameter ...
        if(confFile.exists()) {
            confFile.delete()
        }
        confFile << "-g -nowarn -encoding ${getEncoding()} -target ${getTargetCompatibility()} -source ${getSourceCompatibility()} -classpath ${classpath}"

        JavaExecHandleBuilder javaExec = new JavaExecHandleBuilder(getFileResolver())
        getEclipseCompilerJavaOptions().copyTo(javaExec)

        return javaExec
                .setClasspath(eclipseCompilerConfiguration)
                .setMain(ECLIPSE_COMPILER_CLASSNAME)
                .setArgs(["@${confFile.absolutePath}", pageCompileDir.absolutePath] )
    }

    // Unifies the timestamps of the compiled *.jsp, *.java and *.class files.
    // This is necessary so the Jasper compiler will not re-compile them on
    // first access.
    @CompileStatic
    private void unifyTimestamps(File pageCompileDir) {
        project.logger.info('Unifying compiled template timestamps in {}.', pageCompileDir)
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
        char[] result = new char[5]
        result[0] = '_'
        result[1] = Character.forDigit((ch >> 12) & 0xf, 16)
        result[2] = Character.forDigit((ch >> 8) & 0xf, 16)
        result[3] = Character.forDigit((ch >> 4) & 0xf, 16)
        result[4] = Character.forDigit(ch & 0xf, 16)
        return new String(result);
    }

    private static boolean isJavaKeyword(String key) {
        int i = 0;
        int j = JAVA_KEYWORDS.length;
        while (i < j) {
            int k = (i + j) / 2;
            int result = JAVA_KEYWORDS[k].compareTo(key);
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
    @CompileStatic
    @Internal
    public JavaForkOptions getEclipseCompilerJavaOptions() {
        if (eclipseCompilerJavaOptions == null) {
            eclipseCompilerJavaOptions = new DefaultJavaForkOptions(getFileResolver())
        }

        return eclipseCompilerJavaOptions
    }

    @CompileStatic
    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }
}
