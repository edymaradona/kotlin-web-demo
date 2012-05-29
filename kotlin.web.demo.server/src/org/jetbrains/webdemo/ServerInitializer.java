/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.webdemo;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.cli.jvm.compiler.CompileEnvironmentUtil;
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment;
import org.jetbrains.jet.cli.jvm.compiler.K2JVMCompileEnvironmentConfiguration;
import org.jetbrains.jet.internal.com.intellij.lang.java.JavaParserDefinition;
import org.jetbrains.jet.internal.com.intellij.openapi.Disposable;
import org.jetbrains.jet.internal.com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.jet.internal.com.intellij.openapi.fileTypes.FileTypeRegistry;
import org.jetbrains.jet.internal.com.intellij.openapi.util.Getter;
import org.jetbrains.jet.internal.com.intellij.openapi.vfs.encoding.EncodingRegistry;
import org.jetbrains.jet.lang.parsing.JetParserDefinition;
import org.jetbrains.jet.lang.resolve.java.CompilerDependencies;
import org.jetbrains.jet.lang.resolve.java.CompilerSpecialMode;
import org.jetbrains.jet.plugin.JetFileType;
import org.jetbrains.webdemo.server.ApplicationSettings;

import java.io.File;
import java.net.URL;

/**
 * Created by IntelliJ IDEA.
 * User: Natalia.Ukhorskaya
 * Date: 10/14/11
 * Time: 3:49 PM
 */
public class ServerInitializer extends Initializer {
    private static ServerInitializer initializer = new ServerInitializer();

    private static Getter<FileTypeRegistry> registry;
    private static Disposable root;

    public static ServerInitializer getInstance() {
        return initializer;
    }

    private ServerInitializer() {
    }

    private static JetCoreEnvironment environment;

    public JetCoreEnvironment getEnvironment() {
        if (environment != null) {
            return environment;
        }
        ErrorWriterOnServer.LOG_FOR_EXCEPTIONS.error(ErrorWriter.getExceptionForLog("initialize", "JavaCoreEnvironment is null.", "null"));
        return null;
    }

    @Override
    public Getter<FileTypeRegistry> getRegistry() {
        return registry;
    }

    @Override
    public Disposable getRoot() {
        return root;
    }

    public boolean setJavaCoreEnvironment() {
        File rtJar = findRtJar();
        if (rtJar == null) {
            ErrorWriterOnServer.writeErrorToConsole("Returned rtJar is null.");
            return false;
        }
        environment.addToClasspath(rtJar);
        if (!initializeKotlinRuntime()) {
            ErrorWriterOnServer.writeInfoToConsole("Cannot found Kotlin Runtime library.");
        }
        environment.registerFileType(JetFileType.INSTANCE, "kt");
        environment.registerFileType(JetFileType.INSTANCE, "kts");
        environment.registerFileType(JetFileType.INSTANCE, "ktm");
        environment.registerFileType(JetFileType.INSTANCE, "jet");
        environment.registerParserDefinition(new JetParserDefinition());
        environment.registerParserDefinition(new JavaParserDefinition());

        registry = FileTypeRegistry.ourInstanceGetter;

        return true;
    }

    public boolean initializeKotlinRuntime() {
        final File unpackedRuntimePath = getUnpackedRuntimePath();
        if (unpackedRuntimePath != null) {
            ApplicationSettings.KOTLIN_LIB = unpackedRuntimePath.getAbsolutePath();
            ErrorWriter.writeInfoToConsole("Kotlin Runtime library found at " + ApplicationSettings.KOTLIN_LIB);
            environment.addToClasspath(unpackedRuntimePath);
        } else {
            final File runtimeJarPath = getRuntimeJarPath();
            if (runtimeJarPath != null && runtimeJarPath.exists()) {
                environment.addToClasspath(runtimeJarPath);
                ApplicationSettings.KOTLIN_LIB = runtimeJarPath.getAbsolutePath();
                ErrorWriter.writeInfoToConsole("Kotlin Runtime library found at " + ApplicationSettings.KOTLIN_LIB);
            } else {
                return false;
            }
        }
        return true;
    }

    public static File getUnpackedRuntimePath() {
        URL url = K2JVMCompileEnvironmentConfiguration.class.getClassLoader().getResource("jet/JetObject.class");
        if (url != null && url.getProtocol().equals("file")) {
            return new File(url.getPath()).getParentFile().getParentFile();
        }
        return null;
    }

    public static File getRuntimeJarPath() {
        URL url = K2JVMCompileEnvironmentConfiguration.class.getClassLoader().getResource("kotlin/namespace.class");
        if (url != null && url.getProtocol().equals("jar")) {
            String path = url.getPath();
            return new File(path.substring(path.indexOf(":") + 1, path.indexOf("!/")));
        }
        return null;
    }

    public boolean initJavaCoreEnvironment() {
        if (environment == null) {

            root = new Disposable() {
                @Override
                public void dispose() {
                }
            };
            environment = new JetCoreEnvironment(root, CompilerDependencies.compilerDependenciesForProduction(CompilerSpecialMode.REGULAR));

            return setJavaCoreEnvironment();
        }
        return true;
    }

    public static void reinitializeJavaEnvironment() {
        ApplicationManager.setApplication(environment.getApplication(), registry, EncodingRegistry.ourInstanceGetter, root);
    }

    @Nullable
    private File findRtJar() {
        File rtJar;
        if (!ApplicationSettings.RT_JAR.equals("")) {
            rtJar = new File(ApplicationSettings.RT_JAR);
        } else {
            rtJar = CompileEnvironmentUtil.findRtJar();
        }
        if ((rtJar == null || !rtJar.exists())) {
            if (ApplicationSettings.JAVA_HOME == null) {
                ErrorWriter.writeInfoToConsole("You can set java_home variable at config.properties file.");
            } else {
                ErrorWriter.writeErrorToConsole("No rt.jar found under JAVA_HOME=" + ApplicationSettings.JAVA_HOME + " or path to rt.jar is incorrect " + ApplicationSettings.RT_JAR);
            }
            return null;
        }
        ApplicationSettings.JAVA_HOME = rtJar.getParentFile().getParentFile().getParentFile().getAbsolutePath();
        return rtJar;
    }


}


