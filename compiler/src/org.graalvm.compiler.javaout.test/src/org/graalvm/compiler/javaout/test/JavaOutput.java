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

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.FixedBinaryNode;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;

public final class JavaOutput {
    private final Set<PhiNode> phis = new LinkedHashSet<>();
    private final StructuredGraph g;
    private final ResolvedJavaMethod method;

    public JavaOutput(ResolvedJavaMethod method, StructuredGraph g) {
        this.g = g;
        this.method = method;
    }

    public void generate(StringBuilder out, String sep) throws IOException {
        int length = out.length();
        dump(out, g.start(), sep);
        out.insert(length, declarePhis());
    }

    private void dump(Appendable out, Node at, String sep) throws IOException {
        final String moreSep = sep + "  ";
        if (at instanceof ReturnNode) {
            out.append(sep).append("return ");
            expr(out, ((ReturnNode) at).result(), "");
            out.append(";");
            return;
        }
        if (at instanceof StartNode || at instanceof BeginNode) {
            processSuccessors(at, out, sep);
            return;
        }
        if (at instanceof IfNode) {
            out.append(sep).append("if (");
            expr(out, ((IfNode) at).condition(), "");
            out.append(") {\n");
            dump(out, ((IfNode) at).trueSuccessor(), moreSep);
            out.append("\n").append(sep).append("} else {\n");
            dump(out, ((IfNode) at).falseSuccessor(), moreSep);
            out.append("\n").append(sep).append("}\n");
            FixedNode next = ((IfNode) at).trueSuccessor().next();
            if (next instanceof EndNode && !(next instanceof LoopEndNode)) {
                AbstractMergeNode merge = ((AbstractEndNode) next).merge();
                dump(out, merge, sep);
            }
            return;
        }
        if (at instanceof LoopBeginNode) {
            final LoopBeginNode loopBegin = (LoopBeginNode) at;
            for (PhiNode phi : loopBegin.phis()) {
                if (phi.isLoopPhi()) {
                    out.append(sep);
                    out.append("phi").append(findNodeId(phi)).append(" = ");
                    expr(out, phi.firstValue(), sep);
                    out.append(";").append("\n");
                }
            }
            out.append(sep).append("LOOP" + findNodeId(loopBegin)).append(": for (;;) {\n");
            processSuccessors(at, out, moreSep);
            out.append(sep).append("}\n");
            return;
        }
        if (at instanceof LoopEndNode) {
            LoopEndNode loopEnd = (LoopEndNode) at;
            computePhis(loopEnd, out, sep, true);
            out.append(sep).append("continue LOOP").append(findNodeId(loopEnd.loopBegin())).append(";\n");
            return;
        }
        if (at instanceof LoopExitNode) {
            // LoopExitNode loopExit = (LoopExitNode) at;
            // out.append(sep).append("break LOOP").append(findNodeId(loopExit.loopBegin() + ";\n");
            processSuccessors(at, out, sep);
            return;
        }
        if (at instanceof EndNode) {
            if (((EndNode) at).merge() instanceof MergeNode) {
                computePhis((AbstractEndNode) at, out, sep, false);
                return;
            }
        }
        if (at instanceof InvokeNode) {
            CallTargetNode ct = ((InvokeNode) at).callTarget();
            out.append(sep);
            if (ct.targetMethod().getSignature().getReturnKind() != JavaKind.Void) {
                out.append(ct.targetMethod().getSignature().getReturnType(null).toJavaName());
                out.append(" inv").append(findNodeId(at)).append(" = ");
            }
            if (method == ct.targetMethod()) {
                out.append("test");
            } else {
                out.append(method.getName());
            }
            out.append("(");
            String del = "";
            for (ValueNode arg : ct.arguments()) {
                out.append(del);
                expr(out, arg, sep);
                del = ", ";
            }
            out.append(");\n");
            processSuccessors(at, out, sep);
            return;
        }
        if (at instanceof MergeNode) {
            processSuccessors(at, out, moreSep);
            return;
        }

        out.append(sep).append("/* node: ");
        out.append(sep).append(at.toString()).append("\n");
        for (Node in : at.inputs()) {
            expr(out, in, moreSep);
            out.append("\n");
        }
        out.append("*/\n");
        processSuccessors(at, out, moreSep);
    }

    private String declarePhis() {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        StringBuilder sb = new StringBuilder();
        for (PhiNode phi : phis) {
            sb.append("    ").append(phi.stamp(NodeView.DEFAULT).javaType(metaAccess).toJavaName());
            sb.append(" phi").append(findNodeId(phi)).append(";\n");
        }
        return sb.toString();
    }

