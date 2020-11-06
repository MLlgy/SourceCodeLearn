## Retrofit 涉及到的线程切换

### Call 涉及到的线程切换


1. 在后台进行网络请求的代码的位置


serviceMethod.callAdapter.adapt(okHttpCall)

获取到 Adapter， 默认为 platform.defaultCallAdapterFactory(callbackExecutor)，
涉及的 Adapter 即为：


ExecutorCallAdapterFactory#get

```
final class ExecutorCallAdapterFactory extends CallAdapter.Factory {
    final Executor callbackExecutor;

    ExecutorCallAdapterFactory(Executor callbackExecutor) {
        this.callbackExecutor = callbackExecutor;
    }

    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
        if (getRawType(returnType) != Call.class) {
            return null;
        }
        final Type responseType = Utils.getCallResponseType(returnType);
        return new CallAdapter<Object, Call<?>>() {
            @Override
            public Type responseType() {
                return responseType;
            }

            @Override
            public Call<Object> adapt(Call<Object> call) {
                return new ExecutorCallbackCall<>(callbackExecutor, call);
            }
        };
    }
```

所以 serviceMethod.callAdapter.adapt(okHttpCall) 即为 ExecutorCallbackCall，

所以 RetrofitFactory.getInstance().checkCoupon("2018040202512043", "133", 315).enqueue(),即为
ExecutorCallbackCall#enqueue，真正的执行位置为：

ExecutorCallAdapterFactory#ExecutorCallbackCall#enqueue 具体代码逻辑如下：


```
@Override
public void enqueue(final Callback<T> callback) {
    checkNotNull(callback, "callback == null");
    //delegate 实际为 OkHttpCall
    delegate.enqueue(new Callback<T>() {
        @Override
        public void onResponse(Call<T> call, final Response<T> response) {
            /**
             * 线程池的执行,主线程线程池 MainThreadExecutor，实现将请求的结果发送到主线程
             * 将 Runnable 发送到 UI 线程中
             */
            callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (delegate.isCanceled()) {
                        callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
                    } else {
                        callback.onResponse(ExecutorCallbackCall.this, response);
                    }
                }
            });
        }

        @Override
        public void onFailure(Call<T> call, final Throwable t) {
            callbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    callback.onFailure(ExecutorCallbackCall.this, t);
                }
            });
        }
    });
}
```

可以看到网络请求后的结果会通过 callbackExecutor 发送到主线程，callbackExecutor 为一个主线程的 Handler。


网络请求的位置为 OkHttpCall#enqueue  --> RealCall#enqueue  -->

```
@Override
public void enqueue(Callback responseCallback) {
    synchronized (this) {
        if (executed) throw new IllegalStateException("Already Executed");
        executed = true;
    }
    captureCallStackTrace();
    client.dispatcher().enqueue(new AsyncCall(responseCallback));
}
```

Dispather#enqueue:
Dispather 的作用：用来切换发起网络的线程


```
synchronized void  enqueue(AsyncCall call) {
   /**
    * 正在运行的异步请求队列的大小 < 最大并发请求数  同一个host发起的请求数 < 并且每个主机最大请求数
    *
    * 满足该要求，把 Call 添加到运行着的消息的队列，该 Call 会被马上执行，不满足的话，把消息添加到将要运行的信息的队列。
    */
   if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) {
       runningAsyncCalls.add(call);
       // 线程池执行请求
       executorService().execute(call);
   } else {
       readyAsyncCalls.add(call);
   }
}
```

通过 executorService().execute(call) 开始执行真正，executorService() 会创建线程池，从而会在线程池中执行 AsyncCall。

```
public synchronized ExecutorService executorService() {
    if (executorService == null) {
        executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
    }
    return executorService;
}
```

AsyncCall#execute，发起网络请求的位置，只是位于子线程 -- executorService


```
@Override
protected void execute() {
    boolean signalledCallback = false;
    try {
        // 开始真正的发起网络请求
        Response response = getResponseWithInterceptorChain();
        if (retryAndFollowUpInterceptor.isCanceled()) {
            signalledCallback = true;
            responseCallback.onFailure(RealCall.this, new IOException("Canceled"));
        } else {
            signalledCallback = true;
            responseCallback.onResponse(RealCall.this, response);
        }
    } catch (IOException e) {
        if (signalledCallback) {
            Platform.get().log(INFO, "Callback failure for " + toLoggableString(), e);
        } else {
            responseCallback.onFailure(RealCall.this, e);
        }
    } finally {
        client.dispatcher().finished(this);
    }
}
```




## RxJavaCallAdapter 的线程切换


相应的 Factory 为：RxJava2CallAdapterFactory
相应的 CallAdapter 为： RxJava2CallAdapter





serviceMethod.callAdapter.adapt(okHttpCall)，向 CallAdapter 传递 Call 对象，RxJava2CallAdapter 最终生成的 Observer 对象对 Call 对象进行包裹，同时做一些其他操作。


0. 在初始化过中可以通过调用不同的 API 通知 Observerable 执行的线程：

0.1 创建同步 Observerable，即 Observerable 会在主线中执行，如果切换线程，则需要通过 subscribeOn 来切换线程

```
new Retrofit.Builder().baseUrl("https://api.github.com/")
                      .addCallAdapterFactory(RxJava2CallAdapterFactory.create())


RxJava2CallAdapterFactory#create
public static RxJava2CallAdapterFactory create() {
    return new RxJava2CallAdapterFactory(null, false);
}

```

0.2 创建异步 Observerable，那么后续 Observerable事件流会在子线程中执行



