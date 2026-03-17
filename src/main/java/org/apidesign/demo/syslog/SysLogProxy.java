package org.apidesign.demo.syslog;

import java.util.ResourceBundle;

final class SysLogProxy implements System.Logger {
    private final String name;
    private System.Logger delegate;

    SysLogProxy(String name) {
        this.name = name;
    }

    private System.Logger delegate() {
        if (delegate == null) {
            delegate = System.getLogger(name);
        }
        return delegate;
    }

    @Override
    public String getName() {
        return delegate().getName();
    }

    @Override
    public boolean isLoggable(Level level) {
        return delegate().isLoggable(level);
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {
        delegate().log(level, bundle, msg, thrown);
    }

    @Override
    public void log(Level level, ResourceBundle bundle, String format, Object... params) {
        delegate().log(level, bundle, format, params);
    }
}
