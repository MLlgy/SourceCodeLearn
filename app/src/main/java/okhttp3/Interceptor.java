/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3;

import java.io.IOException;
import javax.annotation.Nullable;

/**
 * Observes, modifies, and potentially short-circuits requests going out and the corresponding
 * responses coming back in. Typically interceptors add, remove, or transform headers on the request
 * or response.
 * 观察、修改和潜在的短路请求，并返回相应的响应。通常，拦截器在请求或响应上添加、删除或转换头。
 *
 * 责任链模式：
 *
 * 一般调用顺序就是 拿到Request后 chain.request();进行操作，然后传递下去给下面的拦截器进行处理   chain.proceed(request),

 *
 */
public interface Interceptor {
  Response intercept(Chain chain) throws IOException;

  interface Chain {
    Request request();

    Response proceed(Request request) throws IOException;

    /**
     * Returns the connection the request will be executed on. This is only available in the chains
     * of network interceptors; for application interceptors this is always null.
     *
     * 返回请求将被执行的连接。这只适用于网络拦截器的链*;对于应用程序拦截器，这总是为空。
     */
    @Nullable Connection connection();
  }
}