```

new Retrofit.Builder().baseUrl("https://api.github.com/").addCallAdapterFactory(RxJava2CallAdapterFactory.createAsync())

public static RxJava2CallAdapterFactory createAsync() {
    return new RxJava2CallAdapterFactory(null, true);
}

```


1. 包裹 Call，根据 isAsync 生成异步调用 CallEnqueueObservable 对象、同步调用 CallExecuteObservable 对象

```
Observable<Response<R>> responseObservable = isAsync
                ? new CallEnqueueObservable<>(call)
                : new CallExecuteObservable<>(call);
```


2. Observable 中一些处理

异步调用相关逻辑，所有的 Call 都被封装在 CallEnqueueObservable 中执行，通过 call.enqueue(callback); 将请求在子线程中执行


```
final class CallEnqueueObservable<T> extends Observable<Response<T>> {
    private final Call<T> originalCall;

    CallEnqueueObservable(Call<T> originalCall) {
        this.originalCall = originalCall;
    }

    @Override
    protected void subscribeActual(Observer<? super Response<T>> observer) {
        // Since Call is a one-shot type, clone it for each new observer.
        Call<T> call = originalCall.clone();

        CallCallback<T> callback = new CallCallback<>(call, observer);
        observer.onSubscribe(callback);
        // 真正执行网络操作，
        call.enqueue(callback);
    }

    private static final class CallCallback<T> implements Disposable, Callback<T> {
        private final Call<?> call;
        private final Observer<? super Response<T>> observer;
        boolean terminated = false;

        CallCallback(Call<?> call, Observer<? super Response<T>> observer) {
            this.call = call;
            this.observer = observer;
        }

        @Override
        public void onResponse(Call<T> call, Response<T> response) {

    }
}

```






final class CallExecuteObservable<T> extends Observable<Response<T>> {
    private final Call<T> originalCall;

    CallExecuteObservable(Call<T> originalCall) {
        this.originalCall = originalCall;
    }

    @Override
    protected void subscribeActual(Observer<? super Response<T>> observer) {
        Call<T> call = originalCall.clone();
        // 在当前线程执行
        observer.onSubscribe(new CallDisposable(call));

        boolean terminated = false;
        try {
            Response<T> response = call.execute();
            if (!call.isCanceled()) {
                observer.onNext(response);
            }
            if (!call.isCanceled()) {
                terminated = true;
                observer.onComplete();
            }
        } catch (Throwable t) {
            Exceptions.throwIfFatal(t);
            if (terminated) {
                RxJavaPlugins.onError(t);
            } else if (!call.isCanceled()) {
                try {
                    observer.onError(t);
                } catch (Throwable inner) {
                    Exceptions.throwIfFatal(inner);
                    RxJavaPlugins.onError(new CompositeException(t, inner));
                }
            }
        }
    }




## RxJava 的核心方法


1. 最下游显式的创建了 Observer(观察者)

```
Single.map(new Function<List<Repo>, List<Repo>>() {
    @Override
    public List<Repo> apply(List<Repo> repos) throws Exception {
        return null;
    }
})
.subscribe(new SingleObserver<List<Repo>>() {
    // new SingleObserver<List<Repo>>()
});
```

2. 调用 subscribe 开始执行订阅过程

此处真正发起调用的为 Single.subscribe

```
public final void subscribe(SingleObserver<? super T> observer) {
    ObjectHelper.requireNonNull(observer, "subscriber is null");
    observer = RxJavaPlugins.onSubscribe(this, observer);
    try {
        // 此处真正调用的为 SingleMap.subscribeActual,因为 map 操作符生成的 SingleMap 对象
        // 可以看到 observer 对象为下游传递过来的 Observer 对象
        subscribeActual(observer);
    } catch (NullPointerException ex) {
        throw ex;
    } catch (Throwable ex) {
        Exceptions.throwIfFatal(ex);
        NullPointerException npe = new NullPointerException("subscribeActual failed");
        npe.initCause(ex);
        throw npe;
    }
}
```


SingleMap#subscribeActual

```
@Override
protected void subscribeActual(final SingleObserver<? super R> t) {
    // 在此处生成 map 操作符对应的 Observer 对象，可以看到上游的 Observer 对象会持有下游 Observer 对象，
    // 这是事件传递流程中重要的一个步骤：上游 Observer 持有下游 Oberver 对象，那么事件才能从上游向下游传递
    source.subscribe(new MapSingleObserver<T, R>(t, mapper));
}
```

subscribeActual 主要做的有两件事情：

1. 创建该操作符对应的 Observer 对象，比如 Single.map 生成具体的 Observer 为 MapSingleObserver；
2. 上游 Observerable 订阅本操作符生成的 Observer 对象，即上游 Observerable 与下一级操作符生成的 Observer 产生订阅关系

探究一下此处 source 从何而来？

SingleMap 的构造函数：

```
public SingleMap(SingleSource<? extends T> source, Function<? super T, ? extends R> mapper) {
    this.source = source;
    this.mapper = mapper;
}

```

那么什么时候会调用 SingleMap 创建该对象，当然是调用 map 操作符时：

```
public final <R> Single<R> map(Function<? super T, ? extends R> mapper) {
    return RxJavaPlugins.onAssembly(new SingleMap<T, R>(this, mapper));
}
```

在初始化 SingleMap 时传入 this，此处的 this 即为上一级 Observerable 对象




## Rxjava 总结


流程得以继续下去的原因：

1. 下游 Observerable 对象(A2)持有上游 Observerable 的对象引用(A1)
2.




























