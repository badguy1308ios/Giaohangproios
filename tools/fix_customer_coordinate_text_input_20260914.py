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

previous = '''                            fun setCoord(raw: String, latitudeField: Boolean) {
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

desired = '''                            fun setCoord(raw: String, latitudeField: Boolean) {
                                // Có thể paste cả cặp vào BẤT KỲ ô nào. App tự lấy 2 số đầu tiên,
                                // nhận diện vĩ độ/kinh độ theo miền giá trị và gán đúng hai textbox.
                                // Ví dụ hợp lệ: "10.796597, 106.950227", "106.950227 10.796597"
                                // hoặc chuỗi có nhãn như "Vĩ độ: 10.796597 Kinh độ: 106.950227".
                                val text = raw.trim()
                                val values = Regex("""[-+]?\\d{1,3}(?:[.,]\\d+)?""")
                                    .findAll(text)
                                    .map { it.value.replace(',', '.') }
                                    .toList()

                                if (values.size >= 2) {
                                    val firstText = values[0]
                                    val secondText = values[1]
                                    val first = firstText.toDoubleOrNull()
                                    val second = secondText.toDoubleOrNull()

                                    val latLng = when {
                                        first != null && second != null && kotlin.math.abs(first) <= 90.0 && kotlin.math.abs(second) <= 180.0 -> firstText to secondText
                                        first != null && second != null && kotlin.math.abs(second) <= 90.0 && kotlin.math.abs(first) <= 180.0 -> secondText to firstText
                                        else -> null
                                    }

                                    if (latLng != null) {
                                        addresses[index] = addresses[index].copy(
                                            latitude = latLng.first,
                                            longitude = latLng.second
                                        )
                                        return
                                    }
                                }

                                // Nếu chỉ nhập một tọa độ thì giữ toàn bộ số, không giới hạn độ dài.
                                val value = text.replace(',', '.')
                                if (latitudeField) addresses[index] = addresses[index].copy(latitude = value)
                                else addresses[index] = addresses[index].copy(longitude = value)
                            }
'''

if desired in s:
    print('customer coordinate auto-split already applied')
elif previous in s:
    s = s.replace(previous, desired, 1)
    p.write_text(s)
    print('customer coordinate auto-split upgraded')
elif old in s:
    s = s.replace(old, desired, 1)
    p.write_text(s)
    print('customer coordinate auto-split applied')
else:
    raise SystemExit('coordinate input anchor not found')
