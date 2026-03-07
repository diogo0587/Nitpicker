package com.d3intran.nitpicker.di

import com.d3intran.nitpicker.api.MediaApiService
import com.d3intran.nitpicker.api.MockMediaApiService
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 依赖注入模块：API 层接口绑定。
 *
 * 将 [MediaApiService] 接口绑定到具体实现。
 * 切换数据源时只需修改此模块，无需改动 Repository 或 ViewModel。
 *
 * - 开发/调试环境：绑定 [MockMediaApiService]
 * - 生产环境：绑定 PexelsApiService（取消下方注释）
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ApiModule {

    /**
     * 当前绑定 Mock 实现用于开发调试。
     * 切换为 Pexels 时，将此方法的实现类改为 PexelsApiService 即可。
     */
    @Binds
    @Singleton
    abstract fun bindMediaApiService(
        impl: MockMediaApiService
    ): MediaApiService
}
