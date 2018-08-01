/*
 * Copyright (C) 2015 Square, Inc.
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
package retrofit2;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import javax.annotation.Nullable;

/**
 * 转换数据类型
 * Adapts a {@link Call} with response type {@code R} into the type of {@code T}. Instances are
 * created by {@linkplain Factory a factory} which is
 * {@linkplain Retrofit.Builder#addCallAdapterFactory(Factory) installed} into the {@link Retrofit}
 * instance.
 */
public interface CallAdapter<R, T> {
    /**
     * Returns the value type that this adapter uses when converting the HTTP response body to a Java
     * object. For example, the response type for {@code Call<Repo>} is {@code Repo}. This type
     * is used to prepare the {@code call} passed to {@code #adapt}.
     * <p>
     * Note: This is typically not the same type as the {@code returnType} provided to this call
     * adapter's factory.
     */

    // 直正数据的类型 如Call<T> 中的 T
    // 这个 T 会作为Converter.Factory.responseBodyConverter 的第一个参数
    // 可以参照上面的自定义Converter

    Type responseType();

    /**
     * Returns an instance of {@code T} which delegates to {@code call}.
     * <p>
     * For example, given an instance for a hypothetical utility, {@code Async}, this instance would
     * return a new {@code Async<R>} which invoked {@code call} when run.
     * <pre><code>
     * &#64;Override
     * public &lt;R&gt; Async&lt;R&gt; adapt(final Call&lt;R&gt; call) {
     *   return Async.create(new Callable&lt;Response&lt;R&gt;&gt;() {
     *     &#64;Override
     *     public Response&lt;R&gt; call() throws Exception {
     *       return call.execute();
     *     }
     *   });
     * }
     * </code></pre>
     */
    T adapt(Call<R> call);

    /**
     * Creates {@link CallAdapter} instances based on the return type of {@linkplain
     * Retrofit#create(Class) the service interface} methods.
     * <p>
     * 根据{@linkplain * Retrofit#create(类)方法的返回类型创建{@link CallAdapter}实例。
     * <p>
     * // 用于向Retrofit提供CallAdapter的工厂类
     */
    abstract class Factory {
        /**
         * Extract the upper bound of the generic parameter at {@code index} from {@code type}. For
         * example, index 1 of {@code Map<String, ? extends Runnable>} returns {@code Runnable}.
         */
        // 用于获取泛型的参数 如 Call<Requestbody> 中 Requestbody
        protected static Type getParameterUpperBound(int index, ParameterizedType type) {
            return Utils.getParameterUpperBound(index, type);
        }

        /**
         * Extract the raw class type from {@code type}. For example, the type representing
         * {@code List<? extends Runnable>} returns {@code List.class}.
         */
        // 用于获取泛型的原始类型 如 Call<Requestbody> 中的 Call
        // 上面的get方法需要使用该方法。
        protected static Class<?> getRawType(Type type) {
            return Utils.getRawType(type);
        }

        /**
         * Returns a call adapter for interface methods that return {@code returnType}, or null if it
         * cannot be handled by this factory.
         */

        // 在这个方法中判断是否是我们支持的类型，returnType 即Call<Requestbody>和`Observable<Requestbody>`
        // RxJavaCallAdapterFactory 就是判断returnType是不是Observable<?> 类型
        // 不支持时返回null
        public abstract @Nullable
        CallAdapter<?, ?> get(Type returnType, Annotation[] annotations,
                              Retrofit retrofit);
    }
}
