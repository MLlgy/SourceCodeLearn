/*
 * Copyright (C) 2013 Square, Inc.
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
package okhttp3.internal.cache;

import java.util.Date;

import javax.annotation.Nullable;

import okhttp3.CacheControl;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.Internal;
import okhttp3.internal.http.HttpDate;
import okhttp3.internal.http.HttpHeaders;
import okhttp3.internal.http.StatusLine;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_GONE;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_NOT_AUTHORITATIVE;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_NOT_IMPLEMENTED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_REQ_TOO_LONG;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * 缓存策略类
 * <p>
 * 它根据之前缓存的结果与当前将要发送Request的header进行策略，并得出是否进行请求的结果
 * <p>
 * Given a request and cached response, this figures out whether to use the network, the cache, or
 * both.
 * <p>
 * <p>Selecting a cache strategy may add conditions to the request (like the "If-Modified-Since"
 * header for conditional GETs) or warnings to the cached response (if the cached data is
 * potentially stale).
 *
 * 选择一个缓存策略可能向 request 添加一些额外的 Header，比如 If-Modified-Since 或者关于缓存响应的一些警告信息
 *
 */
public final class CacheStrategy {
    /**
     * The request to send on the network, or null if this call doesn't use the network.
     */
    public final @Nullable
    Request networkRequest;

    /**
     * 返回缓存的响应，如果此 request 不能使用缓存，那么返回为 null
     * The cached response to return or validate; or null if this call doesn't use a cache.
     */
    public final @Nullable
    Response cacheResponse;

    CacheStrategy(Request networkRequest, Response cacheResponse) {
        this.networkRequest = networkRequest;
        this.cacheResponse = cacheResponse;
    }

    /**
     * Returns true if {@code response} can be stored to later serve another request.
     */
    public static boolean isCacheable(Response response, Request request) {
        // Always go to network for uncacheable response codes (RFC 7231 section 6.1),
        // This implementation doesn't support caching partial content.
        switch (response.code()) {
            case HTTP_OK:
            case HTTP_NOT_AUTHORITATIVE:
            case HTTP_NO_CONTENT:
            case HTTP_MULT_CHOICE:
            case HTTP_MOVED_PERM:
            case HTTP_NOT_FOUND:
            case HTTP_BAD_METHOD:
            case HTTP_GONE:
            case HTTP_REQ_TOO_LONG:
            case HTTP_NOT_IMPLEMENTED:
            case StatusLine.HTTP_PERM_REDIRECT:
                // These codes can be cached unless headers forbid it.
                break;

            case HTTP_MOVED_TEMP:
            case StatusLine.HTTP_TEMP_REDIRECT:
                // These codes can only be cached with the right response headers.
                // http://tools.ietf.org/html/rfc7234#section-3
                // s-maxage is not checked because OkHttp is a private cache that should ignore s-maxage.
                if (response.header("Expires") != null
                        || response.cacheControl().maxAgeSeconds() != -1
                        || response.cacheControl().isPublic()
                        || response.cacheControl().isPrivate()) {
                    break;
                }
                // Fall-through.

            default:
                // All other codes cannot be cached.
                return false;
        }

        // A 'no-store' directive on request or response prevents the response from being cached.

        return !response.cacheControl().noStore() && !request.cacheControl().noStore();
    }

    public static class Factory {
        final long nowMillis;
        final Request request;
        final Response cacheResponse;

        /**
         * The server's time when the cached response was served, if known.
         */
        private Date servedDate;
        private String servedDateString;

        /**
         * The last modified date of the cached response, if known.
         */
        private Date lastModified;
        private String lastModifiedString;

        /**
         * The expiration date of the cached response, if known. If both this field and the max age are
         * set, the max age is preferred.
         */
        private Date expires;

        /**
         * Extension header set by OkHttp specifying the timestamp when the cached HTTP request was
         * first initiated.
         */
        private long sentRequestMillis;

        /**
         * Extension header set by OkHttp specifying the timestamp when the cached HTTP response was
         * first received.
         */
        private long receivedResponseMillis;

        /**
         * Etag of the cached response.
         */
        private String etag;

