from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

# Import each finished VTMan record immediately instead of re-importing the whole session.
s=s.replace(
'''            if (records.size != importedCount) { vm.importVtmanRecords(records); importedCount = records.size }''',
'''            if (records.size > importedCount) {
                vm.importVtmanRecords(records.drop(importedCount))
                importedCount = records.size
            } else if (records.size < importedCount) {
                // A new/cleared VTMan session resets controller records; sync the counter only.
                importedCount = records.size
            }''',1)

# CSV input: skip MVĐ that already exists in Chi tiết đơn, and don't add duplicates from the file.
old_csv='''            val codes = text.lineSequence().map { it.trim().removePrefix("\\uFEFF") }
                .filter { it.isNotBlank() }
                .map { it.substringBefore(',').substringBefore(';').trim().trim('"') }
                .filterNot { it.equals("MVĐ", true) || it.contains("mã vận đơn", true) || it.contains("ma van don", true) }
                .filter { it.isNotBlank() }.toList()
            waybills = codes.joinToString("\\n")
            com.example.giaohangpro.vtman.VtmanQueueController.load(codes)
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            Toast.makeText(context, "Đã nạp ${codes.size} MVĐ từ CSV", Toast.LENGTH_SHORT).show()'''
new_csv='''            val rawCodes = text.lineSequence().map { it.trim().removePrefix("\\uFEFF") }
                .filter { it.isNotBlank() }
                .map { it.substringBefore(',').substringBefore(';').trim().trim('"') }
                .filterNot { it.equals("MVĐ", true) || it.contains("mã vận đơn", true) || it.contains("ma van don", true) }
                .filter { it.isNotBlank() }.distinctBy { it.uppercase() }.toList()
            val existingCodes = vm.orders.map { it.code.trim().uppercase() }.toSet()
            val skippedExisting = rawCodes.count { it.uppercase() in existingCodes }
            val codes = rawCodes.filterNot { it.uppercase() in existingCodes }
            waybills = codes.joinToString("\\n")
            com.example.giaohangpro.vtman.VtmanQueueController.load(codes)
            importedCount = 0
            snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
            val suffix = if (skippedExisting > 0) " • Bỏ qua $skippedExisting mã đã có thông tin" else ""
            Toast.makeText(context, "Đã nạp ${codes.size} MVĐ từ CSV$suffix", Toast.LENGTH_LONG).show()'''
if old_csv in s:
    s=s.replace(old_csv,new_csv,1)

# Scanner: skip any code that already has order information; otherwise append one code per line.
old_scan='''            val currentCodes = waybills.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
            if (currentCodes.any { it.equals(code, ignoreCase = true) }) {
                Toast.makeText(context, "Mã $code đã được quét", Toast.LENGTH_SHORT).show()
            } else {
                val updatedCodes = currentCodes + code
                waybills = updatedCodes.joinToString("\\n")
                com.example.giaohangpro.vtman.VtmanQueueController.load(updatedCodes)
                snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                Toast.makeText(context, "Đã thêm MVĐ $code", Toast.LENGTH_SHORT).show()
            }'''
new_scan='''            val currentCodes = waybills.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
            val alreadyHasInfo = vm.orders.any { it.code.trim().equals(code, ignoreCase = true) }
            when {
                alreadyHasInfo -> Toast.makeText(context, "Mã $code đã có thông tin trong Chi tiết đơn - bỏ qua", Toast.LENGTH_LONG).show()
                currentCodes.any { it.equals(code, ignoreCase = true) } -> Toast.makeText(context, "Mã $code đã được quét", Toast.LENGTH_SHORT).show()
                else -> {
                    val updatedCodes = currentCodes + code
                    waybills = updatedCodes.joinToString("\\n")
                    com.example.giaohangpro.vtman.VtmanQueueController.load(updatedCodes)
                    importedCount = 0
                    snapshot = com.example.giaohangpro.vtman.VtmanQueueController.snapshot()
                    Toast.makeText(context, "Đã thêm MVĐ $code", Toast.LENGTH_SHORT).show()
                }
            }'''
if old_scan in s:
    s=s.replace(old_scan,new_scan,1)

p.write_text(s)
