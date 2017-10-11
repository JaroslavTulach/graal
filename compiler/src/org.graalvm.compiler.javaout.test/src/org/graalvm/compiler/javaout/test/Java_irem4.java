package org.graalvm.compiler.javaout.test;

import org.junit.Test;

public class Java_irem4 extends JavaOutputTest {
    public static int test(int a) {
        return a % 8;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test", 13);
    }

}
