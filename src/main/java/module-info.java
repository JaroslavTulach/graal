module org.apidesign.demo.syslog {
    provides System.LoggerFinder with org.apidesign.demo.syslog.DemoLogger.Finder;
}
