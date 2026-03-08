package com.d3intran.nitpicker.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 媒体会话持有者（单例）。
 * 用于在 HomeViewModel（搜索结果） 和 MediaViewerScreen（全屏查看）之间共享
 * 当前的媒体 URI 列表和当前索引，从而实现左右滑动切换图片的功能。
 */
@Singleton
class MediaSessionHolder @Inject constructor() {
    var mediaUris: List<String> = emptyList()
    var currentIndex: Int = 0
}
