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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.GraalCompiler;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import static org.junit.Assert.assertNotNull;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class JavaOutputTest extends GraalCompilerTest {
    static final Set<DeoptimizationReason> EMPTY = Collections.<DeoptimizationReason> emptySet();

    @BeforeClass
    public static void skipIfNoTool() {
        try {
            Class.forName("javax.tools.JavaFileObject");
            // OK
        } catch (ClassNotFoundException ex) {
            Assume.assumeNoException("Skipping as Javac compiler cannot be found", ex);
        }
    }

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
            GraalCompiler.Request<CompilationResult> request = new GraalCompiler.Request<>(graphToCompile, installedCodeOwner, getProviders(), getBackend(), getDefaultGraphBuilderSuite(), getOptimisticOptimizations(),
                    graphToCompile.getProfilingInfo(), createSuites(options), createLIRSuites(options), compilationResult, CompilationResultBuilderFactory.Default, true);
            GraalCompiler.compile(request).getMethods();
            assert optimizedGraph != null;
            return optimizedGraph;
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        optimizedGraph = graph;
    }

    @Override
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        optimize(installedCodeOwner, graph, compilationResult, compilationId, options);
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

    @SuppressWarnings("unused")
    protected void runTest(OptionValues options, Set<DeoptimizationReason> shouldNotDeopt, boolean bind, boolean noProfile, String name, Object... args) {
        ResolvedJavaMethod method = getResolvedJavaMethod(name);
        Object receiver = method.isStatic() ? null : this;

        Result expect = executeExpected(method, receiver, args);

        if (noProfile) {
            method.reprofile();
        }

        executeActualCheckDeopt(options, method, shouldNotDeopt, receiver, args);
        final StructuredGraph graph = optimizedGraph;
        Object actual = invokeCode(graph, method, receiver, args);
        Assert.assertEquals("The same result produced by:\n" + code, expect.returnValue, actual);
    }

    protected Object invokeCode(StructuredGraph graph, ResolvedJavaMethod method, Object receiver, Object... args) throws IllegalStateException {
        assertNotNull("Optimized graph generated", graph);

        JavaOutput generator = new JavaOutput(method, graph);

        StringBuilder sb = new StringBuilder();
        sb.append("\npublic class Generated {\n");
        ResolvedJavaMethod m = graph.method();
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
        byte[] generated = JavaCompilation.assertCompile(code);
        assertNotNull("Bytes found", generated);

        Method generatedMethod = JavaCompilation.assertMethod("Generated", generated, "test");
        try {
            return generatedMethod.invoke(receiver, args);
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected final void assertCode(String msg, String snippet, int minCount, int maxCount) {
        assertNotNull(msg, code);
        int cnt = 0;
        int at = -1;
        for (;;) {
            int next = code.indexOf(snippet, at + 1);
            if (next == -1) {
                break;
            }
            cnt++;
            at = next;
        }
        assertTrue(msg + " found " + cnt + " in\n" + code, minCount <= cnt && maxCount >= cnt);
    }
}