        /**
         * Age of the cached response.
         */
        private int ageSeconds = -1;

        /**
         *
         * @param nowMillis
         * @param request 发起的请求
         * @param cacheResponse  缓存的响应数据
         */
        public Factory(long nowMillis, Request request, Response cacheResponse) {
            this.nowMillis = nowMillis;
            this.request = request;
            this.cacheResponse = cacheResponse;

            if (cacheResponse != null) {
                this.sentRequestMillis = cacheResponse.sentRequestAtMillis();//获得请求的发起时间
                this.receivedResponseMillis = cacheResponse.receivedResponseAtMillis();// 获取响应的返回时间
                Headers headers = cacheResponse.headers();
                // 获取缓存响应的有关缓存的 Header
                for (int i = 0, size = headers.size(); i < size; i++) {
                    String fieldName = headers.name(i);
                    String value = headers.value(i);
                    if ("Date".equalsIgnoreCase(fieldName)) {
                        servedDate = HttpDate.parse(value);
                        servedDateString = value;
                    } else if ("Expires".equalsIgnoreCase(fieldName)) {
                        expires = HttpDate.parse(value);
                    } else if ("Last-Modified".equalsIgnoreCase(fieldName)) {
                        lastModified = HttpDate.parse(value);
                        lastModifiedString = value;
                    } else if ("ETag".equalsIgnoreCase(fieldName)) {
                        etag = value;
                    } else if ("Age".equalsIgnoreCase(fieldName)) {
                        ageSeconds = HttpHeaders.parseSeconds(value, -1);
                    }
                }
            }
        }

        /**
         * Returns true if the request contains conditions that save the server from sending a response
         * that the client has locally. When a request is enqueued with its own conditions, the built-in
         * response cache won't be used.
         */
        private static boolean hasConditions(Request request) {
            return request.header("If-Modified-Since") != null || request.header("If-None-Match") != null;
        }

        /**
         * Returns a strategy to satisfy {@code request} using the a cached response {@code response}. 返回一个返回一个战略，以满足请求使用缓存的响应
         */
        public CacheStrategy get() {
            CacheStrategy candidate = getCandidate();

            if (candidate.networkRequest != null && request.cacheControl().onlyIfCached()) {
                // We're forbidden from using the network and the cache is insufficient. 我们禁止使用网络，并且该缓存是不够的
                return new CacheStrategy(null, null);
            }

            return candidate;
        }

