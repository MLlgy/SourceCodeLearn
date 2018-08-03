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
package okhttp3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import okhttp3.RealCall.AsyncCall;
import okhttp3.internal.Util;

/**
 * Policy on when async requests are executed.
 * <p>
 * <p>Each dispatcher uses an {@link ExecutorService} to run calls internally. If you supply your
 * own executor, it should be able to run {@linkplain #getMaxRequests the configured maximum} number
 * of calls concurrently.
 * Dispatcher 任务调度器，它定义了三个双向任务队列，
 * 两个异步队列：准备执行的请求队列 readyAsyncCalls 、正在运行的请求队列 runningAsyncCalls ；
 * 一个正在运行的同步请求队列 runningSyncCalls ；
 */
public final class Dispatcher {

    /**
     * 将要运行的异步请求队列
     */
    private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();
    /**
     * 正在运行的异步请求队列
     */
    private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();
    /**
     * 正在运行的同步请求队列
     */
    private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();

    /** Executes calls. Created lazily. */
    /**
     * 最大并发请求数
     */
    private int maxRequests = 64;

    /** Ready async calls in the order they'll be run. */
    /**
     * 每个主机最大请求数
     */
    private int maxRequestsPerHost = 5;

    /**
     * Running asynchronous calls. Includes canceled calls that haven't finished yet.
     */
    private @Nullable
    Runnable idleCallback;

    /** Running synchronous calls. Includes canceled calls that haven't finished yet. */
    /**
     * 消费者线程池 ,任务队列
     */
    private @Nullable
    ExecutorService executorService;

    public Dispatcher(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public Dispatcher() {
    }

    public synchronized ExecutorService executorService() {
        if (executorService == null) {
            executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                    new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
        }
        return executorService;
    }

    public synchronized int getMaxRequests() {
        return maxRequests;
    }

    /**
     * Set the maximum number of requests to execute concurrently. Above this requests queue in
     * memory, waiting for the running calls to complete.
     * <p>
     * <p>If more than {@code maxRequests} requests are in flight when this is invoked, those requests
     * will remain in flight.
     */
    public synchronized void setMaxRequests(int maxRequests) {
        if (maxRequests < 1) {
            throw new IllegalArgumentException("max < 1: " + maxRequests);
        }
        this.maxRequests = maxRequests;
        promoteCalls();
    }

    public synchronized int getMaxRequestsPerHost() {
        return maxRequestsPerHost;
    }

    /**
     * Set the maximum number of requests for each host to execute concurrently. This limits requests
     * by the URL's host name. Note that concurrent requests to a single IP address may still exceed
     * this limit: multiple hostnames may share an IP address or be routed through the same HTTP
     * proxy.
     * <p>
     * <p>If more than {@code maxRequestsPerHost} requests are in flight when this is invoked, those
     * requests will remain in flight.
     */
    public synchronized void setMaxRequestsPerHost(int maxRequestsPerHost) {
        if (maxRequestsPerHost < 1) {
            throw new IllegalArgumentException("max < 1: " + maxRequestsPerHost);
        }
        this.maxRequestsPerHost = maxRequestsPerHost;
        promoteCalls();
    }

    /**
     * Set a callback to be invoked each time the dispatcher becomes idle (when the number of running
     * calls returns to zero).
     * <p>
     * <p>Note: The time at which a {@linkplain Call call} is considered idle is different depending
     * on whether it was run {@linkplain Call#enqueue(Callback) asynchronously} or
     * {@linkplain Call#execute() synchronously}. Asynchronous calls become idle after the
     * {@link Callback#onResponse onResponse} or {@link Callback#onFailure onFailure} callback has
     * returned. Synchronous calls become idle once {@link Call#execute() execute()} returns. This
     * means that if you are doing synchronous calls the network layer will not truly be idle until
     * every returned {@link Response} has been closed.
     */
    public synchronized void setIdleCallback(@Nullable Runnable idleCallback) {
        this.idleCallback = idleCallback;
    }

    synchronized void enqueue(AsyncCall call) {
        /**
         * 正在运行的异步请求队列的大小 < 最大并发请求数  同一个host发起的请求数 < 并且每个主机最大请求数
         */
        if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
            runningAsyncCalls.add(call);
            executorService().execute(call);
        } else {
            readyAsyncCalls.add(call);
        }
    }