    private void computePhis(AbstractEndNode end, Appendable out, String sep, boolean loopPhiOnly) throws IOException {
        MetaAccessProvider metaAccess = JVMCI.getRuntime().getHostJVMCIBackend().getMetaAccess();
        for (PhiNode phi : end.merge().phis()) {
            if (phi.isLoopPhi() == loopPhiOnly) {
                phis.add(phi);
                out.append(sep).append(phi.stamp(NodeView.DEFAULT).javaType(metaAccess).toJavaName());
                out.append(" newPhi").append(findNodeId(phi)).append(" = ");
                expr(out, phi.valueAt(end), sep);
                out.append(";").append("\n");
            }
        }
        for (PhiNode phi : end.merge().phis()) {
            if (phi.isLoopPhi() == loopPhiOnly) {
                out.append(sep);
                out.append("phi").append(findNodeId(phi));
                out.append(" = newPhi").append(findNodeId(phi));
                out.append(";").append("\n");
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static String findNodeId(Node phi) {
        return Integer.toString(phi.getId());
    }

    private void processSuccessors(Node at, Appendable out, final String moreSep) throws IOException {
        for (Node next : at.cfgSuccessors()) {
            dump(out, next, moreSep);
        }
    }

    private static void expr(Appendable out, Node at, String sep) throws IOException {
        final String moreSep = sep + "  ";
        if (at instanceof ParameterNode) {
            StructuredGraph g = (StructuredGraph) at.graph();
            final int index = ((ParameterNode) at).index();
            if ("org.graalvm.compiler.truffle.runtime.OptimizedCallTarget".equals(g.method().getDeclaringClass().toJavaName()) &&
                            "callRoot".equals(g.method().getName())) {
                out.append(g.method().getParameters()[0].getName());
                out.append("[" + index + "]");
            } else {
                String paramName = g.method().getParameters()[index].getName();
                out.append(paramName);
            }
            return;
        }
        if (at instanceof ConstantNode) {
            String value = ((ConstantNode) at).getValue().toValueString();
            final String txt;
            switch (((ConstantNode) at).getStackKind()) {
                case Boolean:
                case Int:
                case Double:
                    txt = value.toString();
                    break;
                case Byte:
                    txt = "((byte)" + value + ")";
                    break;
                case Short:
                    txt = "((short)" + value + ")";
                    break;
                case Char:
                    txt = "((char)" + value + ")";
                    break;
                case Float:
                    txt = value + "f";
                    break;
                case Long:
                    txt = value + "l";
                    break;
                case Object:
                    txt = value.toString();
                    break;
                default:
                    throw new IllegalStateException("can't convert " + value);
            }
            out.append(txt);
            return;
        }
        if (at instanceof BinaryOpLogicNode || at instanceof BinaryNode || at instanceof FixedBinaryNode) {
            NodeIterable<Node> two = at.inputs();
            assert 2 == two.count();
            out.append("(");
            Iterator<Node> twoIt = two.iterator();
            expr(out, twoIt.next(), "");
            out.append(" ").append(at.getNodeClass().shortName()).append(" ");
            expr(out, twoIt.next(), "");
            out.append(")");
            return;
        }
        if (at instanceof NarrowNode) {
            switch (((NarrowNode) at).getStackKind()) {
                case Byte:
                    out.append("((byte)(");
                    break;
                case Short:
                    out.append("((short)(");
                    break;
                case Int:
                    out.append("((int)(");
                    break;
                default:
                    throw new IllegalStateException("no narrow for " + at);
            }
            expr(out, at.inputs().iterator().next(), "");
            out.append("))");
            return;
        }
        if (at instanceof NegateNode) {
            out.append("-");
            expr(out, ((UnaryNode) at).getValue(), "");
            return;
        }
        if (at instanceof ConditionalNode) {
            out.append("(");
            expr(out, ((ConditionalNode) at).condition(), "");
            out.append(" ? ");
            expr(out, ((ConditionalNode) at).trueValue(), "");
            out.append(" : ");
            expr(out, ((ConditionalNode) at).falseValue(), "");
            out.append(")");
            return;
        }
        if (at instanceof PhiNode) {
            out.append("phi").append(findNodeId(at));
            return;
        }
        if (at instanceof FrameState) {
            return;
        }
        if (at instanceof InvokeNode) {
            out.append("inv").append(findNodeId(at));
            return;
        }
        for (Node next : at.inputs()) {
            expr(out, next, moreSep);
        }
    }
}
