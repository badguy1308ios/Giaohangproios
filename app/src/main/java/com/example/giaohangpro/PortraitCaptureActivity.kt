package com.example.giaohangpro

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Activity quét mã cố định ở chế độ dọc.
 * Orientation được khóa thêm trong AndroidManifest để tránh xoay ngang khi mở camera.
 */
class PortraitCaptureActivity : CaptureActivity()
