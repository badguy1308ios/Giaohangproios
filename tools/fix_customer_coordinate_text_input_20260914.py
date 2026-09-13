from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()
old = '''                            fun setCoord(raw: String, latitudeField: Boolean) {
                                val pair = Regex("""([-+]?\\d{1,3}(?:\\.\\d+)?)\\s*[,;\\s]\\s*([-+]?\\d{1,3}(?:\\.\\d+)?)""").find(raw.trim())
                                if (pair != null) {
                                    var a = pair.groupValues[1]; var b = pair.groupValues[2]
                                    val av=a.toDoubleOrNull(); val bv=b.toDoubleOrNull()
                                    if (av != null && bv != null && kotlin.math.abs(av) > 90 && kotlin.math.abs(bv) <= 90) { val t=a; a=b; b=t }
                                    addresses[index] = addresses[index].copy(latitude = a, longitude = b)
                                } else if (latitudeField) addresses[index] = addresses[index].copy(latitude = raw.replace(',', '.'))
                                else addresses[index] = addresses[index].copy(longitude = raw.replace(',', '.'))
                            }
'''
new = '''                            fun setCoord(raw: String, latitudeField: Boolean) {
                                // Không cắt chuỗi tọa độ khi gõ/paste. Chỉ tách cặp khi clipboard
                                // thực sự chứa HAI tọa độ (vd: "10.796597, 106.950227").
                                val text = raw.trim()
                                val pair = Regex("""^\\s*([-+]?\\d{1,3}(?:[.,]\\d+)?)\\s*[,;\\s]+\\s*([-+]?\\d{1,3}(?:[.,]\\d+)?)\\s*$""")
                                    .matchEntire(text)
                                if (pair != null) {
                                    var a = pair.groupValues[1].replace(',', '.')
                                    var b = pair.groupValues[2].replace(',', '.')
                                    val av = a.toDoubleOrNull(); val bv = b.toDoubleOrNull()
                                    if (av != null && bv != null && kotlin.math.abs(av) > 90 && kotlin.math.abs(bv) <= 90) {
                                        val t = a; a = b; b = t
                                    }
                                    addresses[index] = addresses[index].copy(latitude = a, longitude = b)
                                } else {
                                    // Một giá trị riêng lẻ được giữ nguyên toàn bộ độ chính xác.
                                    // Dấu phẩy thập phân từ bàn phím VN được đổi thành dấu chấm.
                                    val value = text.replace(',', '.')
                                    if (latitudeField) addresses[index] = addresses[index].copy(latitude = value)
                                    else addresses[index] = addresses[index].copy(longitude = value)
                                }
                            }
'''
if old not in s:
    raise SystemExit('coordinate input anchor not found')
s = s.replace(old, new, 1)
p.write_text(s)
print('customer coordinate fields now keep full precision and support pair paste')
