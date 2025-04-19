// filepath: app/src/main/java/com/example/nitpicker/NitpickerApplication.kt
package com.example.nitpicker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NitpickerApplication : Application() {
    // 你可以在这里添加自定义的 Application 逻辑
}