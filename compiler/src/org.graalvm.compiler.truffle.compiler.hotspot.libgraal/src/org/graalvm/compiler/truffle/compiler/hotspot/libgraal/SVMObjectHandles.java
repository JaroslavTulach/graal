/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.compiler.hotspot.libgraal;

import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.word.WordFactory;

/**
 * Manages handles to SVM objects whose lifetime is controlled by
 * {@code org.graalvm.compiler.truffle.runtime.hotspot.libgraal.SVMObject} instances in the HotSpot
 * heap.
 */
final class SVMObjectHandles {

    /**
     * Creates a handle to {@code object}. The object is kept alive at least until
     * {@link #remove(long)} is called on the returned handle.
     */
    static long create(Object object) {
        return ObjectHandles.getGlobal().create(object).rawValue();
    }

    /**
     * Resolves {@code handle} to an object and casts it to {@code type}.
     *
     * @param type the expected type of the object
     * @return the object identified by the handle
     * @throws IllegalArgumentException if {@code} is invalid
     */
    static <T> T resolve(long handle, Class<T> type) {
        return type.cast(ObjectHandles.getGlobal().get(WordFactory.pointer(handle)));
    }

    /**
     * Releases the reference to the object associated with {@code handle}. After calling this
     * method, the handle must not be used anymore.
     */
    static void remove(long handle) {
        ObjectHandles.getGlobal().destroy(WordFactory.pointer(handle));
    }
}
