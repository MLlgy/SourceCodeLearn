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
package okhttp3.internal.connection;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.Socket;

import okhttp3.Address;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import okhttp3.Route;
import okhttp3.internal.Internal;
import okhttp3.internal.Util;
import okhttp3.internal.http.HttpCodec;
import okhttp3.internal.http2.ConnectionShutdownException;
import okhttp3.internal.http2.ErrorCode;
import okhttp3.internal.http2.StreamResetException;

import static okhttp3.internal.Util.closeQuietly;

/**
 * This class coordinates the relationship between three entities:
 * （这个类协调三个实体之间的关系） Connections  Streams   Calls
 * <p>
 * <ul>
 * <li><strong>Connections:</strong> physical socket connections to remote servers. These are
 * potentially slow to establish so it is necessary to be able to cancel a connection
 * currently being connected.
 * （与远程服务器的物理套接字连接。这些连接建立起来可能很慢，因此必须能够取消当前正在连接的连接。）
 * <li><strong>Streams:</strong> logical HTTP request/response pairs that are layered on
 * connections. Each connection has its own allocation limit, which defines how many
 * concurrent streams that connection can carry. HTTP/1.x connections can carry 1 stream
 * at a time, HTTP/2 typically carry multiple.
 * （在*连接上分层的逻辑HTTP请求/响应对。
 * 每个连接都有自己的分配限制，它定义了连接可以承载多少并发流。
 * HTTP/1.x 连接一次可以携带1个流*，HTTP/2通常携带多个流*。）
 * <li><strong>Calls:</strong> a logical sequence of streams, typically an initial request and
 * its follow up requests. We prefer to keep all streams of a single call on the same
 * connection for better behavior and locality.
 * （流的逻辑序列，通常是初始请求和它的后续请求。我们更喜欢将单个调用的所有流保存在同一个连接上，
 * 以获得更好的行为和位置）
 * </ul>
 * <p>
 * <p>Instances of this class act on behalf of the call, using one or more streams over one or more
 * connections. This class has APIs to release each of the above resources:
 * <p>
 * <ul>
 * <li>{@link #noNewStreams()} prevents the connection from being used for new streams in the
 * future. Use this after a {@code Connection: close} header, or when the connection may be
 * inconsistent.
 * <li>{@link #streamFinished streamFinished()} releases the active stream from this allocation.
 * Note that only one stream may be active at a given time, so it is necessary to call
 * {@link #streamFinished streamFinished()} before creating a subsequent stream with {@link
 * #newStream newStream()}.
 * <li>{@link #release()} removes the call's hold on the connection. Note that this won't
 * immediately free the connection if there is a stream still lingering. That happens when a
 * call is complete but its response body has yet to be fully consumed.
 * </ul>
 * <p>
 * <p>This class supports {@linkplain #cancel asynchronous canceling}. This is intended to have the
 * smallest blast radius possible. If an HTTP/2 stream is active, canceling will cancel that stream
 * but not the other streams sharing its connection. But if the TLS handshake is still in progress
 * then canceling may break the entire connection.
 */
public final class StreamAllocation {
    public final Address address;
    private final ConnectionPool connectionPool;
    private final Object callStackTrace;
    // State guarded by connectionPool.
    private final RouteSelector routeSelector;
    private Route route;
    private int refusedStreamCount;
    private RealConnection connection;
    private boolean released;
    private boolean canceled;
    private HttpCodec codec;

    public StreamAllocation(ConnectionPool connectionPool, Address address, Object callStackTrace) {
        this.connectionPool = connectionPool;
        this.address = address;
        this.routeSelector = new RouteSelector(address, routeDatabase());
        this.callStackTrace = callStackTrace;
    }

    public HttpCodec newStream(OkHttpClient client, boolean doExtensiveHealthChecks) {
        int connectTimeout = client.connectTimeoutMillis();
        int readTimeout = client.readTimeoutMillis();
        int writeTimeout = client.writeTimeoutMillis();
        boolean connectionRetryEnabled = client.retryOnConnectionFailure();

        try {
            // 获得 Connection，HealthyConnection：现在就可用的 Connectation
            RealConnection resultConnection = findHealthyConnection(connectTimeout, readTimeout,
                    writeTimeout, connectionRetryEnabled, doExtensiveHealthChecks);

            HttpCodec resultCodec = resultConnection.newCodec(client, this);

            synchronized (connectionPool) {
                codec = resultCodec;
                return resultCodec;
            }
        } catch (IOException e) {
            throw new RouteException(e);
        }
    }

