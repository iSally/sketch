/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.sketch.request;

import android.util.Log;

import me.xiaopan.sketch.Sketch;
import me.xiaopan.sketch.cache.DiskCache;
import me.xiaopan.sketch.util.SketchUtils;

/**
 * 下载请求
 */
public class DownloadRequest extends BaseRequest {
    private RequestAttrs requestAttrs;
    private DownloadOptions options;
    private DownloadListener downloadListener;
    private DownloadProgressListener progressListener;

    private DownloadResult downloadResult;

    public DownloadRequest(
            Sketch sketch, RequestAttrs requestAttrs,
            DownloadOptions options, DownloadListener downloadListener,
            DownloadProgressListener progressListener) {
        super(sketch);

        this.requestAttrs = requestAttrs;
        this.options = options;
        this.downloadListener = downloadListener;
        this.progressListener = progressListener;

        setLogName("DownloadRequest");
    }

    /**
     * 获取请求基本属性
     */
    public RequestAttrs getRequestAttrs() {
        return requestAttrs;
    }

    /**
     * 获取下载选项
     */
    public DownloadOptions getOptions() {
        return options;
    }

    /**
     * 获取下载结果
     */
    public DownloadResult getDownloadResult() {
        return downloadResult;
    }

    /**
     * 设置下载结果
     */
    protected void setDownloadResult(DownloadResult downloadResult) {
        this.downloadResult = downloadResult;
    }

    @Override
    public void failed(FailedCause failedCause) {
        super.failed(failedCause);

        if (downloadListener != null) {
            postRunFailed();
        }
    }

    @Override
    public void canceled(CancelCause cancelCause) {
        super.canceled(cancelCause);

        if (downloadListener != null) {
            postRunCanceled();
        }
    }

    @Override
    protected void submitRunDispatch() {
        setStatus(Status.WAIT_DISPATCH);
        super.submitRunDispatch();
    }

    @Override
    protected void submitRunDownload() {
        setStatus(Status.WAIT_DOWNLOAD);
        super.submitRunDownload();
    }

    @Override
    protected void submitRunLoad() {
        setStatus(Status.WAIT_LOAD);
        super.submitRunLoad();
    }

    @Override
    protected void runDispatch() {
        setStatus(Status.DISPATCHING);

        // 然后从磁盘缓存中找缓存文件
        if (options.isCacheInDisk()) {
            DiskCache diskCache = getSketch().getConfiguration().getDiskCache();
            DiskCache.Entry diskCacheEntry = diskCache.get(requestAttrs.getDiskCacheKey());
            if (diskCacheEntry != null) {
                if (Sketch.isDebugMode()) {
                    Log.d(Sketch.TAG, SketchUtils.concat(getLogName(),
                            " - ", "runDispatch",
                            " - ", "diskCache",
                            " - ", requestAttrs.getName()));
                }
                downloadResult = new DownloadResult(diskCacheEntry, false);
                downloadComplete();
                return;
            }
        }

        // 在下载之前判断如果请求Level限制只能从本地加载的话就取消了
        if (options.getRequestLevel() == RequestLevel.LOCAL) {
            requestLevelIsLocal();
            return;
        }

        // 执行下载
        if (Sketch.isDebugMode()) {
            Log.d(Sketch.TAG, SketchUtils.concat(getLogName(),
                    " - ", "runDispatch",
                    " - ", "download",
                    " - ", requestAttrs.getName()));
        }
        submitRunDownload();
    }

    @Override
    protected void runDownload() {
        if (isCanceled()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runDownload",
                        " - ", "canceled",
                        " - ", "startDownload",
                        " - ", requestAttrs.getName()));
            }
            return;
        }

        // 调用下载器下载
        DownloadResult justDownloadResult = getSketch().getConfiguration().getImageDownloader().download(this);

        if (isCanceled()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runDownload",
                        " - ", "canceled",
                        " - ", "downloadAfter",
                        " - ", requestAttrs.getName()));
            }
            return;
        }

        // 都是空的就算下载失败
        if (justDownloadResult == null
                || (justDownloadResult.getDiskCacheEntry() == null && justDownloadResult.getImageData() == null)) {
            failed(FailedCause.DOWNLOAD_FAIL);
            return;
        }

        // 下载成功了
        downloadResult = justDownloadResult;
        downloadComplete();
    }

    @Override
    protected void runLoad() {

    }

    /**
     * 更新进度
     * @param totalLength 文件总长度
     * @param completedLength 已完成长度
     */
    public void updateProgress(int totalLength, int completedLength) {
        if (progressListener != null) {
            postRunUpdateProgress(totalLength, completedLength);
        }
    }

    /**
     * 处理RequestLevel是LOCAL
     */
    protected void requestLevelIsLocal(){
        boolean isPauseDownload = options.getRequestLevelFrom() == RequestLevelFrom.PAUSE_DOWNLOAD;

        if (Sketch.isDebugMode()) {
            Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                    " - ", "runDispatch",
                    " - ", "canceled",
                    " - ", isPauseDownload ? "pause download" : "requestLevel is local",
                    " - ", requestAttrs.getName()));
        }

        canceled(isPauseDownload ? CancelCause.PAUSE_DOWNLOAD : CancelCause.LEVEL_IS_LOCAL);
    }

    /**
     * 下载完成后续处理
     */
    protected void downloadComplete(){
        postRunCompleted();
    }

    @Override
    protected void runUpdateProgressInMainThread(int totalLength, int completedLength) {
        if (isFinished()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runUpdateProgressInMainThread",
                        " - ", "finished",
                        " - ", requestAttrs.getName()));
            }
            return;
        }

        if (progressListener != null) {
            progressListener.onUpdateDownloadProgress(totalLength, completedLength);
        }
    }

    @Override
    protected void runCanceledInMainThread() {
        if (downloadListener != null) {
            downloadListener.onCanceled(getCancelCause());
        }
    }

    @Override
    protected void runCompletedInMainThread() {
        if (isCanceled()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runCompletedInMainThread",
                        " - ", "canceled",
                        " - ", requestAttrs.getName()));
            }
            return;
        }

        setStatus(Status.COMPLETED);

        if (downloadListener != null) {
            if (downloadResult.getDiskCacheEntry() != null) {
                downloadListener.onCompleted(downloadResult.getDiskCacheEntry().getFile(), downloadResult.isFromNetwork());
            } else if (downloadResult.getImageData() != null) {
                downloadListener.onCompleted(downloadResult.getImageData());
            }
        }
    }

    @Override
    protected void runFailedInMainThread() {
        if (isCanceled()) {
            if (Sketch.isDebugMode()) {
                Log.w(Sketch.TAG, SketchUtils.concat(getLogName(),
                        " - ", "runFailedInMainThread",
                        " - ", "canceled",
                        " - ", requestAttrs.getName()));
            }
            return;
        }

        if (downloadListener != null) {
            downloadListener.onFailed(getFailedCause());
        }
    }
}