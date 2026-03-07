package com.d3intran.nitpicker.model

/**
 * 媒体专辑（合集）的数据模型。
 *
 * 代表一个远程资源合集，包含标题、所含文件数量以及访问地址。
 * 对应远程 API 返回的"Gallery/Album"概念。
 *
 * @property title 专辑显示标题
 * @property fileCount 专辑中包含的媒体文件数量
 * @property url 专辑的访问地址（用于跳转到详情页获取文件列表）
 */
data class Album(
    val title: String,
    val fileCount: Int,
    val url: String
)