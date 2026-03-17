module org.apidesign.demo.syslog {
    requires static org.graalvm.nativeimage;

    provides System.LoggerFinder with org.apidesign.demo.syslog.DemoLogger.Finder;

    opens org.apidesign.demo.syslog;
}
