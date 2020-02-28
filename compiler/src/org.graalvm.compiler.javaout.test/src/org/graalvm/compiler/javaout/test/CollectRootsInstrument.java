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

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@TruffleInstrument.Registration(services = Function.class, id = CollectRootsInstrument.ID)
public final class CollectRootsInstrument extends TruffleInstrument {
    public static final String ID = "CollectRootsInstrument";

    private final Map<String, RootCallTarget> namedTargets = Collections.synchronizedMap(new HashMap<>());

    @Override
    protected void onCreate(Env env) {
        final SourceSectionFilter tags = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootBodyTag.class).build();
        env.getInstrumenter().attachExecutionEventFactory(tags, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                final RootNode rootNode = context.getInstrumentedNode().getRootNode();
                namedTargets.put(rootNode.getName(), rootNode.getCallTarget());
                return new ExecutionEventNode() {
                };
            }
        });
        Function<String, RootCallTarget> getter = namedTargets::get;
        env.registerService(getter);
    }

}
