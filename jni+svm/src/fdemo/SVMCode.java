package fdemo;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CPointerTo;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

public class SVMCode {
    @CEntryPoint(name = "Java_fdemo_Fdemo_createIsolate", builtin=CEntryPoint.Builtin.CreateIsolate)
    public static native long createIsolate();

    @CEntryPoint(name = "Java_fdemo_Fdemo_add")
    static int add(JNIEnvironment env, JClass clazz, @CEntryPoint.IsolateContext long isolateId, int a, int b) {
        System.err.println("in add");
        System.err.println("in env: " + env.rawValue());
        JNINativeInterface fn = env.getFunctions();
        System.err.println("in fn: " + fn.rawValue());
        GetVersion version = fn.getGetVersion();
        System.err.println("in ver: " + version.rawValue());
        System.err.println("version: 0x" + Integer.toHexString(fn.getGetVersion().getVersion(env)));

        try (
            CTypeConversion.CCharPointerHolder className = CTypeConversion.toCString("fdemo/Fdemo");
            CTypeConversion.CCharPointerHolder name = CTypeConversion.toCString("hello");
            CTypeConversion.CCharPointerHolder sig = CTypeConversion.toCString("(ZCBSIJFD)V");
        ) {
            JClass fdemoClass = fn.getFindClass().find(env, className.get());
            System.err.println("classId: " + fdemoClass.rawValue());
            JMethodID helloId = fn.getGetStaticMethodID().find(env, fdemoClass, name.get(), sig.get());
            System.err.println("helloId: " + helloId.rawValue());

            JValue args = StackValue.get(8, SizeOf.get(JValue.class));
            args.addressOf(0).z(false);
            args.addressOf(1).c('A');
            args.addressOf(2).b((byte)22);
            args.addressOf(3).s((short)33);
            args.addressOf(4).i(39);
            args.addressOf(5).j(Long.MAX_VALUE / 2l);
            args.addressOf(6).f(3.14f);
            args.addressOf(7).d(Math.PI);
            fn.getCallStaticVoidMethodA().call(env, clazz, helloId, args);
            System.err.println("back in svm");
        } finally {
            fn.getExceptionClear().clear(env);
        }

        return a + b;
    }

    static class JNIHeaderDirectives implements CContext.Directives {

        @Override
        public List<String> getOptions() {
            File[] jnis = findJNIHeaders();
            return Arrays.asList(
                "-I" + jnis[0].getParent(),
                "-I" + jnis[1].getParent()
            );
        }

        @Override
        public List<String> getHeaderFiles() {
            File[] jnis = findJNIHeaders();
            return Arrays.asList(
                "<" + jnis[0] + ">",
                "<" + jnis[1] + ">"
            );
        }

        private static File[] findJNIHeaders() throws IllegalStateException {
            File[] jnis = new File[2];
            {
                File jreHome = new File(System.getProperty("java.home"));
                File jniH = new File(new File(jreHome.getParentFile(), "include"), "jni.h");
                if (!jniH.exists()) {
                    throw new IllegalStateException("jni.h doesn't exist: "+ jniH);
                }
                File jniMd = new File(new File(jniH.getParentFile(), "linux"), "jni_md.h");
                if (!jniMd.exists()) {
                    throw new IllegalStateException("jni_md.h doesn't exist: "+ jniMd);
                }
                jnis[0] = jniH;
                jnis[1] = jniMd;
            }
            return jnis;
        }
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JNIEnv_", addStructKeyword = true)
    interface JNIEnvironment extends PointerBase {
        @CField("functions")
        JNINativeInterface getFunctions();
    }

    @CPointerTo(JNIEnvironment.class)
    interface JNIEnvironmentPointer extends PointerBase {
        JNIEnvironment read();
        void write(JNIEnvironment value);
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct(value = "JNINativeInterface_", addStructKeyword = true)
    interface JNINativeInterface extends PointerBase {

        @CField
        WordPointer reserved0();

        @CField
        WordPointer reserved1();

        @CField
        WordPointer reserved2();

        @CField
        WordPointer reserved3();

        @CField
        GetVersion getGetVersion();

        @CField
        CFunctionPointer getDefineClass();

        @CField
        FindClass getFindClass();

