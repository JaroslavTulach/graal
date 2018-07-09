package fdemo;

import java.io.File;
import java.net.URISyntaxException;

public class Fdemo {
    public static void hello(boolean z, char c, byte b, short s, int i, long j, float f, double d) {
        System.err.println("Hi, I have just been called back!");
        System.err.println("With: " + z + " " + c + " " + b + " " + s);
        System.err.println(" and: " + i + " " + j + " " + f + " " + d);
    }

    public static void main(String[] args) {
        System.err.println("prepare isolate");
        long isolateId = createIsolate();
        System.err.println("created isolate: " + isolateId);

        System.err.println("2 + 40 = " + add(isolateId, 2, 40));
        System.err.println("12 + 30 = " + add(isolateId, 12, 30));
        System.err.println("20 + 22 = " + add(isolateId, 20, 22));
    }

    private static native int add(long isolate, int a, int b);
    private static native long createIsolate();

    static {
        try {
            System.err.println("initializing");
            File path = new File(Fdemo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            while (path != null) {
                File lib = new File(path, "fdemosvm.so");
                if (lib.exists()) {
                    System.load(lib.getAbsolutePath());
                    System.err.println("dynamic library loaded");
                    break;
                }
                path = path.getParentFile();
            }
            System.err.println("searched to " + path);
        } catch (URISyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }

}
