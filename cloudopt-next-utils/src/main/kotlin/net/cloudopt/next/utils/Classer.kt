/*
 * Copyright 2017 Cloudopt.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package net.cloudopt.next.utils

import java.io.File
import java.io.FileFilter
import java.io.IOException
import java.net.URL
import java.net.URLDecoder
import java.util.*
import java.util.jar.JarFile

/*
 * @author: Cloudopt
 * @Time: 2018/1/4
 * @Description: This is for scanning tools class
 */
object Classer {

    private val CLASS_EXT = ".class"

    private val JAR_FILE_EXT = ".jar"

    private val JAR_PATH_EXT = ".jar!"

    private val PATH_FILE_PRE = "file:"

    /**
     * @return Get the Java ClassPath path, excluding jre
     */
    val javaClassPaths: Array<String>
        get() = System.getProperty("java.class.path").split(System.getProperty("path.separator").toRegex())
            .dropLastWhile { it.isEmpty() }.toTypedArray()

    /**
     * Get the current thread's ClassLoader
     * @return The current thread's class loader
     */
    val contextClassLoader: ClassLoader
        get() = Thread.currentThread().contextClassLoader

    /**
     * Get class loader
     * If the current thread class loader does not exist, take the current class loader class
     * @return class loader
     */
    val classLoader: ClassLoader
        get() {
            var classLoader: ClassLoader? = contextClassLoader
            return classLoader ?: Classer::class.java.classLoader
        }

    //--------------------------------------------------------------------------------------------------- Private method start
    /**
     * This is a file filter for filtering out unneeded files
     * (leaving only Class files, directories, and Jars)
     */
    private val fileFilter =
        FileFilter { pathname -> isClass(pathname.name) || pathname.isDirectory || isJarFile(pathname) }

    /**
     * This is used to scan for all classes containing specified annotations in the path of the specified package
     * @param packageName  Package path
     * @param inJar  Find in the jar package
     * @param annotationClass  Annotation class
     * @return Collection of classes
     */
    fun scanPackageByAnnotation(
        packageName: String,
        inJar: Boolean,
        annotationClass: Class<out Annotation>
    ): Set<Class<*>> {
        return scanPackage(packageName, inJar, object : ClassFilter {
            override fun accept(clazz: Class<*>): Boolean {
                return clazz.isAnnotationPresent(annotationClass)
            }
        })
    }

    /**
     * This is a subclass of all the specified classes for scanning the path to the specified package
     * @param packageName  Package path
     * @param inJar  Find in the jar package
     * @param superClass  Father class
     * @return Collection of classes
     */
    fun scanPackageBySuper(packageName: String, inJar: Boolean, superClass: Class<*>): Set<Class<*>> {
        return scanPackage(packageName, inJar, object : ClassFilter {
            override fun accept(clazz: Class<*>): Boolean {
                return superClass.isAssignableFrom(clazz) && superClass != clazz
            }
        })
    }

    /**
     * This is used to scan the package path to meet the class filter all the class files
     * If the package path com.abs + A.class but enter abs, will produce classNotFoundException
     * Because className should be com.abs.A, and now it becomes abs.A
     * This tool to ignore the exception handling, there may be an imperfect place, need to be modified later
     *
     * @param packageName Package  path com | com. | com.abs | com.abs.
     * @param inJar  Find in the jar package
     * @param This is a classFilter class filter, which is used to filter out unneeded class
     * @return Collection of classes
     */
    fun scanPackage(packageName: String?, inJar: Boolean, classFilter: ClassFilter): Set<Class<*>> {
        var packageName = packageName
        if (packageName == null) {
            packageName = ""
        }
        packageName = getWellFormedPackageName(packageName!!)

        val classes = HashSet<Class<*>>()
        for (classPath in getClassPaths(packageName)) {
            // This is used to populate the classes and decode the classpath
            fillClasses(decodeUrl(classPath), packageName, classFilter, classes)
        }
        // If you do not find packageName in your project's ClassPath, go to your system-defined ClassPath
        if (inJar) {
            for (classPath in javaClassPaths) {
                // This is used to populate the classes and decode the classpath
                fillClasses(decodeUrl(classPath), File(classPath), packageName, classFilter, classes)
            }
        }
        return classes
    }