    /**
     * Finds a connection and returns it if it is healthy. If it is unhealthy the process is repeated
     * until a healthy connection is found.
     *
     *
     *
     */
    private RealConnection findHealthyConnection(int connectTimeout, int readTimeout,
                                                 int writeTimeout, boolean connectionRetryEnabled, boolean doExtensiveHealthChecks)
            throws IOException {
        while (true) {
            RealConnection candidate = findConnection(connectTimeout, readTimeout, writeTimeout,
                    connectionRetryEnabled);

            // If this is a brand new connection, we can skip the extensive health checks.
            synchronized (connectionPool) {
                if (candidate.successCount == 0) {
                    return candidate;
                }
            }

            // Do a (potentially slow) check to confirm that the pooled connection is still good. If it
            // isn't, take it out of the pool and start again.
            if (!candidate.isHealthy(doExtensiveHealthChecks)) {
                noNewStreams();
                continue;
            }

            return candidate;
        }
    }

    /**
     * 获得连接的顺序：存在的链接 、 连接池、新建一个连接
     * Returns a connection to host a new stream. This prefers the existing connection if it exists,
     * then the pool, finally building a new connection.
     */
    private RealConnection findConnection(int connectTimeout, int readTimeout, int writeTimeout,
                                          boolean connectionRetryEnabled) throws IOException {
        Route selectedRoute;
        synchronized (connectionPool) {
            if (released) throw new IllegalStateException("released");
            if (codec != null) throw new IllegalStateException("codec != null");
            if (canceled) throw new IOException("Canceled");

            // Attempt to use an already-allocated connection.  尝试拿到已存在的 connection ，直接返回
            RealConnection allocatedConnection = this.connection;
            if (allocatedConnection != null && !allocatedConnection.noNewStreams) {
                return allocatedConnection;
            }

            // Attempt to get a connection from the pool. 从 connectionpool 拿到 connection 直接返回
            Internal.instance.get(connectionPool, address, this, null);
            if (connection != null) {
                return connection;
            }

            selectedRoute = route;
        }

        /**
         * 更换路由，在 ConnectionPool 中继续寻找 连接，寻找到之后直接返回
         */
        // If we need a route, make one. This is a blocking operation.
        if (selectedRoute == null) {
            selectedRoute = routeSelector.next();
        }

        RealConnection result;
        synchronized (connectionPool) {
            if (canceled) throw new IOException("Canceled");

            // Now that we have an IP address, make another attempt at getting a connection from the pool.
            // This could match due to connection coalescing.
            Internal.instance.get(connectionPool, address, this, selectedRoute);
            if (connection != null) {
                route = selectedRoute;
                return connection;
            }

            /**
             * 如果上面两部操作还是不能找到对应的 Connection ，那么就新建 Connection
             */
            // Create a connection and assign it to this allocation immediately. This makes it possible
            // for an asynchronous cancel() to interrupt the handshake we're about to do.
            route = selectedRoute;
            refusedStreamCount = 0;
            result = new RealConnection(connectionPool, selectedRoute);
            acquire(result);
        }

        // Do TCP + TLS handshakes. This is a blocking operation. 进行 TCP + Tls 连接，这是阻塞操作
        result.connect(connectTimeout, readTimeout, writeTimeout, connectionRetryEnabled);

        routeDatabase().connected(result.route());

        Socket socket = null;
        synchronized (connectionPool) {
            // Pool the connection.
            Internal.instance.put(connectionPool, result);

            // If another multiplexed connection to the same address was created concurrently, then
            // release this connection and acquire that one.
            if (result.isMultiplexed()) {
                socket = Internal.instance.deduplicate(connectionPool, address, this);
                result = connection;
            }
        }
        closeQuietly(socket);

        return result;
    }

    public void streamFinished(boolean noNewStreams, HttpCodec codec) {
        Socket socket;
        synchronized (connectionPool) {
            if (codec == null || codec != this.codec) {
                throw new IllegalStateException("expected " + this.codec + " but was " + codec);
            }
            if (!noNewStreams) {
                connection.successCount++;
            }
            socket = deallocate(noNewStreams, false, true);
        }
        closeQuietly(socket);
    }

    public HttpCodec codec() {
        synchronized (connectionPool) {
            return codec;
        }
    }

    private RouteDatabase routeDatabase() {
        return Internal.instance.routeDatabase(connectionPool);
    }

    public synchronized RealConnection connection() {
        return connection;
    }

    public void release() {
        Socket socket;
        synchronized (connectionPool) {
            socket = deallocate(false, true, false);
        }
        closeQuietly(socket);
    }

    /**
     * Forbid new streams from being created on the connection that hosts this allocation.
     */
    public void noNewStreams() {
        Socket socket;
        synchronized (connectionPool) {
            socket = deallocate(true, false, false);
        }
        closeQuietly(socket);
    }

