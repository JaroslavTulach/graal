package org.apidesign.demo.syslog;

public final class SysLog {
    private static final System.Logger LOG = System.getLogger(SysLog.class.getName());
    
    public static void main(String[] args) {
        var jvm = System.getProperty("java.vm.name");
        LOG.log(System.Logger.Level.INFO, "Hello via {0} in {1}", LOG, jvm);
    }
}
