package org.graalvm.compiler.javaout.test;

import java.util.Collections;
import java.util.Set;
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
import static org.junit.Assert.assertNotNull;

public abstract class JavaOutputTest extends GraalCompilerTest {
    protected static final Set<DeoptimizationReason> EMPTY = Collections.<DeoptimizationReason> emptySet();

    /**
     * The arguments which, if non-null, will replace the Locals in the test method's graph.
     */
    Object[] argsToBind;
    private StructuredGraph optimizedGraph;

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

    }
}