    /**
     * Releases resources held by this allocation. If sufficient resources are allocated, the
     * connection will be detached or closed. Callers must be synchronized on the connection pool.
     * <p>
     * <p>Returns a closeable that the caller should pass to {@link Util#closeQuietly} upon completion
     * of the synchronized block. (We don't do I/O while synchronized on the connection pool.)接触分配
     */
    private Socket deallocate(boolean noNewStreams, boolean released, boolean streamFinished) {
        assert (Thread.holdsLock(connectionPool));

        if (streamFinished) {
            this.codec = null;
        }
        if (released) {
            this.released = true;
        }
        Socket socket = null;
        if (connection != null) {
            if (noNewStreams) {
                connection.noNewStreams = true;
            }
            if (this.codec == null && (this.released || connection.noNewStreams)) {
                release(connection);
                if (connection.allocations.isEmpty()) {
                    connection.idleAtNanos = System.nanoTime();
                    if (Internal.instance.connectionBecameIdle(connectionPool, connection)) {
                        socket = connection.socket();
                    }
                }
                connection = null;
            }
        }
        return socket;
    }

    public void cancel() {
        HttpCodec codecToCancel;
        RealConnection connectionToCancel;
        synchronized (connectionPool) {
            canceled = true;
            codecToCancel = codec;
            connectionToCancel = connection;
        }
        if (codecToCancel != null) {
            codecToCancel.cancel();
        } else if (connectionToCancel != null) {
            connectionToCancel.cancel();
        }
    }

    public void streamFailed(IOException e) {
        Socket socket;
        boolean noNewStreams = false;

        synchronized (connectionPool) {
            if (e instanceof StreamResetException) {
                StreamResetException streamResetException = (StreamResetException) e;
                if (streamResetException.errorCode == ErrorCode.REFUSED_STREAM) {
                    refusedStreamCount++;
                }
                // On HTTP/2 stream errors, retry REFUSED_STREAM errors once on the same connection. All
                // other errors must be retried on a new connection.
                if (streamResetException.errorCode != ErrorCode.REFUSED_STREAM || refusedStreamCount > 1) {
                    noNewStreams = true;
                    route = null;
                }
            } else if (connection != null
                    && (!connection.isMultiplexed() || e instanceof ConnectionShutdownException)) {
                noNewStreams = true;

                // If this route hasn't completed a call, avoid it for new connections.
                if (connection.successCount == 0) {
                    if (route != null && e != null) {
                        routeSelector.connectFailed(route, e);
                    }
                    route = null;
                }
            }
            socket = deallocate(noNewStreams, false, true);
        }

        closeQuietly(socket);
    }

    /**
     * Use this allocation to hold {@code connection}. Each call to this must be paired with a call to
     * {@link #release} on the same connection.
     *
     * 使用这个分配来保存connection。每一个调用都必须与同一个连接上的 release的调用相匹配。
     */
    public void acquire(RealConnection connection) {
        assert (Thread.holdsLock(connectionPool));
        if (this.connection != null) throw new IllegalStateException();

        this.connection = connection;
        connection.allocations.add(new StreamAllocationReference(this, callStackTrace));
    }

    /**
     * Remove this allocation from the connection's list of allocations.
     */
    private void release(RealConnection connection) {
        for (int i = 0, size = connection.allocations.size(); i < size; i++) {
            Reference<StreamAllocation> reference = connection.allocations.get(i);
            if (reference.get() == this) {
                connection.allocations.remove(i);
                return;
            }
        }
        throw new IllegalStateException();
    }

    /**
     * Release the connection held by this connection and acquire {@code newConnection} instead. It is
     * only safe to call this if the held connection is newly connected but duplicated by {@code
     * newConnection}. Typically this occurs when concurrently connecting to an HTTP/2 webserver.
     * <p>
     * <p>Returns a closeable that the caller should pass to {@link Util#closeQuietly} upon completion
     * of the synchronized block. (We don't do I/O while synchronized on the connection pool.)
     */
    public Socket releaseAndAcquire(RealConnection newConnection) {
        assert (Thread.holdsLock(connectionPool));
        if (codec != null || connection.allocations.size() != 1) throw new IllegalStateException();

        // Release the old connection.
        Reference<StreamAllocation> onlyAllocation = connection.allocations.get(0);
        Socket socket = deallocate(true, false, false);

        // Acquire the new connection.
        this.connection = newConnection;
        newConnection.allocations.add(onlyAllocation);

        return socket;
    }

    public boolean hasMoreRoutes() {
        return route != null || routeSelector.hasNext();
    }

    @Override
    public String toString() {
        RealConnection connection = connection();
        return connection != null ? connection.toString() : address.toString();
    }

    public static final class StreamAllocationReference extends WeakReference<StreamAllocation> {
        /**
         * Captures the stack trace at the time the Call is executed or enqueued. This is helpful for
         * identifying the origin of connection leaks.
         */
        public final Object callStackTrace;

        StreamAllocationReference(StreamAllocation referent, Object callStackTrace) {
            super(referent);
            this.callStackTrace = callStackTrace;
        }
    }
}
