package xutils3.http;

import android.text.TextUtils;
import android.view.View;

import com.example.mkio.importsource.xutils3sample.HttpFragment;

import java.io.Closeable;
import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import xutils3.common.Callback;
import xutils3.common.task.AbsTask;
import xutils3.common.task.Priority;
import xutils3.common.task.PriorityExecutor;
import xutils3.common.util.IOUtil;
import xutils3.common.util.LogUtil;
import xutils3.common.util.ParameterizedTypeUtil;
import xutils3.ex.HttpException;
import xutils3.ex.HttpRedirectException;
import xutils3.http.app.HttpRetryHandler;
import xutils3.http.app.RedirectHandler;
import xutils3.http.app.RequestInterceptListener;
import xutils3.http.app.RequestTracker;
import xutils3.http.request.HttpRequest;
import xutils3.http.request.UriRequest;
import xutils3.http.request.UriRequestFactory;
import xutils3.x;

/**
 * Created by wyouflf on 15/7/23.
 * http 请求任务
 * 此处真正的进行网络请求
 */
public class HttpTask<ResultType> extends AbsTask<ResultType> implements ProgressHandler {

    private final static int MAX_FILE_LOAD_WORKER = 3;
    private final static AtomicInteger sCurrFileLoadCount = new AtomicInteger(0);
    // 文件下载任务
    private static final HashMap<String, WeakReference<HttpTask<?>>>
            DOWNLOAD_TASK = new HashMap<String, WeakReference<HttpTask<?>>>(1);
    private static final PriorityExecutor HTTP_EXECUTOR = new PriorityExecutor(5, true);
    private static final PriorityExecutor CACHE_EXECUTOR = new PriorityExecutor(5, true);
    private static final int FLAG_REQUEST_CREATED = 1;
    private static final int FLAG_CACHE = 2;
    private static final int FLAG_PROGRESS = 3;
    private final Executor executor;//线程池
    private final Callback.CommonCallback<ResultType> callback;
    private final Object cacheLock = new Object();
    // 请求相关
    private RequestParams params;//请求参数
    private UriRequest request;//请求对象
    private RequestWorker requestWorker;
    private volatile boolean hasException = false;
    // 缓存控制
    private Object rawResult = null;
    private volatile Boolean trustCache = null;
    // 扩展callback
    private Callback.CacheCallback<ResultType> cacheCallback;
    private Callback.PrepareCallback prepareCallback;
    private Callback.ProgressCallback progressCallback;
    private RequestInterceptListener requestInterceptListener;
    // 日志追踪
    private RequestTracker tracker;
    // 文件下载线程数限制
    private Type loadType;
    // ############################### start: region implements ProgressHandler
    private long lastUpdateTime;
    private long loadingUpdateMaxTimeSpan = 300; // 300ms

    public HttpTask(RequestParams params, Callback.Cancelable cancelHandler,
                    Callback.CommonCallback<ResultType> callback) {
        super(cancelHandler);

        assert params != null;
        assert callback != null;

        // set params & callback
        this.params = params;
        this.callback = callback;
        /**
         * 转换成 具体的 callback 的
         */
        if (callback instanceof Callback.CacheCallback) {
            this.cacheCallback = (Callback.CacheCallback<ResultType>) callback;
        }
        if (callback instanceof Callback.PrepareCallback) {
            this.prepareCallback = (Callback.PrepareCallback) callback;
        }
        if (callback instanceof Callback.ProgressCallback) {
            this.progressCallback = (Callback.ProgressCallback<ResultType>) callback;
        }
        if (callback instanceof RequestInterceptListener) {
            this.requestInterceptListener = (RequestInterceptListener) callback;
        }

        // init tracker
        {
            RequestTracker customTracker = params.getRequestTracker();
            if (customTracker == null) {
                if (callback instanceof RequestTracker) {
                    customTracker = (RequestTracker) callback;
                } else {
                    customTracker = UriRequestFactory.getDefaultTracker();
                }
            }
            if (customTracker != null) {
                tracker = new RequestTrackerWrapper(customTracker);
            }
        }

        // init executor
        if (params.getExecutor() != null) {
            this.executor = params.getExecutor();
        } else {
            if (cacheCallback != null) {
                this.executor = CACHE_EXECUTOR;
            } else {
                this.executor = HTTP_EXECUTOR;
            }
        }
    }

    // 解析loadType
    private void resolveLoadType() {
        Class<?> callBackType = callback.getClass();
        if (callback instanceof Callback.TypedCallback) {
            loadType = ((Callback.TypedCallback) callback).getLoadType();
        } else if (callback instanceof Callback.PrepareCallback) {
            loadType = ParameterizedTypeUtil.getParameterizedType(callBackType, Callback.PrepareCallback.class, 0);
        } else {
            loadType = ParameterizedTypeUtil.getParameterizedType(callBackType, Callback.CommonCallback.class, 0);
        }
    }

