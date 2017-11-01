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
import java.util.Iterator;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.BeginNode;
import org.graalvm.compiler.nodes.BinaryOpLogicNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StartNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.BinaryNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;
import org.graalvm.compiler.nodes.calc.NegateNode;
import org.graalvm.compiler.nodes.calc.UnaryNode;

public final class JavaOutput {
    private final StructuredGraph g;

    public JavaOutput(StructuredGraph g) {
        this.g = g;
    }

    public void generate(Appendable out, String sep) throws IOException {
        dump(out, g.start(), sep);
    }

    private static void dump(Appendable out, Node at, String sep) throws IOException {
        final String moreSep = sep + "  ";
        if (at instanceof ReturnNode) {
            out.append(sep).append("return ");
            expr(out, ((ReturnNode) at).result(), "");
            out.append(";");
            return;
        }
        if (at instanceof StartNode || at instanceof BeginNode) {
            for (Node next : at.cfgSuccessors()) {
                dump(out, next, sep);
            }
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
            return;
        }

        for (Node in : at.inputs()) {
            out.append(sep).append(" in ").append(in.toString()).append("\n");
            expr(out, in, moreSep);
            out.append("\n");
        }
        out.append(sep).append(at.toString()).append("\n");
        for (Node next : at.cfgSuccessors()) {
            dump(out, next, moreSep);
        }
    }

    private static void expr(Appendable out, Node at, String sep) throws IOException {
        final String moreSep = sep + "  ";
        if (at instanceof ParameterNode) {
            StructuredGraph g = (StructuredGraph) at.graph();
            String paramName = g.getMethods().get(0).getParameters()[((ParameterNode) at).index()].getName();
            out.append(paramName);
            return;
        }
        if (at instanceof ConstantNode) {
            String value = ((ConstantNode) at).getValue().toValueString();
            out.append(value);
            return;
        }
        if (at instanceof BinaryOpLogicNode || at instanceof BinaryNode) {
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
        for (Node next : at.inputs()) {
            expr(out, next, moreSep);
        }
    }
}
