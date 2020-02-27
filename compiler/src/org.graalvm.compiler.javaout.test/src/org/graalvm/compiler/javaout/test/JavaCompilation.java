/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.javaout.test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

final class JavaCompilation {

    static byte[] assertCompile(String code) {
        try {
            JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();

            JavaFileObject file = new SimpleJavaFileObject(new URI("mem://Generated.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public String getName() {
                    return "Generated.java";
                }

                @Override
                public boolean isNameCompatible(String simpleName, JavaFileObject.Kind aKind) {
                    if (!aKind.equals(getKind())) {
                        return false;
                    }
                    return "Generated".equals(simpleName);
                }

                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                    return code;
                }
            };

            final ByteArrayOutputStream os = new ByteArrayOutputStream();
            JavaFileObject classFile = new SimpleJavaFileObject(new URI("mem://Generated.class"), JavaFileObject.Kind.CLASS) {
                @Override
                public OutputStream openOutputStream() throws IOException {
                    return os;
                }
            };

            JavaFileManager jfm = new ForwardingJavaFileManager<StandardJavaFileManager>(compiler.getStandardFileManager(null, null, null)) {
                @Override
                public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location, String className, JavaFileObject.Kind kind, FileObject sibling) throws IOException {
                    if ("Generated".equals(className)) {
                        return classFile;
                    }
                    return super.getJavaFileForOutput(location, className, kind, sibling);
                }
            };

            JavaCompiler.CompilationTask task = compiler.getTask(null, jfm, null, null, null, Collections.singleton(file));
            boolean result = task.call();
            assertTrue(code, result);
            assertNotEquals("Some bytes written", 0, os.size());
            return os.toByteArray();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static Method assertMethod(String className, byte[] code, String methodName) {
        try {
            ClassLoader loader = new ClassLoader() {
                @Override
                protected Class<?> findClass(String name) throws ClassNotFoundException {
                    if (className.equals(name)) {
                        return defineClass(name, code, 0, code.length);
                    }
                    return null;
                }
            };
            Class<?> clazz = loader.loadClass(className);
            for (Method method : clazz.getMethods()) {
                if (methodName.equals(method.getName())) {
                    return method;
                }
            }
            fail("No method " + methodName + " found");
            return null;
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
