/*
 * Copyright (c) 2011, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.spi;

import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

import com.oracle.graal.compiler.common.spi.ConstantFieldProvider;
import com.oracle.graal.graph.Node;

public interface CanonicalizerTool {

    Assumptions getAssumptions();

    MetaAccessProvider getMetaAccess();

    ConstantReflectionProvider getConstantReflection();

    ConstantFieldProvider getConstantFieldProvider();

    boolean canonicalizeReads();

    /**
     * If this method returns false, not all {@link Node#usages() usages of a node} are yet
     * available. So a node must not be canonicalized base on, e.g., information returned from
     * {@link Node#hasNoUsages()}.
     */
    boolean allUsagesAvailable();

    /**
     * Indicates whether the target platform supports comparison of integers of a particular bit
     * width. This check is used by canonicalizations that might introduce subword compares.
     */
    boolean supportSubwordCompare(int bits);
}
