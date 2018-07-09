# JNI via Substrate VM

Substrate VM can be used to implement low-level system operations and
make them available via [JNI](JNI.md) to regular Java HotSpot JVM. As
a result one can use the same language to write the application logic
as well as the system calls.

## Create a Shared Library

First of all one has to use the `native-image` command to generate shared library
with few [entry points](README.md#images-and-entry-points). Let's start with
the [Java code](https://github.com/JaroslavTulach/graal/commit/70322b57f64b5b965d8dd4643bff0959c951a502#diff-8e38292a2eabb24029f220db903feb1bR799):
```java
package org.pkg.implnative;

import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.word.Pointer;

public final class NativeImpl {
    @CEntryPoint(name = "Java_org_pkg_apinative_Native_add")
    public static int add(Pointer jvm, Pointer clazz, @CEntryPoint.IsolateContext long isolateId, int a, int b) {
        return a + b;
    }
}
```
After being processed by the `native-image` command the code
[exposes a C function](C-API.md) `Java_org_pkg_apinative_Native_add`
(the name follows conventions of [JNI](JNI.md) that will be handful later) and
SubstrateVM signature typical for [JNI](JNI.md) methods. The first parameter
is a reference to JVM `Env*` environment, the second parameter is a reference
to the JVM class object that contains the method. The third parameter is a
pointer to [SubstrateVM C structure](C-API.md) holding an initialized isolate.
The rest of the parameters are the actual parameters of the Java `Native.add`
method described in the next section.
Compile the code with
[shared option on](https://github.com/JaroslavTulach/graal/commit/70322b57f64b5b965d8dd4643bff0959c951a502#diff-8e38292a2eabb24029f220db903feb1bR824):
```bash
$GRAALVM/bin/native-image --shared -H:Name=libnativeimpl -cp nativeimpl
```
and the `libnativeimpl.so` is generated. We are ready to use it via [JNI](JNI.md).

## Bind Java Native Method

Now we need another [Java class](https://github.com/JaroslavTulach/graal/commit/70322b57f64b5b965d8dd4643bff0959c951a502#diff-8e38292a2eabb24029f220db903feb1bR837)
to use the native library generated in the previous step:
```java
package org.pkg.apinative;

public final class Native {
    private static native int add(@CEntryPoint.IsolateContext long isolateId, int a, int b);
}
```
the package name of the class as well as name of the method has to correspond
(after the [JNI mangling](https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html))
to the name of the `@CEntryPoint` introduced previously. The first argument shall
be a pointer (e.g. `long`) to the SubstrateVM isolate. The rest of the arguments
shall match the parameters of the entry point.

## Loading the Native Library

The next step is to bind the JDK with the generated `.so` library - e.g.
make sure the implementation of the native `Native.add` method is loaded.
Simple [load or loadLibrary](https://github.com/JaroslavTulach/graal/commit/70322b57f64b5b965d8dd4643bff0959c951a502#diff-8e38292a2eabb24029f220db903feb1bR837)
will do:
```java
public static void main(String[] args) {
    System.loadLibrary("nativeimpl");
    // ...
}
```
under the assumption your `LD_LIBRARY_PATH` environment variable
or `java.library.path` Java property are
[properly set](https://github.com/JaroslavTulach/graal/commit/70322b57f64b5b965d8dd4643bff0959c951a502#diff-8e38292a2eabb24029f220db903feb1bR865) up.

## Initializing the Substrate VM

Before making the call to `Native.add` method, we need to create a Substrate VM
isolate. Substrate VM provides special hook to allow that: define method
named `createIsolate` which takes no parameters and returns `long`. Place
the method along your other `@CEntryPoint` methods:
```java
public final class NativeImpl {
    @CEntryPoint(name = "Java_org_pkg_apinative_Native_createIsolate")
    public static native long createIsolate();
}
```
SubstrateVM then generates default implementation of the method into the final `.so` library.
The method returns a [C API pointer](C-API.md) - e.g. `long` to hold
an instance of Substrate VM isolate. Such isolate can then be [used for
multiple invocations](https://github.com/JaroslavTulach/graal/commit/70322b57f64b5b965d8dd4643bff0959c951a502#diff-8e38292a2eabb24029f220db903feb1bR841)
of our **JNI** code:
```java
package org.pkg.apinative;

public final class Native {
    public static void main(String[] args) {
        System.loadLibrary("nativeimpl");

        long isolate = createIsolate();

        System.out.println("2 + 40 = " + add(isolate, 2, 40));
        System.out.println("12 + 30 = " + add(isolate, 12, 30));
        System.out.println("20 + 22 = " + add(isolate, 20, 22));
    }

    private static native int add(long isolate, int a, int b);
    private static native long createIsolate();```
```
The classical JVM is started. It initializes a Substrate VM isolate and
the universal answer `42` is then computed three times inside of
the isolate.