        @CField
        CFunctionPointer getFromReflectedMethod();

        @CField
        CFunctionPointer getFromReflectedField();

        @CField
        CFunctionPointer getToReflectedMethod();

        @CField
        CFunctionPointer getGetSuperclass();

        @CField
        CFunctionPointer getIsAssignableFrom();

        @CField
        CFunctionPointer getToReflectedField();

        @CField
        CFunctionPointer getThrow();

        @CField
        CFunctionPointer getThrowNew();

        @CField
        CFunctionPointer getExceptionOccurred();

        @CField
        CFunctionPointer getExceptionDescribe();

        @CField
        ExceptionClear getExceptionClear();

        @CField
        CFunctionPointer getFatalError();

        @CField
        CFunctionPointer getPushLocalFrame();

        @CField
        CFunctionPointer getPopLocalFrame();

        @CField
        CFunctionPointer getNewGlobalRef();

        @CField
        CFunctionPointer getDeleteGlobalRef();

        @CField
        CFunctionPointer getDeleteLocalRef();

        @CField
        CFunctionPointer getIsSameObject();

        @CField
        CFunctionPointer getNewLocalRef();

        @CField
        CFunctionPointer getEnsureLocalCapacity();

        @CField
        CFunctionPointer getAllocObject();

        @CField
        CFunctionPointer getNewObject();

        @CField
        CFunctionPointer getNewObjectV();

        @CField
        CFunctionPointer getNewObjectA();

        @CField
        CFunctionPointer getGetObjectClass();

        @CField
        CFunctionPointer getIsInstanceOf();

        @CField
        GetMethodId getGetMethodID();

        @CField
        CFunctionPointer getCallObjectMethod();

        @CField
        CFunctionPointer getCallObjectMethodV();

        @CField
        CFunctionPointer getCallObjectMethodA();

        @CField
        CFunctionPointer getCallBooleanMethod();


        @CField
        CFunctionPointer getCallBooleanMethodV();


        @CField
        CFunctionPointer getCallBooleanMethodA();


        @CField
        CFunctionPointer getCallByteMethod();


        @CField
        CFunctionPointer getCallByteMethodV();


        @CField
        CFunctionPointer getCallByteMethodA();


        @CField
        CFunctionPointer getCallCharMethod();


        @CField
        CFunctionPointer getCallCharMethodV();


        @CField
        CFunctionPointer getCallCharMethodA();


        @CField
        CFunctionPointer getCallShortMethod();


        @CField
        CFunctionPointer getCallShortMethodV();


        @CField
        CFunctionPointer getCallShortMethodA();


        @CField
        CFunctionPointer getCallIntMethod();


        @CField
        CFunctionPointer getCallIntMethodV();


        @CField
        CFunctionPointer getCallIntMethodA();


        @CField
        CFunctionPointer getCallLongMethod();


        @CField
        CFunctionPointer getCallLongMethodV();


        @CField
        CFunctionPointer getCallLongMethodA();


        @CField
        CFunctionPointer getCallFloatMethod();


        @CField
        CFunctionPointer getCallFloatMethodV();


        @CField
        CFunctionPointer getCallFloatMethodA();


        @CField
        CFunctionPointer getCallDoubleMethod();


        @CField
        CFunctionPointer getCallDoubleMethodV();


        @CField
        CFunctionPointer getCallDoubleMethodA();


        @CField
        CFunctionPointer getCallVoidMethod();


        @CField
        CFunctionPointer getCallVoidMethodV();


        @CField
        CFunctionPointer getCallVoidMethodA();


        @CField
        CFunctionPointer getCallNonvirtualObjectMethod();


        @CField
        CFunctionPointer getCallNonvirtualObjectMethodV();


        @CField
        CFunctionPointer getCallNonvirtualObjectMethodA();


        @CField
        CFunctionPointer getCallNonvirtualBooleanMethod();


        @CField
        CFunctionPointer getCallNonvirtualBooleanMethodV();


        @CField
        CFunctionPointer getCallNonvirtualBooleanMethodA();


        @CField
        CFunctionPointer getCallNonvirtualByteMethod();


        @CField
        CFunctionPointer getCallNonvirtualByteMethodV();