    /**
     * Get ClassPath
     * @param packageName  The name of the package
     * @return A collection of strings for the ClassPath path
     */
    fun getClassPaths(packageName: String): Set<String> {
        val packagePath = packageName.replace(".", "/")
        val resources: Enumeration<URL>
        try {
            resources = classLoader.getResources(packagePath)
        } catch (e: IOException) {
            throw RuntimeException(String.format("Loading classPath [%s] error!", packageName), e)
        }

        val paths = HashSet<String>()
        while (resources.hasMoreElements()) {
            paths.add(resources.nextElement().path)
        }
        return paths
    }

    /**
     * It will load the class according to the specified class name
     * @param className  The name of the complete class
     * @return {Class}
     * @throws ClassNotFoundException  Can not find abnormalities
     */
    @Throws(ClassNotFoundException::class)
    fun loadClass(className: String): Class<*> {
        try {
            return contextClassLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            try {
                return Class.forName(className, false, classLoader)
            } catch (ex: ClassNotFoundException) {
                try {
                    return ClassLoader::class.java.classLoader.loadClass(className)
                } catch (exc: ClassNotFoundException) {
                    throw exc
                }

            }

        }

    }

    /**
     * Change com -> com. It is used to avoid comparing scan times like for example completeTestSuite.class
     * If there is no ".", The class class at the beginning of the class will also be scanned into
     * In fact, only need a "." Behind or in front of the name, you can use to add the package features
     *
     * @param packageName Package name
     * @return After the format of the package name
     */
    private fun getWellFormedPackageName(packageName: String): String {
        return if (packageName.lastIndexOf('.') != packageName.length - 1) packageName + '.' else packageName
    }

    /**
     * This is used to remove the specified prefix
     *
     * @param str  String
     * @param prefix  Prefix
     * @return Cut off the string, if the prefix is not preffix, then return to the original string
     */
    private fun removePrefix(str: String?, prefix: String): String? {
        return if (str != null && str.startsWith(prefix)) {
            str.substring(prefix.length)
        } else str
    }

    /**
     * Fill meet the conditions of the class will be filled into the classes
     * At the same time, it will also determine whether the given path is a path within the Jar package, and if so, scan the Jar package
     *
     * @param Path Class file path or the directory where the Jar package path
     * @param packageName Need to scan the package name
     * @param classFilter Class filter
     * @param classes A collection of list
     */
    private fun fillClasses(
        path: String,
        packageName: String,
        classFilter: ClassFilter,
        classes: MutableSet<Class<*>>
    ) {
        var path = path
        // This is is used to determine if the given path is Jar
        val index = path.lastIndexOf(JAR_PATH_EXT)
        if (index != -1) {
            //Jar file
            path = path.substring(0, index + JAR_FILE_EXT.length)    // Intercept the path to the jar

            path = removePrefix(path, PATH_FILE_PRE) ?: ""    // Used to remove the file prefix

            processJarFile(File(path), packageName, classFilter, classes)
        } else {
            fillClasses(path, File(path), packageName, classFilter, classes)
        }
    }

    /**
     * Fill meet the conditions of the class will be filled into the classes
     *
     * @param classPath This is the directory where the class file is located. This parameter is used when the package name is empty. It is used to cut the file path in front of the class name
     * @param The target folder under the class file or jar file
     * @param packageName Need to scan the package name
     * @param classFilter Class filter
     * @param classes A collection of list
     */
    private fun fillClasses(
        classPath: String,
        file: File,
        packageName: String,
        classFilter: ClassFilter,
        classes: MutableSet<Class<*>>
    ) {
        if (file.isDirectory) {
            processDirectory(classPath, file, packageName, classFilter, classes)
        } else if (isClassFile(file)) {
            processClassFile(classPath, file, packageName, classFilter, classes)
        } else if (isJarFile(file)) {
            processJarFile(file, packageName, classFilter, classes)
        }
    }

