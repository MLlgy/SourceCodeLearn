#### How do I fix verify warnings in dalvikvm?

OkHttp supports some APIs that require Java 7+ or Android API 20+. If you run OkHttp on earlier Android releases, dalvikvm's verifier will warn about the missing methods. This isn't a problem and you can ignore the warnings.

```
I/dalvikvm﹕ Could not find method okhttp3.internal.huc.HttpURLConnectionImpl.getContentLengthLong, referenced from method okhttp3.internal.huc.HttpsURLConnectionImpl.getContentLengthLong
W/dalvikvm﹕ VFY: unable to resolve virtual method 21498: Lokhttp3/internal/huc/HttpURLConnectionImpl;.getContentLengthLong ()J
D/dalvikvm﹕ VFY: replacing opcode 0x6e at 0x0002
I/dalvikvm﹕ Could not find method okhttp3.internal.huc.HttpURLConnectionImpl.getHeaderFieldLong, referenced from method okhttp3.internal.huc.HttpsURLConnectionImpl.getHeaderFieldLong
W/dalvikvm﹕ VFY: unable to resolve virtual method 21503: Lokhttp3/internal/huc/HttpURLConnectionImpl;.getHeaderFieldLong (Ljava/lang/String;J)J
D/dalvikvm﹕ VFY: replacing opcode 0x6e at 0x0002
W/dalvikvm﹕ VFY: unable to find class referenced in signature (Ljava/nio/file/Path;)
W/dalvikvm﹕ VFY: unable to find class referenced in signature ([Ljava/nio/file/OpenOption;)
I/dalvikvm﹕ Could not find method java.nio.file.Files.newOutputStream, referenced from method okio.Okio.sink
W/dalvikvm﹕ VFY: unable to resolve static method 24080: Ljava/nio/file/Files;.newOutputStream (Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/OutputStream;
D/dalvikvm﹕ VFY: replacing opcode 0x71 at 0x000a
W/dalvikvm﹕ VFY: unable to find class referenced in signature (Ljava/nio/file/Path;)
W/dalvikvm﹕ VFY: unable to find class referenced in signature ([Ljava/nio/file/OpenOption;)
I/dalvikvm﹕ Could not find method java.nio.file.Files.newInputStream, referenced from method okio.Okio.source
W/dalvikvm﹕ VFY: unable to resolve static method 24079: Ljava/nio/file/Files;.newInputStream (Ljava/nio/file/Path;[Ljava/nio/file/OpenOption;)Ljava/io/InputStream;
D/dalvikvm﹕ VFY: replacing opcode 0x71 at 0x000a
```