    // 初始化请求参数
    private UriRequest createNewRequest() throws Throwable {
        // init request
        params.init();
        /**
         * 此处会根据 loadType 的类型实例化出 Loader 的类型是StringLoader ？ FileLoader....
         * */
        UriRequest result = UriRequestFactory.getUriRequest(params, loadType);
        result.setCallingClassLoader(callback.getClass().getClassLoader());
        result.setProgressHandler(this);
        this.loadingUpdateMaxTimeSpan = params.getLoadingUpdateMaxTimeSpan();
        this.update(FLAG_REQUEST_CREATED, result);
        return result;
    }

    // 文件下载冲突检测
    private void checkDownloadTask() {
        if (File.class == loadType) {
            synchronized (DOWNLOAD_TASK) {
                String downloadTaskKey = this.params.getSaveFilePath();
                /*{
                    // 不处理缓存文件下载冲突,
                    // 缓存文件下载冲突会抛出FileLockedException异常,
                    // 使用异常处理控制是否重新尝试下载.
                    if (TextUtils.isEmpty(downloadTaskKey)) {
                        downloadTaskKey = this.request.getCacheKey();
                    }
                }*/
                if (!TextUtils.isEmpty(downloadTaskKey)) {
                    WeakReference<HttpTask<?>> taskRef = DOWNLOAD_TASK.get(downloadTaskKey);
                    if (taskRef != null) {
                        HttpTask<?> task = taskRef.get();
                        if (task != null) {
                            task.cancel();
                            task.closeRequestSync();
                        }
                        DOWNLOAD_TASK.remove(downloadTaskKey);
                    }
                    DOWNLOAD_TASK.put(downloadTaskKey, new WeakReference<HttpTask<?>>(this));
                } // end if (!TextUtils.isEmpty(downloadTaskKey))

                if (DOWNLOAD_TASK.size() > MAX_FILE_LOAD_WORKER) {
                    Iterator<Map.Entry<String, WeakReference<HttpTask<?>>>>
                            entryItr = DOWNLOAD_TASK.entrySet().iterator();
                    while (entryItr.hasNext()) {
                        Map.Entry<String, WeakReference<HttpTask<?>>> next = entryItr.next();
                        WeakReference<HttpTask<?>> value = next.getValue();
                        if (value == null || value.get() == null) {
                            entryItr.remove();
                        }
                    }
                }
            } // end synchronized
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ResultType doBackground() throws Throwable {

        if (this.isCancelled()) {
            throw new Callback.CancelledException("cancelled before request");
        }

        // 初始化请求参数
        ResultType result = null;
        resolveLoadType();
        request = createNewRequest();
        checkDownloadTask();//检查现在是否有下载任务
        // retry 初始化
        boolean retry = true;
        int retryCount = 0;
        Throwable exception = null;
        //设置重试次数
        HttpRetryHandler retryHandler = this.params.getHttpRetryHandler();
        if (retryHandler == null) {
            retryHandler = new HttpRetryHandler();
        }
        retryHandler.setMaxRetryCount(this.params.getMaxRetryCount());

        if (this.isCancelled()) {
            throw new Callback.CancelledException("cancelled before request");
        }

        // 检查缓存 允许缓存
        Object cacheResult = null;
        //如果允许缓存，
        if (cacheCallback != null && HttpMethod.permitsCache(params.getMethod())) {
            // 尝试从缓存获取结果, 并为请求头加入缓存控制参数.
            try {
                clearRawResult();
                LogUtil.d("load cache: " + this.request.getRequestUri());
                /**
                 * {@link xutils3.http.request.HttpRequest#loadResultFromCache()}
                 */
                rawResult = this.request.loadResultFromCache();//从缓存中获取数据
            } catch (Throwable ex) {
                LogUtil.w("load disk cache error", ex);
            }

            if (this.isCancelled()) {
                clearRawResult();
                throw new Callback.CancelledException("cancelled before request");
            }

            if (rawResult != null) {
                if (prepareCallback != null) {
                    try {
                        cacheResult = prepareCallback.prepare(rawResult);
                    } catch (Throwable ex) {
                        cacheResult = null;
                        LogUtil.w("prepare disk cache error", ex);
                    } finally {
                        clearRawResult();
                    }
                } else {
                    cacheResult = rawResult;
                }

                if (this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled before request");
                }

                if (cacheResult != null) {
                    //
                    /**
                     * 同步等待是否信任缓存
                     *最终来到这个位置 {@link HttpTask#onUpdate(int, Object...)}
                     */
                    this.update(FLAG_CACHE, cacheResult);
                    synchronized (cacheLock) {
                        while (trustCache == null) {
                            try {
                                cacheLock.wait();
                            } catch (InterruptedException iex) {
                                throw new Callback.CancelledException("cancelled before request");
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    // 处理完成，如果信任该缓存，则返回 null，只是不会继续执行下面的代码（网络请求）
                    if (trustCache) {
                        return null;
                    }
                }
            }
        }

        if (trustCache == null) {
            trustCache = false;
        }

        if (cacheResult == null) {
            this.request.clearCacheHeader();
        }

        // 判断请求的缓存策略
        if (callback instanceof Callback.ProxyCacheCallback) {
            if (((Callback.ProxyCacheCallback) callback).onlyCache()) {
                return null;
            }
        }

        // 发起请求
        retry = true;
        while (retry) {
            retry = false;

            try {
                if (this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled before request");
                }

                // 由loader发起请求, 拿到结果.
                this.request.close(); // retry 前关闭上次请求

                try {
                    clearRawResult();
                    // 开始请求工作
                    LogUtil.d("load: " + this.request.getRequestUri());
                    requestWorker = new RequestWorker();
                    requestWorker.request();
                    if (requestWorker.ex != null) {
                        throw requestWorker.ex;
                    }
                    LogUtil.e("rawData111: " + (ResultType) requestWorker.result);
                    rawResult = requestWorker.result;//由 RequestWorker 获得的真正的数据
                } catch (Throwable ex) {
                    clearRawResult();
                    if (this.isCancelled()) {
                        throw new Callback.CancelledException("cancelled during request");
                    } else {
                        throw ex;
                    }
                }

                if (prepareCallback != null) {

                    if (this.isCancelled()) {
                        throw new Callback.CancelledException("cancelled before request");
                    }

                    try {
                        result = (ResultType) prepareCallback.prepare(rawResult);//在图片请求的模块中 rawResult 的具体类型为 File
                    } finally {
                        clearRawResult();
                    }
                } else {
                    result = (ResultType) rawResult;
                }

                // 保存缓存
                if (cacheCallback != null && HttpMethod.permitsCache(params.getMethod())) {
                    this.request.save2Cache();
                }

                if (this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled after request");
                }
            } catch (HttpRedirectException redirectEx) {
                retry = true;
                LogUtil.w("Http Redirect:" + params.getUri());
            } catch (Throwable ex) {
                switch (this.request.getResponseCode()) {
                    case 204: // empty content
                    case 205: // empty content
                    case 304: // disk cache is valid.
                        return null;
                    default: {
                        exception = ex;
                        if (this.isCancelled() && !(exception instanceof Callback.CancelledException)) {
                            exception = new Callback.CancelledException("canceled by user");
                        }
                        retry = retryHandler.canRetry(this.request, exception, ++retryCount);
                    }
                }
            }

        }

        if (exception != null && result == null && !trustCache) {
            hasException = true;
            throw exception;
        }

        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void onUpdate(int flag, Object... args) {
        switch (flag) {
            case FLAG_REQUEST_CREATED: {
                if (this.tracker != null) {
                    this.tracker.onRequestCreated((UriRequest) args[0]);
                }
                break;
            }
            case FLAG_CACHE: {
                synchronized (cacheLock) {
                    try {
                        ResultType result = (ResultType) args[0];
                        if (tracker != null) {
                            tracker.onCache(request, result);
                        }
                        /**
                         * {@link HttpFragment#onTest1Click(View)#cancelable2#onCache}
                         */
                        trustCache = this.cacheCallback.onCache(result);//检验是否信任缓存数据，通过回调将数据展示出来
                    } catch (Throwable ex) {
                        trustCache = false;
                        callback.onError(ex, true);
                    } finally {
                        cacheLock.notifyAll();
                    }
                }
                break;
            }
            case FLAG_PROGRESS: {
                if (this.progressCallback != null && args.length == 3) {
                    try {
                        this.progressCallback.onLoading(
                                ((Number) args[0]).longValue(),
                                ((Number) args[1]).longValue(),
                                (Boolean) args[2]);
                    } catch (Throwable ex) {
                        callback.onError(ex, true);
                    }
                }
                break;
            }
            default: {
                break;
            }
        }
    }

    @Override
    protected void onWaiting() {
        if (tracker != null) {
            tracker.onWaiting(params);
        }
        if (progressCallback != null) {
            progressCallback.onWaiting();
        }
    }

    @Override
    protected void onStarted() {
        if (tracker != null) {
            tracker.onStart(params);
        }
        if (progressCallback != null) {
            progressCallback.onStarted();
        }
    }

    @Override
    protected void onSuccess(ResultType result) {
        if (hasException) return;
        if (tracker != null) {
            tracker.onSuccess(request, result);
        }
        callback.onSuccess(result);
    }

    @Override
    protected void onError(Throwable ex, boolean isCallbackError) {
        if (tracker != null) {
            tracker.onError(request, ex, isCallbackError);
        }
        callback.onError(ex, isCallbackError);
    }

    @Override
    protected void onCancelled(Callback.CancelledException cex) {
        if (tracker != null) {
            tracker.onCancelled(request);
        }
        callback.onCancelled(cex);
    }

    @Override
    protected void onFinished() {
        if (tracker != null) {
            tracker.onFinished(request);
        }
        x.task().run(new Runnable() {
            @Override
            public void run() {
                closeRequestSync();
            }
        });
        callback.onFinished();
    }

    private void clearRawResult() {
        if (rawResult instanceof Closeable) {
            IOUtil.closeQuietly((Closeable) rawResult);
        }
        rawResult = null;
    }

    @Override
    protected void cancelWorks() {
        x.task().run(new Runnable() {
            @Override
            public void run() {
                closeRequestSync();
            }
        });
    }

    @Override
    protected boolean isCancelFast() {
        return params.isCancelFast();
    }

    private void closeRequestSync() {
        clearRawResult();
        IOUtil.closeQuietly(request);
    }

    @Override
    public Executor getExecutor() {
        return this.executor;
    }

    @Override
    public Priority getPriority() {
        return params.getPriority();
    }

    /**
     * @param total
     * @param current
     * @param forceUpdateUI
     * @return continue
     */
    @Override
    public boolean updateProgress(long total, long current, boolean forceUpdateUI) {

        if (isCancelled() || isFinished()) {
            return false;
        }

        if (progressCallback != null && request != null && total > 0) {
            if (total < current) {
                total = current;
            }
            if (forceUpdateUI) {
                lastUpdateTime = System.currentTimeMillis();
                this.update(FLAG_PROGRESS, total, current, request.isLoading());
            } else {
                long currTime = System.currentTimeMillis();
                if (currTime - lastUpdateTime >= loadingUpdateMaxTimeSpan) {
                    lastUpdateTime = currTime;
                    this.update(FLAG_PROGRESS, total, current, request.isLoading());
                }
            }
        }

        return !isCancelled() && !isFinished();
    }

    // ############################### end: region implements ProgressHandler

    @Override
    public String toString() {
        return params.toString();
    }


    /**
     * 请求发送和加载数据线程.
     * 该线程被join到HttpTask的工作线程去执行.
     * 它的主要作用是为了能强行中断请求的链接过程;
     * 并辅助限制同时下载文件的线程数.
     */
    private final class RequestWorker {
        /*private*/ Object result;
        /*private*/ Throwable ex;

        private RequestWorker() {
        }

        public void request() {
            try {
                boolean interrupted = false;
                if (File.class == loadType) {
                    synchronized (sCurrFileLoadCount) {
                        while (sCurrFileLoadCount.get() >= MAX_FILE_LOAD_WORKER
                                && !HttpTask.this.isCancelled()) {
                            try {
                                sCurrFileLoadCount.wait(10);
                            } catch (InterruptedException iex) {
                                interrupted = true;
                                break;
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                    sCurrFileLoadCount.incrementAndGet();
                }

                if (interrupted || HttpTask.this.isCancelled()) {
                    throw new Callback.CancelledException("cancelled before request" + (interrupted ? "(interrupted)" : ""));
                }

                try {
                    request.setRequestInterceptListener(requestInterceptListener);
                    /**
                     * 开始网络请求，得到真正的数据
                     * {@link HttpRequest#loadResult()}
                     */
                    this.result = request.loadResult();
                } catch (Throwable ex) {
                    this.ex = ex;
                }

                if (this.ex != null) {
                    throw this.ex;
                }
            } catch (Throwable ex) {
                this.ex = ex;
                if (ex instanceof HttpException) {
                    HttpException httpEx = (HttpException) ex;
                    int errorCode = httpEx.getCode();
                    if (errorCode == 301 || errorCode == 302) {
                        RedirectHandler redirectHandler = params.getRedirectHandler();
                        if (redirectHandler != null) {
                            try {
                                RequestParams redirectParams = redirectHandler.getRedirectParams(request);
                                if (redirectParams != null) {
                                    if (redirectParams.getMethod() == null) {
                                        redirectParams.setMethod(params.getMethod());
                                    }
                                    // 开始重定向请求
                                    HttpTask.this.params = redirectParams;
                                    HttpTask.this.request = createNewRequest();
                                    this.ex = new HttpRedirectException(errorCode, httpEx.getMessage(), httpEx.getResult());
                                }
                            } catch (Throwable throwable) {
                                this.ex = ex;
                            }
                        }
                    }
                }
            } finally {
                if (File.class == loadType) {
                    synchronized (sCurrFileLoadCount) {
                        sCurrFileLoadCount.decrementAndGet();
                        sCurrFileLoadCount.notifyAll();
                    }
                }
            }
        }
    }

}
