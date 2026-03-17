package org.apidesign.demo.syslog;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.TargetClass;

public final class SysLogSubstitutions {
    @TargetClass(System.LoggerFinder.class)
    public static final class LoggerFinder_Target {
        @Alias
        @RecomputeFieldValue(kind=RecomputeFieldValue.Kind.Reset)
        public static LoggerFinder_Target service;
    }
    @TargetClass(className = "jdk.internal.logger.LazyLoggers")
    public static final class LazyLoggers_Target {
        @Alias
        @RecomputeFieldValue(kind=RecomputeFieldValue.Kind.Reset)
        public static LoggerFinder_Target provider;
    }
}
