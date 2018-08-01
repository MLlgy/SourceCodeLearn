package xutils3.http.body;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by wyouflf on 15/10/29.
 */
public interface RequestBody {

    long getContentLength();

    String getContentType();

    void setContentType(String contentType);

    void writeTo(OutputStream out) throws IOException;
}