    /**
     * Cancel all calls currently enqueued or executing. Includes calls executed both {@linkplain
     * Call#execute() synchronously} and {@linkplain Call#enqueue asynchronously}.
     */
    public synchronized void cancelAll() {
        for (AsyncCall call : readyAsyncCalls) {
            call.get().cancel();
        }

        for (AsyncCall call : runningAsyncCalls) {
            call.get().cancel();
        }

        for (RealCall call : runningSyncCalls) {
            call.cancel();
        }
    }

    private void promoteCalls() {
        if (runningAsyncCalls.size() >= maxRequests) return; // Already running max capacity.
        if (readyAsyncCalls.isEmpty()) return; // No ready calls to promote.

        /**
         * 从将要执行的队列中删除这个call，并把它加入到正在执行的队列中
         */
        for (Iterator<AsyncCall> i = readyAsyncCalls.iterator(); i.hasNext(); ) {
            AsyncCall call = i.next();

            /**
             * 在遍历过程中如果runningAsyncCalls超过maxRequest则不再添加，否则一直添加
             * 也就是说最多一次可以有5个并发线程
             */
            if (runningCallsForHost(call) < maxRequestsPerHost) {
                i.remove();
                runningAsyncCalls.add(call);
                executorService().execute(call);
            }

            if (runningAsyncCalls.size() >= maxRequests) return; // Reached max capacity.
        }
    }

    /**
     * Returns the number of running calls that share a host with {@code call}.
     * 返回同一个host 的 call 的数量
     */
    private int runningCallsForHost(AsyncCall call) {
        int result = 0;
        for (AsyncCall c : runningAsyncCalls) {
            if (c.host().equals(call.host())) result++;
        }
        return result;
    }

    /**
     * Used by {@code Call#execute} to signal it is in-flight.
     */
    synchronized void executed(RealCall call) {
        runningSyncCalls.add(call);
    }

    /**
     * Used by {@code AsyncCall#run} to signal completion.
     */
    void finished(AsyncCall call) {
        finished(runningAsyncCalls, call, true);
    }

    /**
     * Used by {@code Call#execute} to signal completion.
     */
    void finished(RealCall call) {
        finished(runningSyncCalls, call, false);
    }

    /**
     * 从ready到running,在每个call结束的时候都会调用finished  注意是每个call都会执行该操作。 promoteCalls() 继续执行下面的call
     * @param calls
     * @param call
     * @param promoteCalls
     * @param <T>
     */
    private <T> void finished(Deque<T> calls, T call, boolean promoteCalls) {
        int runningCallsCount;
        Runnable idleCallback;
        synchronized (this) {
            if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");
            if (promoteCalls) promoteCalls();//promote 促进
            runningCallsCount = runningCallsCount();
            idleCallback = this.idleCallback;
        }

        if (runningCallsCount == 0 && idleCallback != null) {
            idleCallback.run();
        }
    }

    /**
     * Returns a snapshot of the calls currently awaiting execution.
     */
    public synchronized List<Call> queuedCalls() {
        List<Call> result = new ArrayList<>();
        for (AsyncCall asyncCall : readyAsyncCalls) {
            result.add(asyncCall.get());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns a snapshot of the calls currently being executed.
     */
    public synchronized List<Call> runningCalls() {
        List<Call> result = new ArrayList<>();
        result.addAll(runningSyncCalls);
        for (AsyncCall asyncCall : runningAsyncCalls) {
            result.add(asyncCall.get());
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized int queuedCallsCount() {
        return readyAsyncCalls.size();
    }

    public synchronized int runningCallsCount() {
        return runningAsyncCalls.size() + runningSyncCalls.size();
    }
}
