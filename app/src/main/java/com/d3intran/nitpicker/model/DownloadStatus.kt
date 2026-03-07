package com.d3intran.nitpicker.model

/**
 * 下载任务的状态枚举。
 *
 * 状态转换流程：
 * ```
 * Pending → FetchingUrl → Downloading → Completed
 *                ↓              ↓
 *              Error          Error
 *                ↓              ↓
 *             Pending        Pending   (重试时回到 Pending)
 *
 * 任何状态 → Cancelled       (用户主动取消)
 * ```
 */
enum class DownloadStatus {
    /** 等待队列中，尚未开始处理 */
    Pending,

    /** 正在解析真实下载地址（从中间页提取直链） */
    FetchingUrl,

    /** 正在下载文件内容（支持断点续传） */
    Downloading,

    /** 下载已暂停（预留功能） */
    Paused,

    /** 下载成功完成，文件已保存到本地 */
    Completed,

    /** 下载过程中发生错误（网络异常、HTTP 416 等） */
    Error,

    /** 用户主动取消了下载任务 */
    Cancelled
}