/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import static org.graalvm.compiler.core.test.GraalCompilerTest.getInitialOptions;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.junit.Assert;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public abstract class JavaOutputTest extends GraalCompilerTest {
    protected static final Set<DeoptimizationReason> EMPTY = Collections.<DeoptimizationReason> emptySet();

    /**
     * The arguments which, if non-null, will replace the Locals in the test method's graph.
     */
    Object[] argsToBind;
    private StructuredGraph optimizedGraph;
    private String code;

    @SuppressWarnings("try")
    protected StructuredGraph optimize(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        StructuredGraph graphToCompile = graph == null ? parseForCompile(installedCodeOwner, compilationId, options) : graph;
        lastCompiledGraph = graphToCompile;
        DebugContext debug = graphToCompile.getDebug();
        try (DebugContext.Scope s = debug.scope("Compile", graphToCompile)) {
            assert options != null;
            GraalCompiler.Request<CompilationResult> request = new GraalCompiler.Request<>(graphToCompile, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(),
                            OptimisticOptimizations.ALL,
                            graphToCompile.getProfilingInfo(), createSuites(options), createLIRSuites(options), compilationResult, CompilationResultBuilderFactory.Default);
            GraalCompiler.emitFrontEnd(
                            request.providers, request.backend, request.graph, request.graphBuilderSuite,
                            request.optimisticOpts, request.profilingInfo, request.suites);
            return request.graph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    @Override
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        optimizedGraph = optimize(installedCodeOwner, graph, compilationResult, compilationId, options);
        return null;
    }

    protected void runTest(String name, Object... args) {
        runTest(getInitialOptions(), name, args);
    }

    protected void runTest(OptionValues options, String name, Object... args) {
        runTest(options, EMPTY, true, false, name, args);
    }

    protected void runTest(Set<DeoptimizationReason> shouldNotDeopt, String name, Object... args) {
        runTest(getInitialOptions(), shouldNotDeopt, true, false, name, args);
    }

    protected void runTest(OptionValues options, Set<DeoptimizationReason> shouldNotDeopt, boolean bind, boolean noProfile, String name, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Object receiver = method.isStatic() ? null : this;

        Result expect = executeExpected(method, receiver, args);

        if (noProfile) {
            method.reprofile();
        }

        executeActualCheckDeopt(options, method, shouldNotDeopt, receiver, args);
        assertNotNull("Optimized graph generated", optimizedGraph);

        JavaOutput generator = new JavaOutput(optimizedGraph);

        StringBuilder sb = new StringBuilder();
        sb.append("\npublic class Generated {\n");
        ResolvedJavaMethod m = optimizedGraph.getMethods().get(0);
        sb.append("  public static ").append(m.getSignature().getReturnType(m.getDeclaringClass()).toJavaName());
        sb.append(" test(");
        for (int i = 0; i < m.getParameters().length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            final ResolvedJavaMethod.Parameter p = m.getParameters()[i];
            sb.append(p.getType().toJavaName()).append(" ").append(p.getName());
        }
        sb.append(") {\n");
        try {
            generator.generate(sb, "    ");
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        sb.append("\n  }");
        sb.append("\n}");

        code = sb.toString();
        byte[] generated = assertCompile(code);
        assertNotNull("Bytes found", generated);

        Method generatedMethod = assertMethod("Generated", generated, "test");
        try {
            Object actual = generatedMethod.invoke(receiver, args);
            Assert.assertEquals("The same result produced by:\n" + sb.toString(), expect.returnValue, actual);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] assertCompile(String code) {
        try {
            JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();

            JavaFileObject file = new SimpleJavaFileObject(new URI("mem://Generated.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public String getName() {
                    return "Generated.java";
                }

                @Override
                public boolean isNameCompatible(String simpleName, JavaFileObject.Kind kind) {
                    if (!kind.equals(getKind())) {
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

    private Method assertMethod(String className, byte[] code, String methodName) {
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