        @CField
        CFunctionPointer getCallNonvirtualByteMethodA();


        @CField
        CFunctionPointer getCallNonvirtualCharMethod();


        @CField
        CFunctionPointer getCallNonvirtualCharMethodV();


        @CField
        CFunctionPointer getCallNonvirtualCharMethodA();


        @CField
        CFunctionPointer getCallNonvirtualShortMethod();


        @CField
        CFunctionPointer getCallNonvirtualShortMethodV();


        @CField
        CFunctionPointer getCallNonvirtualShortMethodA();


        @CField
        CFunctionPointer getCallNonvirtualIntMethod();


        @CField
        CFunctionPointer getCallNonvirtualIntMethodV();


        @CField
        CFunctionPointer getCallNonvirtualIntMethodA();


        @CField
        CFunctionPointer getCallNonvirtualLongMethod();


        @CField
        CFunctionPointer getCallNonvirtualLongMethodV();


        @CField
        CFunctionPointer getCallNonvirtualLongMethodA();


        @CField
        CFunctionPointer getCallNonvirtualFloatMethod();


        @CField
        CFunctionPointer getCallNonvirtualFloatMethodV();


        @CField
        CFunctionPointer getCallNonvirtualFloatMethodA();


        @CField
        CFunctionPointer getCallNonvirtualDoubleMethod();


        @CField
        CFunctionPointer getCallNonvirtualDoubleMethodV();


        @CField
        CFunctionPointer getCallNonvirtualDoubleMethodA();


        @CField
        CFunctionPointer getCallNonvirtualVoidMethod();


        @CField
        CFunctionPointer getCallNonvirtualVoidMethodV();


        @CField
        CFunctionPointer getCallNonvirtualVoidMethodA();


        @CField
        CFunctionPointer getGetFieldID();


        @CField
        CFunctionPointer getGetObjectField();


        @CField
        CFunctionPointer getGetBooleanField();


        @CField
        CFunctionPointer getGetByteField();


        @CField
        CFunctionPointer getGetCharField();


        @CField
        CFunctionPointer getGetShortField();


        @CField
        CFunctionPointer getGetIntField();


        @CField
        CFunctionPointer getGetLongField();


        @CField
        CFunctionPointer getGetFloatField();


        @CField
        CFunctionPointer getGetDoubleField();


        @CField
        CFunctionPointer getSetObjectField();


        @CField
        CFunctionPointer getSetBooleanField();


        @CField
        CFunctionPointer getSetByteField();


        @CField
        CFunctionPointer getSetCharField();


        @CField
        CFunctionPointer getSetShortField();


        @CField
        CFunctionPointer getSetIntField();


        @CField
        CFunctionPointer getSetLongField();


        @CField
        CFunctionPointer getSetFloatField();


        @CField
        CFunctionPointer getSetDoubleField();


        @CField
        GetMethodId getGetStaticMethodID();


        @CField
        CFunctionPointer getCallStaticObjectMethod();


        @CField
        CFunctionPointer getCallStaticObjectMethodV();


        @CField
        CFunctionPointer getCallStaticObjectMethodA();


        @CField
        CFunctionPointer getCallStaticBooleanMethod();


        @CField
        CFunctionPointer getCallStaticBooleanMethodV();


        @CField
        CFunctionPointer getCallStaticBooleanMethodA();


        @CField
        CFunctionPointer getCallStaticByteMethod();


        @CField
        CFunctionPointer getCallStaticByteMethodV();


        @CField
        CFunctionPointer getCallStaticByteMethodA();


        @CField
        CFunctionPointer getCallStaticCharMethod();


        @CField
        CFunctionPointer getCallStaticCharMethodV();


        @CField
        CFunctionPointer getCallStaticCharMethodA();


        @CField
        CFunctionPointer getCallStaticShortMethod();


        @CField
        CFunctionPointer getCallStaticShortMethodV();


        @CField
        CFunctionPointer getCallStaticShortMethodA();


        @CField
        CFunctionPointer getCallStaticIntMethod();


        @CField
        CFunctionPointer getCallStaticIntMethodV();


        @CField
        CFunctionPointer getCallStaticIntMethodA();


        @CField
        CFunctionPointer getCallStaticLongMethod();


