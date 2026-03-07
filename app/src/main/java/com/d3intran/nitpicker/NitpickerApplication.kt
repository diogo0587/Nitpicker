package com.d3intran.nitpicker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Nitpicker 应用程序类。
 * 集成了 Hilt 依赖注入，并配置了 Coil 视频解码和 WorkManager 自定义配置。
 */
@HiltAndroidApp
class NitpickerApplication : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * 配置 Coil 全局图片加载器，支持视频帧解码。
     */
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }

    /**
     * 配置 WorkManager 使用 Hilt 注入的 WorkerFactory。
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}