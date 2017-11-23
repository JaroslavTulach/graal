/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.CancellableCompileTask;
import org.graalvm.compiler.truffle.GraalTruffleCompilationListener;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class JavaOutputSL extends JavaOutputTest {
    public Fun compile(String code, String symbol) {
        final Context ctx = Context.create("sl");
        ctx.eval("sl", code);
        Value fn = ctx.importSymbol(symbol);
        return new Fun(fn, symbol);
    }

    @SuppressWarnings("deprecation")
    protected static final OptimizedCallTarget findTarget(String symbol) {
        OptimizedCallTarget found = null;
        for (RootCallTarget target : Truffle.getRuntime().getCallTargets()) {
            if (target instanceof OptimizedCallTarget) {
                OptimizedCallTarget oct = (OptimizedCallTarget) target;
                if (oct.getCompilationProfile() == null) {
                    continue;
                }
                if (oct.getRootNode().getClass().getSimpleName().equals("SLRootNode")) {
                    if (symbol.equals(oct.getRootNode().getName())) {
                        assertNull("No previous target: " + found, found);
                        found = oct;
                    }
                }
            }
        }
        assertNotNull("Target " + symbol + " found", found);
        return found;
    }

    public static final class Fun implements GraalTruffleCompilationListener {
        private final Value fn;
        private final String name;
        private StructuredGraph graph;
        private CancellableCompileTask task;

        private Fun(Value fn, String name) {
            this.fn = fn;
            this.name = name;
        }

        public long call(Object... args) {
            return fn.execute(args).asLong();
        }

        public StructuredGraph compile() {
            OptimizedCallTarget target = findTarget(name);
            GraalTruffleRuntime runtime = (GraalTruffleRuntime) Truffle.getRuntime();
            runtime.addCompilationListener(this);
            task = runtime.submitForCompilation(target);
            try {
                task.getFuture().get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new AssertionError("Error waiting for compilation", ex);
            } catch (CancellationException ok) {
                // OK
            }
            assertTrue("Compilation finished", task.getFuture().isDone());
            runtime.removeCompilationListener(this);
            return graph;
        }

        @Override
        public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
        }

        @Override
        public void notifyCompilationQueued(OptimizedCallTarget target) {
        }

        @Override
        public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        }

        @Override
        public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
        }

        @Override
        public void notifyCompilationStarted(OptimizedCallTarget target) {
        }

        @Override
        public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {
            this.graph = graph;
            this.task.cancel();
        }

        @Override
        public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
        }

        @Override
        public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {
        }

        @Override
        public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        }

        @Override
        public void notifyCompilationDeoptimized(OptimizedCallTarget target, Frame frame) {
        }

        @Override
        public void notifyShutdown(GraalTruffleRuntime runtime) {
        }

        @Override
        public void notifyStartup(GraalTruffleRuntime runtime) {
        }
    }

    private static final class StructuredGraphException extends RuntimeException {
        static final long serialVersionUID = 1L;

        private final StructuredGraph graph;

        public StructuredGraphException(StructuredGraph graph) {
            this.graph = graph;
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }
}
