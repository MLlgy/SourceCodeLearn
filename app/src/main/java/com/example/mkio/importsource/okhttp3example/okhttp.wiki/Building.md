OkHttp requires Java 7 to build and run tests. Runtime compatibility with Java 6 is enforced as part of the build to ensure compliance with Android and older versions of the JVM.

### Desktop Testing with Maven

Run OkHttp tests on the desktop with Maven. Running HTTP/2 and SPDY tests on the desktop uses [Jetty-ALPN][1], which adds ALPN support to JDK 7 and JDK 8.

```
mvn clean test
```

### Desktop Testing without Maven

If you're working in an IDE, or in another environment where Maven configuration isn't honored, you'll need to manually enable ALPN. Add this JVM flag for OpenJDK 8:
```
-Xbootclasspath/p:/Users/jwilson/.m2/repository/org/mortbay/jetty/alpn/alpn-boot/8.1.2.v20141202/alpn-boot-8.1.2.v20141202.jar
```

Add this JVM flag for OpenJDK 7:
```
-Xbootclasspath/p:/Users/jwilson/.m2/repository/org/mortbay/jetty/alpn/alpn-boot/7.1.2.v20141202/alpn-boot-7.1.2.v20141202.jar
```

You must substitute `/Users/jwilson/.m2/repository` with the path to your Maven repository!

### Device Testing

OkHttp's test suite creates an in-process HTTPS server. Prior to Android 2.3, SSL server sockets were broken, and so HTTPS tests will time out when run on such devices.

Test on a USB-attached Android using [Vogar][2]. Unfortunately `dx` requires that you build with Java 6, otherwise the test class will be silently omitted from the `.dex` file.

```
mvn clean
mvn package -DskipTests
vogar \
    --classpath ~/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.48/bcprov-jdk15on-1.48.jar \
    --classpath ~/.m2/repository/com/squareup/okio/okio/1.6.0/okio-1.6.0.jar \
    --classpath okhttp/target/okhttp-2.0.0-SNAPSHOT.jar \
    ./samples/guide/src/main/java/okhttp3/recipes/SynchronousGet.java
```

 [1]: https://github.com/jetty-project/jetty-alpn
 [2]: https://code.google.com/p/vogar/
