package org.apidesign.demo.syslog;

import org.graalvm.nativeimage.hosted.Feature;

public final class SysLogFeature implements Feature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer((obj) -> {
            if (obj instanceof System.Logger logger && !(obj instanceof SysLogProxy)) {
                return new SysLogProxy(logger.getName());
            }
            return obj;
        });
    }

}
