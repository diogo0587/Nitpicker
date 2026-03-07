package com.d3intran.nitpicker.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块：网络层。
 *
 * 提供全局共享的 [OkHttpClient] 单例，用于：
 * - [DownloadManagerService] 中的文件下载（支持断点续传 Range 请求）
 * - 未来接入 Pexels/Unsplash REST API 时的搜索请求
 *
 * 超时配置说明：
 * - 连接超时 30s：覆盖弱网 / 首次 DNS 解析场景
 * - 读取超时 120s：覆盖大文件流式下载的慢速读取
 * - 写入超时 60s：覆盖 POST 上传场景（当前未使用）
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}