    /**
     * This is for dealing with situations such as directories, you need to call the fillClasses method recursively
     *
     * @param directory Directory
     * @param packageName Package name
     * @param classFilter Class filter
     * @param classes  Collection of classes
     */
    private fun processDirectory(
        classPath: String,
        directory: File,
        packageName: String,
        classFilter: ClassFilter,
        classes: MutableSet<Class<*>>
    ) {
        for (file in directory.listFiles(fileFilter)!!) {
            fillClasses(classPath, file, packageName, classFilter, classes)
        }
    }

    /**
     *  This is used to handle the case of a file as a class file, filling the class to classes that meet the conditions
     *
     * @param classPath Class file directory, use this parameter when the package name is empty, used to cut off the file path in front of the class name
     * @param file class file
     * @param packageName Package name
     * @param classFilter Class filter
     * @param classes Collection of classes
     */
    private fun processClassFile(
        classPath: String,
        file: File,
        packageName: String?,
        classFilter: ClassFilter,
        classes: MutableSet<Class<*>>
    ) {
        var classPath = classPath
        if (false == classPath.endsWith(File.separator)) {
            classPath += File.separator
        }
        var path: String? = file.absolutePath
        if (packageName?.isNotBlank() ?: false) {
            path = removePrefix(path, classPath)
        }
        val filePathWithDot = path!!.replace(File.separator, ".")

        var subIndex = -1
        subIndex = filePathWithDot.indexOf(packageName ?: "")
        if (subIndex != -1) {
            val endIndex = filePathWithDot.lastIndexOf(CLASS_EXT)

            val className = filePathWithDot.substring(subIndex, endIndex)
            fillClass(className, packageName ?: "", classes, classFilter)
        }
    }

    /**
     * This is the case for handling files as jar files and will fill the class-to-classes that satisfy the condition
     *
     * @param file jar file
     * @param packageName Package name
     * @param classFilter Class filter
     * @param classes Collection of classes
     */
    private fun processJarFile(
        file: File,
        packageName: String,
        classFilter: ClassFilter,
        classes: MutableSet<Class<*>>
    ) {
        try {
            for (entry in Collections.list(JarFile(file).entries())) {
                if (isClass(entry.name)) {
                    val className = entry.name.replace("/", ".").replace(CLASS_EXT, "")
                    fillClass(className, packageName, classes, classFilter)
                }
            }
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }

    }

    /**
     * It will populate the class to classes
     *
     * @param className Class name
     * @param packageName Package name
     * @param classes Collection of classes
     * @param classFilter Class filter
     */
    private fun fillClass(
        className: String,
        packageName: String,
        classes: MutableSet<Class<*>>,
        classFilter: ClassFilter?
    ) {
        if (className.startsWith(packageName)) {
            try {
                val clazz = loadClass(className)
                if (classFilter == null || classFilter.accept(clazz)) {
                    classes.add(clazz)
                }
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }

        }
    }

    /**
     * @param file File
     * @return Is it a class file?
     */
    private fun isClassFile(file: File): Boolean {
        return isClass(file.name)
    }

    /**
     * @param fileName File name
     * @return Is it a class file?
     */
    private fun isClass(fileName: String): Boolean {
        return fileName.endsWith(CLASS_EXT)
    }

    /**
     * @param file file
     * @return Is it a Jar file?
     */
    private fun isJarFile(file: File): Boolean {
        return file.name.endsWith(JAR_FILE_EXT)
    }
    //--------------------------------------------------------------------------------------------------- Private method end

    /**
     * This is a class filter, which is used to filter classes that do not need to be loaded
     */
    interface ClassFilter {
        fun accept(clazz: Class<*>): Boolean
    }

    /**
     * This is used to decode the path
     * @param url Path
     * @return String After decoding the path
     */
    private fun decodeUrl(url: String): String {
        try {
            return URLDecoder.decode(url, "UTF-8")
        } catch (e: java.io.UnsupportedEncodingException) {
            throw RuntimeException(e)
        }

    }

}