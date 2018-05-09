package xutils3.http.loader;


import android.text.TextUtils;

import xutils3.cache.DiskCacheEntity;
import xutils3.cache.LruDiskCache;
import xutils3.http.ProgressHandler;
import xutils3.http.RequestParams;
import xutils3.http.request.HttpRequest;
import xutils3.http.request.UriRequest;

import java.io.InputStream;
import java.util.Date;

/**
 * Author: wyouflf
 * Time: 2014/05/26
 *
 * 加载数据，
 */
public abstract class Loader<T> {

    protected RequestParams params;
    protected ProgressHandler progressHandler;

    public void setParams(final RequestParams params) {
        this.params = params;
    }

    public void setProgressHandler(final ProgressHandler callbackHandler) {
        this.progressHandler = callbackHandler;
    }

    //设置 缓存的缓存时间等信息
    protected void saveStringCache(UriRequest request, String resultStr) {
        if (!TextUtils.isEmpty(resultStr)) {
            DiskCacheEntity entity = new DiskCacheEntity();
            /**
             * {@link HttpRequest#getCacheKey()}
             */
            entity.setKey(request.getCacheKey());//设置 缓存对象对应的key值
            entity.setLastAccess(System.currentTimeMillis());
            entity.setEtag(request.getETag());
            entity.setExpires(request.getExpiration());//设置了过期时间
            entity.setLastModify(new Date(request.getLastModified()));
            entity.setTextContent(resultStr);
            LruDiskCache.getDiskCache(request.getParams().getCacheDirName()).put(entity);
        }
    }

    public abstract Loader<T> newInstance();

    public abstract T load(final InputStream in) throws Throwable;

    public abstract T load(final UriRequest request) throws Throwable;

    public abstract T loadFromCache(final DiskCacheEntity cacheEntity) throws Throwable;

    public abstract void save2Cache(final UriRequest request);
}
