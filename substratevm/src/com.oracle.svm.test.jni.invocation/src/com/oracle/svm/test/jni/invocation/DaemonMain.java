/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test.jni.invocation;

import java.util.Timer;
import java.util.TimerTask;

/**
 * The Java side of {@code jnidestroyvm.c}: the {@code main} a launcher calls over JNI before it
 * calls {@code DestroyJavaVM}.
 * <p>
 * It leaves behind a daemon thread that swallows {@link InterruptedException}. Which one depends
 * on the argument: {@code timer} starts a {@link Timer}, whose thread waits on its queue, and
 * {@code daemon} starts a thread that sleeps. HotSpot's {@code DestroyJavaVM} does not wait for
 * daemon threads, so a launcher can exit. Native Image tears down the isolate, which interrupts
 * the thread and, when that does not end it, makes its blocking operation throw
 * {@link ThreadDeath}; {@code DestroyJavaVM} must not block on it either.
 * <p>
 * The cases are separate so that a fix for one of them can be verified on its own.
 */
public class DaemonMain {

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: timer or daemon");
        }
        switch (args[0]) {
            case "timer" -> startTimer();
            case "daemon" -> startDaemon();
            default -> throw new IllegalArgumentException("Unknown case: " + args[0]);
        }
        System.out.println("main returns: " + args[0]);
    }

    private static void startTimer() {
        Timer timer = new Timer("timer-daemon", true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
            }
        }, 100_000_000L);
    }

    private static void startDaemon() {
        Thread daemon = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(100_000);
                } catch (InterruptedException e) {
                    /* Ignore the interrupt, and keep running. */
                }
            }
        }, "interrupt-swallowing-daemon");
        daemon.setDaemon(true);
        daemon.start();
    }
}
