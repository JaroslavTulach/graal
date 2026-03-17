package org.apidesign.demo.syslog;

import java.util.Arrays;
import java.util.ResourceBundle;

public class DemoLogger implements System.Logger {

    @Override
    public String getName() {
        return "Demo logger";
    }

    @Override
    public boolean isLoggable(Level level) {
        return true;
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        thrown.printStackTrace();
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        System.err.println(format + " " + Arrays.toString(params));
    }

    public static final class Finder extends System.LoggerFinder {
        @Override
        public System.Logger getLogger(String name, Module module) {
            return new DemoLogger();
        }
    }
}
