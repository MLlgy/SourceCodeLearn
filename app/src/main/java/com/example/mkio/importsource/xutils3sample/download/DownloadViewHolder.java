package com.example.mkio.importsource.xutils3sample.download;

import android.view.View;

import java.io.File;

import xutils3.common.Callback;
import xutils3.x;

/**
 * Created by wyouflf on 15/11/10.
 */
public abstract class DownloadViewHolder {

    protected DownloadInfo downloadInfo;

    public DownloadViewHolder(View view, DownloadInfo downloadInfo) {
        this.downloadInfo = downloadInfo;
        x.view().inject(this, view);
    }

    public final DownloadInfo getDownloadInfo() {
        return downloadInfo;
    }

    public void update(DownloadInfo downloadInfo) {
        this.downloadInfo = downloadInfo;
    }

    public abstract void onWaiting();

    public abstract void onStarted();

    public abstract void onLoading(long total, long current);

    public abstract void onSuccess(File result);

    public abstract void onError(Throwable ex, boolean isOnCallback);

    public abstract void onCancelled(Callback.CancelledException cex);
}