        @CField
        CFunctionPointer getCallStaticLongMethodV();


        @CField
        CFunctionPointer getCallStaticLongMethodA();


        @CField
        CFunctionPointer getCallStaticFloatMethod();


        @CField
        CFunctionPointer getCallStaticFloatMethodV();


        @CField
        CFunctionPointer getCallStaticFloatMethodA();


        @CField
        CFunctionPointer getCallStaticDoubleMethod();


        @CField
        CFunctionPointer getCallStaticDoubleMethodV();


        @CField
        CFunctionPointer getCallStaticDoubleMethodA();


        @CField
        CallStaticVoidMethod getCallStaticVoidMethod();


        @CField
        CFunctionPointer getCallStaticVoidMethodV();


        @CField
        CallStaticVoidMethod getCallStaticVoidMethodA();


        @CField
        CFunctionPointer getGetStaticFieldID();


        @CField
        CFunctionPointer getGetStaticObjectField();


        @CField
        CFunctionPointer getGetStaticBooleanField();


        @CField
        CFunctionPointer getGetStaticByteField();


        @CField
        CFunctionPointer getGetStaticCharField();


        @CField
        CFunctionPointer getGetStaticShortField();


        @CField
        CFunctionPointer getGetStaticIntField();


        @CField
        CFunctionPointer getGetStaticLongField();


        @CField
        CFunctionPointer getGetStaticFloatField();


        @CField
        CFunctionPointer getGetStaticDoubleField();


        @CField
        CFunctionPointer getSetStaticObjectField();


        @CField
        CFunctionPointer getSetStaticBooleanField();


        @CField
        CFunctionPointer getSetStaticByteField();


        @CField
        CFunctionPointer getSetStaticCharField();


        @CField
        CFunctionPointer getSetStaticShortField();


        @CField
        CFunctionPointer getSetStaticIntField();


        @CField
        CFunctionPointer getSetStaticLongField();


        @CField
        CFunctionPointer getSetStaticFloatField();


        @CField
        CFunctionPointer getSetStaticDoubleField();


        @CField
        CFunctionPointer getNewString();


        @CField
        CFunctionPointer getGetStringLength();


        @CField
        CFunctionPointer getGetStringChars();


        @CField
        CFunctionPointer getReleaseStringChars();


        @CField
        CFunctionPointer getNewStringUTF();


        @CField
        CFunctionPointer getGetStringUTFLength();


        @CField
        CFunctionPointer getGetStringUTFChars();


        @CField
        CFunctionPointer getReleaseStringUTFChars();


        @CField
        CFunctionPointer getGetArrayLength();


        @CField
        CFunctionPointer getNewObjectArray();


        @CField
        CFunctionPointer getGetObjectArrayElement();


        @CField
        CFunctionPointer getSetObjectArrayElement();


        @CField
        CFunctionPointer getNewBooleanArray();


        @CField
        CFunctionPointer getNewByteArray();


        @CField
        CFunctionPointer getNewCharArray();


        @CField
        CFunctionPointer getNewShortArray();


        @CField
        CFunctionPointer getNewIntArray();


        @CField
        CFunctionPointer getNewLongArray();


        @CField
        CFunctionPointer getNewFloatArray();


        @CField
        CFunctionPointer getNewDoubleArray();


        @CField
        CFunctionPointer getGetBooleanArrayElements();


        @CField
        CFunctionPointer getGetByteArrayElements();


        @CField
        CFunctionPointer getGetCharArrayElements();


        @CField
        CFunctionPointer getGetShortArrayElements();


        @CField
        CFunctionPointer getGetIntArrayElements();


        @CField
        CFunctionPointer getGetLongArrayElements();


        @CField
        CFunctionPointer getGetFloatArrayElements();


        @CField
        CFunctionPointer getGetDoubleArrayElements();


        @CField
        CFunctionPointer getReleaseBooleanArrayElements();


        @CField
        CFunctionPointer getReleaseByteArrayElements();


        @CField
        CFunctionPointer getReleaseCharArrayElements();


        @CField
        CFunctionPointer getReleaseShortArrayElements();


        @CField
        CFunctionPointer getReleaseIntArrayElements();


        @CField
        CFunctionPointer getReleaseLongArrayElements();


