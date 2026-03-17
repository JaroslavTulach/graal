## Demonstrates problems with `System.Logger`

Run the application in HotSpot mode and see what it prints:
```
JAVA_HOME=/graalvm-community-openjdk-25.0.1+8.1/ mvn -q -Pmodulepath package exec:exec
Hello via {0} in {1} [org.apidesign.demo.syslog.DemoLogger@33c7353a, OpenJDK 64-Bit Server VM]
```

Run the same application in GraalVM _native image_ and it behaves very differently without 
additonal work. With substituations and features it handles even `--initialize-at-build-time`:
```
JAVA_HOME=/graalvm-community-openjdk-25.0.1+8.1/ mvn -q -Pnative package exec:exec@syslog
Hello via {0} in {1} [org.apidesign.demo.syslog.SysLogProxy@5792f4a3, Substrate VM]
```
The necessary tricks there involve:
- [substituations](https://github.com/JaroslavTulach/graal/blob/SysLogDemo/src/main/java/org/apidesign/demo/syslog/SysLogSubstitutions.java) to reset `LoggerFinder` fields in the JDK
- [feature](https://github.com/JaroslavTulach/graal/blob/01c8470ad4ec0bb5baa5aad819981a0821e7630c/src/main/java/org/apidesign/demo/syslog/SysLogFeature.java#L10) to replace all already created `System.Logger` instances with a proxy

With these changes `System.Logger` seems to work OK in _native image_.
