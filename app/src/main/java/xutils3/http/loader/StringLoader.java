package xutils3.http.loader;

import android.text.TextUtils;

import xutils3.cache.DiskCacheEntity;
import xutils3.common.util.IOUtil;
import xutils3.http.RequestParams;
import xutils3.http.request.HttpRequest;
import xutils3.http.request.UriRequest;

import java.io.InputStream;

/**
 * Author: wyouflf
 * Time: 2014/05/30
 */
/*package*/ class StringLoader extends Loader<String> {

    private String charset = "UTF-8";
    private String resultStr = null;

    @Override
    public Loader<String> newInstance() {
        return new StringLoader();
    }

    @Override
    public void setParams(final RequestParams params) {
        if (params != null) {
            String charset = params.getCharset();
            if (!TextUtils.isEmpty(charset)) {
                this.charset = charset;
            }
        }
    }

    @Override
    public String load(final InputStream in) throws Throwable {
        resultStr = IOUtil.readStr(in, charset);
        return resultStr;
    }

    @Override
    public String load(final UriRequest request) throws Throwable {
        /**
         * 执行 {@link HttpRequest#sendRequest() 去发起请求}
         */
        request.sendRequest();
        return this.load(request.getInputStream());
    }

    @Override
    public String loadFromCache(final DiskCacheEntity cacheEntity) throws Throwable {
        if (cacheEntity != null) {
            return cacheEntity.getTextContent();
        }

        return null;
    }

    @Override
    public void save2Cache(UriRequest request) {
        saveStringCache(request, resultStr);
    }
}