        @CField
        CFunctionPointer getReleaseFloatArrayElements();


        @CField
        CFunctionPointer getReleaseDoubleArrayElements();


        @CField
        CFunctionPointer getGetBooleanArrayRegion();


        @CField
        CFunctionPointer getGetByteArrayRegion();


        @CField
        CFunctionPointer getGetCharArrayRegion();


        @CField
        CFunctionPointer getGetShortArrayRegion();


        @CField
        CFunctionPointer getGetIntArrayRegion();


        @CField
        CFunctionPointer getGetLongArrayRegion();


        @CField
        CFunctionPointer getGetFloatArrayRegion();


        @CField
        CFunctionPointer getGetDoubleArrayRegion();


        @CField
        CFunctionPointer getSetBooleanArrayRegion();


        @CField
        CFunctionPointer getSetByteArrayRegion();


        @CField
        CFunctionPointer getSetCharArrayRegion();


        @CField
        CFunctionPointer getSetShortArrayRegion();


        @CField
        CFunctionPointer getSetIntArrayRegion();


        @CField
        CFunctionPointer getSetLongArrayRegion();


        @CField
        CFunctionPointer getSetFloatArrayRegion();


        @CField
        CFunctionPointer getSetDoubleArrayRegion();


        @CField
        CFunctionPointer getRegisterNatives();


        @CField
        CFunctionPointer getUnregisterNatives();


        @CField
        CFunctionPointer getMonitorEnter();


        @CField
        CFunctionPointer getMonitorExit();


        @CField
        CFunctionPointer getGetJavaVM();


        @CField
        CFunctionPointer getGetStringRegion();


        @CField
        CFunctionPointer getGetStringUTFRegion();


        @CField
        CFunctionPointer getGetPrimitiveArrayCritical();


        @CField
        CFunctionPointer getReleasePrimitiveArrayCritical();


        @CField
        CFunctionPointer getGetStringCritical();


        @CField
        CFunctionPointer getReleaseStringCritical();


        @CField
        CFunctionPointer getNewWeakGlobalRef();


        @CField
        CFunctionPointer getDeleteWeakGlobalRef();


        @CField
        CFunctionPointer getExceptionCheck();


        @CField
        CFunctionPointer getNewDirectByteBuffer();


        @CField
        CFunctionPointer getGetDirectBufferAddress();


        @CField
        CFunctionPointer getGetDirectBufferCapacity();


        @CField
        CFunctionPointer getGetObjectRefType();

    }

    interface GetVersion extends CFunctionPointer {
        @InvokeCFunctionPointer
        int getVersion(JNIEnvironment env);
    }

    interface FindClass extends CFunctionPointer {
        @InvokeCFunctionPointer
        JClass find(JNIEnvironment env, CCharPointer jvmName);
    }

    interface ExceptionClear extends CFunctionPointer {
        @InvokeCFunctionPointer
        void clear(JNIEnvironment env);
    }

    interface GetMethodId extends CFunctionPointer {
        @InvokeCFunctionPointer
        JMethodID find(JNIEnvironment env, JClass clazz, CCharPointer name, CCharPointer sig);
    }

    interface JObject extends PointerBase {
    }

    @CContext(JNIHeaderDirectives.class)
    @CStruct("jvalue")
    interface JValue extends PointerBase {
        @CField
        boolean z();
        @CField
        byte b();
        @CField
        char c();
        @CField
        short s();
        @CField
        int i();
        @CField
        long j();
        @CField
        float f();
        @CField
        double d();
        @CField
        JObject l();


        @CField
        void z(boolean b);
        @CField
        void b(byte b);
        @CField
        void c(char ch);
        @CField
        void s(short s);
        @CField
        void i(int i);
        @CField
        void j(long l);
        @CField
        void f(float f);
        @CField
        void d(double d);
        @CField
        void l(JObject obj);

        JValue addressOf(int index);
    }

    interface CallStaticVoidMethod extends CFunctionPointer {
        @InvokeCFunctionPointer
        void call(JNIEnvironment env, JClass cls, JMethodID methodid, JValue args);
    }

    interface JClass extends PointerBase {
    }
    interface JMethodID extends PointerBase {
    }
}