        /**
         * Returns a strategy to use assuming the request can use the network.
         * 返回一个缓存策略
         */
        private CacheStrategy getCandidate() {
            // No cached response.
            if (cacheResponse == null) {
                return new CacheStrategy(request, null);
            }

            // Drop the cached response if it's missing a required handshake.
            // 如果是 HTTPS 请求，但是缓存响应中没有 TLS 相关的数据，那么说明缓存的响应不可用
            if (request.isHttps() && cacheResponse.handshake() == null) {
                return new CacheStrategy(request, null);
            }

            // If this response shouldn't have been stored, it should never be used
            // as a response source. This check should be redundant as long as the
            // persistence store is well-behaved and the rules are constant.

            /**
             * 检测该 request 和缓存的响应是否被缓存
             */
            if (!isCacheable(cacheResponse, request)) {
                return new CacheStrategy(request, null);
            }

            CacheControl requestCaching = request.cacheControl();
            // 发起的 request 不能被缓存
            if (requestCaching.noCache() || hasConditions(request)) {
                return new CacheStrategy(request, null);
            }

            long ageMillis = cacheResponseAge();// age
            long freshMillis = computeFreshnessLifetime();

            if (requestCaching.maxAgeSeconds() != -1) {
                freshMillis = Math.min(freshMillis, SECONDS.toMillis(requestCaching.maxAgeSeconds()));
            }

            long minFreshMillis = 0;
            if (requestCaching.minFreshSeconds() != -1) {
                minFreshMillis = SECONDS.toMillis(requestCaching.minFreshSeconds());
            }

            long maxStaleMillis = 0;
            CacheControl responseCaching = cacheResponse.cacheControl();
            if (!responseCaching.mustRevalidate() && requestCaching.maxStaleSeconds() != -1) {
                maxStaleMillis = SECONDS.toMillis(requestCaching.maxStaleSeconds());
            }

            if (!responseCaching.noCache() && ageMillis + minFreshMillis < freshMillis + maxStaleMillis) {
                Response.Builder builder = cacheResponse.newBuilder();
                if (ageMillis + minFreshMillis >= freshMillis) {
                    builder.addHeader("Warning", "110 HttpURLConnection \"Response is stale\"");
                }
                long oneDayMillis = 24 * 60 * 60 * 1000L;
                if (ageMillis > oneDayMillis && isFreshnessLifetimeHeuristic()) {
                    builder.addHeader("Warning", "113 HttpURLConnection \"Heuristic expiration\"");
                }
                return new CacheStrategy(null, builder.build());
            }

            // Find a condition to add to the request. If the condition is satisfied, the response body
            // will not be transmitted. transmitted：传送 寻找一种添加 reques 的情况。如果这个请求满足要求，响应体不会被传送。
            String conditionName;
            String conditionValue;
            if (etag != null) {
                conditionName = "If-None-Match";
                conditionValue = etag;
            } else if (lastModified != null) {
                conditionName = "If-Modified-Since";
                conditionValue = lastModifiedString;
            } else if (servedDate != null) {
                conditionName = "If-Modified-Since";
                conditionValue = servedDateString;
            } else {
                return new CacheStrategy(request, null); // No condition! Make a regular request.
            }

            Headers.Builder conditionalRequestHeaders = request.headers().newBuilder();
            Internal.instance.addLenient(conditionalRequestHeaders, conditionName, conditionValue);

            Request conditionalRequest = request.newBuilder()
                    .headers(conditionalRequestHeaders.build())
                    .build();
            return new CacheStrategy(conditionalRequest, cacheResponse);
        }

        /**
         * Returns the number of milliseconds that the response was fresh for, starting from the served
         * date. 返回响应从服务器返回后的新鲜时间
         */
        private long computeFreshnessLifetime() {
            CacheControl responseCaching = cacheResponse.cacheControl();
            if (responseCaching.maxAgeSeconds() != -1) {
                return SECONDS.toMillis(responseCaching.maxAgeSeconds());
            } else if (expires != null) {
                long servedMillis = servedDate != null
                        ? servedDate.getTime()
                        : receivedResponseMillis;
                long delta = expires.getTime() - servedMillis;
                return delta > 0 ? delta : 0;
            } else if (lastModified != null
                    && cacheResponse.request().url().query() == null) {
                // As recommended by the HTTP RFC and implemented in Firefox, the
                // max age of a document should be defaulted to 10% of the
                // document's age at the time it was served. Default expiration
                // dates aren't used for URIs containing a query.
                long servedMillis = servedDate != null
                        ? servedDate.getTime()
                        : sentRequestMillis;
                long delta = servedMillis - lastModified.getTime();
                return delta > 0 ? (delta / 10) : 0;
            }
            return 0;
        }

        /**
         * Returns the current age of the response, in milliseconds. The calculation is specified by RFC
         * 2616, 13.2.3 Age Calculations.
         */
        private long cacheResponseAge() {
            long apparentReceivedAge = servedDate != null
                    ? Math.max(0, receivedResponseMillis - servedDate.getTime())
                    : 0;
            long receivedAge = ageSeconds != -1
                    ? Math.max(apparentReceivedAge, SECONDS.toMillis(ageSeconds))
                    : apparentReceivedAge;
            long responseDuration = receivedResponseMillis - sentRequestMillis;
            long residentDuration = nowMillis - receivedResponseMillis;
            return receivedAge + responseDuration + residentDuration;
        }

        /**
         * Returns true if computeFreshnessLifetime used a heuristic. If we used a heuristic to serve a
         * cached response older than 24 hours, we are required to attach a warning.
         */
        private boolean isFreshnessLifetimeHeuristic() {
            return cacheResponse.cacheControl().maxAgeSeconds() == -1 && expires == null;
        }
    }
}
