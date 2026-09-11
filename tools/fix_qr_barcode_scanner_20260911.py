from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text(encoding='utf-8')

old = '''                scanLauncher.launch(ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Đưa mã QR vào giữa khung")
                    setBeepEnabled(false)
                    setOrientationLocked(false)'''
new = '''                scanLauncher.launch(ScanOptions().apply {
                    // Quét cả QR và các mã vạch 1D phổ biến (Code 128, EAN, UPC, Code 39...).
                    setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                    setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung")
                    setBeepEnabled(false)
                    // Dùng CaptureActivity riêng và khóa dọc để camera không xoay ngang.
                    setCaptureActivity(PortraitCaptureActivity::class.java)
                    setOrientationLocked(true)'''

if old in s:
    s = s.replace(old, new, 1)
else:
    # Idempotent fallback cho trường hợp source đã được chỉnh một phần.
    s = s.replace('setDesiredBarcodeFormats(ScanOptions.QR_CODE)', 'setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)')
    s = s.replace('setPrompt("Đưa mã QR vào giữa khung")', 'setPrompt("Đưa mã QR hoặc mã vạch vào giữa khung")')
    s = s.replace('setOrientationLocked(false)', 'setCaptureActivity(PortraitCaptureActivity::class.java)\n                    setOrientationLocked(true)')

p.write_text(s, encoding='utf-8')
print('QR/barcode scanner updated for all formats + portrait mode')
