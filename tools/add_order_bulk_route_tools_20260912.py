from pathlib import Path
p=Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s=p.read_text()

s=s.replace('Tab.MAP -> MapScreen(vm.orders, vm.customers)','Tab.MAP -> MapScreen(vm)',1)

# Wrap existing map with route tools, preserving current map implementation.
old='fun MapScreen(orders: List<Order>, customers: List<Customer>) {'
if old in s and 'fun MapScreen(vm: MainViewModel)' not in s:
    s=s.replace(old,'private fun BaseMapScreen(orders: List<Order>, customers: List<Customer>) {',1)
    pos=s.index('@Composable\nprivate fun BaseMapScreen')
    wrapper=r'''@Composable
fun MapScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmSave by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(vm.orders.map { it.code }) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val csv = buildString { append("STT,MVĐ\n"); vm.orders.forEachIndexed { i,o -> append("${i+1},${csvCell(o.code)}\n") } }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(csv) }
        }.onSuccess { Toast.makeText(context,"Đã xuất thứ tự ${vm.orders.size} MVĐ",Toast.LENGTH_SHORT).show() }
         .onFailure { Toast.makeText(context,"Không xuất được STT",Toast.LENGTH_LONG).show() }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            val text=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val codes=text.lineSequence().drop(1).mapNotNull { line -> line.substringAfter(',',"").trim().trim('"').takeIf(String::isNotBlank) }.toList()
            vm.reorderOrders(codes)
            Toast.makeText(context,"Đã nhập STT cho ${codes.size} MVĐ",Toast.LENGTH_SHORT).show()
        }.onFailure { Toast.makeText(context,"Không đọc được file STT",Toast.LENGTH_LONG).show() }
    }
    Box(Modifier.fillMaxSize()) {
        BaseMapScreen(vm.orders, vm.customers)
        FloatingActionButton(
            onClick = { if(editing) confirmSave=true else menu=true },
            modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(44.dp),
            containerColor = Orange
        ) { Icon(if(editing) Icons.Default.Save else Icons.Default.KeyboardArrowUp, if(editing) "Lưu tuyến" else "Công cụ tuyến", tint=Color.White) }
        DropdownMenu(expanded=menu,onDismissRequest={menu=false},modifier=Modifier.align(Alignment.TopEnd)) {
            DropdownMenuItem(text={Text("Tạo tuyến")},leadingIcon={Icon(Icons.Default.Route,null)},onClick={
                menu=false
                val sorted=vm.orders.sortedWith(compareBy<Order> { it.latitude.toDoubleOrNull() ?: 999.0 }.thenBy { it.longitude.toDoubleOrNull() ?: 999.0 }).map { it.code }
                vm.reorderOrders(sorted); Toast.makeText(context,"Đã tạo tuyến theo vị trí",Toast.LENGTH_SHORT).show()
            })
            DropdownMenuItem(text={Text("Sửa tuyến")},leadingIcon={Icon(Icons.Default.Edit,null)},onClick={menu=false;draft=vm.orders.map{it.code};editing=true})
            DropdownMenuItem(text={Text("Xuất STT")},leadingIcon={Icon(Icons.Default.FileDownload,null)},onClick={menu=false;exportLauncher.launch("giaohangpro_thu_tu_mvd.csv")})
            DropdownMenuItem(text={Text("Nhập STT")},leadingIcon={Icon(Icons.Default.FileOpen,null)},onClick={menu=false;importLauncher.launch("text/*")})
        }
    }
    if(editing) Dialog(onDismissRequest={editing=false}) {
        Card(Modifier.fillMaxWidth().heightIn(max=560.dp),colors=CardDefaults.cardColors(containerColor=Color.White)) {
            Column(Modifier.padding(10.dp)) {
                Text("SỬA TUYẾN",fontWeight=FontWeight.Bold,fontSize=16.sp)
                Spacer(Modifier.height(6.dp))
                LazyColumn(Modifier.weight(1f,false)) {
                    itemsIndexed(draft) { index, code ->
                        Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text("${index+1}.",Modifier.width(34.dp),fontWeight=FontWeight.Bold)
                            Text(code,Modifier.weight(1f))
                            IconButton(onClick={ if(index>0){ val m=draft.toMutableList(); val x=m[index-1];m[index-1]=m[index];m[index]=x;draft=m } }){Icon(Icons.Default.KeyboardArrowUp,"Lên")}
                            IconButton(onClick={ if(index<draft.lastIndex){ val m=draft.toMutableList(); val x=m[index+1];m[index+1]=m[index];m[index]=x;draft=m } }){Icon(Icons.Default.KeyboardArrowDown,"Xuống")}
                        }
                    }
                }
                Text("Bấm nút Lưu ở góc phải để lưu tuyến.",fontSize=11.sp,color=TextGray)
            }
        }
    }
    if(confirmSave) AlertDialog(onDismissRequest={confirmSave=false},title={Text("Lưu tuyến")},text={Text("Xác nhận lưu thứ tự tuyến hiện tại?")},confirmButton={TextButton(onClick={vm.reorderOrders(draft);confirmSave=false;editing=false}){Text("LƯU")}},dismissButton={TextButton(onClick={confirmSave=false}){Text("HỦY")}})
}

'''
    s=s[:pos]+wrapper+s[pos:]

# Replace order list with CSV import + bulk delete selection.
start=s.index('@Composable\nfun OrderListScreen(')
end=s.index('\n@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)',start)
orders=r'''@Composable
fun OrderListScreen(
    vm: MainViewModel,
    focusOrderCode: String? = null,
    onFocusConsumed: () -> Unit = {},
    onCustomerClick: (Order) -> Unit
) {
    val context=LocalContext.current
    val orders=vm.orders
    val listState=androidx.compose.foundation.lazy.rememberLazyListState()
    var keyword by remember{mutableStateOf("")}
    var showTools by remember{mutableStateOf(false)}
    var editPicker by remember{mutableStateOf(false)}
    var editOrder by remember{mutableStateOf<Order?>(null)}
    var deleteMode by remember{mutableStateOf(false)}
    var selected by remember{mutableStateOf(setOf<String>())}
    var confirmDelete by remember{mutableStateOf(false)}
    val scanLauncher=rememberLauncherForActivityResult(ScanContract()){r->r.contents?.trim()?.takeIf{it.isNotEmpty()}?.let{keyword=it}}
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->
        uri?:return@rememberLauncherForActivityResult
        runCatching{val text=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}.orEmpty();val n=vm.importOrdersCsv(text);Toast.makeText(context,"Đã nhập $n đơn từ CSV",Toast.LENGTH_SHORT).show()}.onFailure{Toast.makeText(context,"Không đọc được CSV",Toast.LENGTH_LONG).show()}
    }
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->uri?:return@rememberLauncherForActivityResult;runCatching{val csv=buildString{append("MVĐ,Shop,SĐT,Tên khách,COD,Địa chỉ,Hàng hóa,Trạng thái,Dịch vụ\n");orders.forEach{o->append(listOf(o.code,o.shop,o.phone,o.customer,o.amount,o.address,o.item,o.status,o.tags.joinToString(" ")).joinToString(","){csvCell(it)}).append('\n')}};context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(csv)}}}
    val filtered=remember(orders,keyword){val q=keyword.trim();if(q.isBlank())orders else orders.filter{it.code.contains(q,true)||it.customer.contains(q,true)||it.phone.contains(q,true)||it.address.contains(q,true)}}
    LaunchedEffect(focusOrderCode,filtered){val c=focusOrderCode?:return@LaunchedEffect;val i=filtered.indexOfFirst{it.code==c};if(i>=0)listState.scrollToItem(i);onFocusConsumed()}
    Box(Modifier.fillMaxSize()){
        Column(Modifier.fillMaxSize().padding(horizontal=6.dp)){
            Spacer(Modifier.height(6.dp));SearchBox(keyword,{keyword=it},{keyword=""}){scanLauncher.launch(ScanOptions().apply{setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES);setCaptureActivity(PortraitCaptureActivity::class.java);setOrientationLocked(true)})}
            Spacer(Modifier.height(6.dp))
            if(deleteMode){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=filtered.isNotEmpty()&&selected.size==filtered.size,onCheckedChange={all->selected=if(all)filtered.map{it.code}.toSet() else emptySet()})
                    Text("Chọn tất cả",Modifier.weight(1f),fontWeight=FontWeight.Bold)
                    Button(onClick={if(selected.isNotEmpty())confirmDelete=true},enabled=selected.isNotEmpty()){Icon(Icons.Default.Delete,null);Spacer(Modifier.width(4.dp));Text("XÓA (${selected.size})")}
                }
            } else Text("Tổng số: ${filtered.size} đơn",color=Navy,fontSize=20.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(state=listState,verticalArrangement=Arrangement.spacedBy(6.dp),contentPadding=PaddingValues(bottom=62.dp)){
                itemsIndexed(filtered){index,o->
                    Row(verticalAlignment=Alignment.Top){
                        if(deleteMode)Checkbox(checked=o.code in selected,onCheckedChange={ck->selected=if(ck)selected+o.code else selected-o.code})
                        Box(Modifier.weight(1f)){OrderCard(index+1,o,onCustomerClick)}
                    }
                }
            }
        }
        FloatingActionButton(onClick={showTools=true},modifier=Modifier.align(Alignment.BottomStart).padding(10.dp).size(44.dp),containerColor=Orange){Icon(Icons.Default.Edit,"Công cụ đơn",tint=Color.White)}
        DropdownMenu(expanded=showTools,onDismissRequest={showTools=false},modifier=Modifier.align(Alignment.BottomStart)){
            DropdownMenuItem(text={Text("Nhập danh sách đơn")},leadingIcon={Icon(Icons.Default.FileOpen,null)},onClick={showTools=false;importLauncher.launch("text/*")})
            DropdownMenuItem(text={Text("Xuất danh sách đơn")},leadingIcon={Icon(Icons.Default.FileDownload,null)},onClick={showTools=false;exportLauncher.launch("giaohangpro_orders.csv")})
            DropdownMenuItem(text={Text("Sửa đơn hàng")},leadingIcon={Icon(Icons.Default.Edit,null)},onClick={showTools=false;editPicker=true})
            DropdownMenuItem(text={Text("Xóa đơn hàng")},leadingIcon={Icon(Icons.Default.Delete,null)},onClick={showTools=false;selected=emptySet();deleteMode=true})
        }
    }
    if(editPicker)AlertDialog(onDismissRequest={editPicker=false},title={Text("Chọn đơn cần sửa")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){filtered.forEach{o->Row(Modifier.fillMaxWidth().clickable{editOrder=o;editPicker=false}.padding(10.dp)){Text(o.code,Modifier.weight(1f));Text(o.customer,fontSize=11.sp,color=TextGray)}}}},confirmButton={},dismissButton={TextButton(onClick={editPicker=false}){Text("ĐÓNG")}})
    editOrder?.let{original->var customer by remember(original.code){mutableStateOf(original.customer)};var phone by remember(original.code){mutableStateOf(original.phone)};var address by remember(original.code){mutableStateOf(original.address)};var amount by remember(original.code){mutableStateOf(original.amount)};AlertDialog(onDismissRequest={editOrder=null},title={Text("Sửa ${original.code}")},text={Column{OutlinedTextField(customer,{customer=it},label={Text("Tên khách")});OutlinedTextField(phone,{phone=it},label={Text("SĐT")});OutlinedTextField(address,{address=it},label={Text("Địa chỉ")});OutlinedTextField(amount,{amount=it},label={Text("COD")})}},confirmButton={TextButton(onClick={vm.updateOrder(original.copy(customer=customer,phone=phone,address=address,amount=amount));editOrder=null}){Text("LƯU")}},dismissButton={TextButton(onClick={editOrder=null}){Text("HỦY")}})}
    if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Xóa đơn hàng")},text={Text("Xác nhận xóa ${selected.size} đơn đã chọn?")},confirmButton={TextButton(onClick={vm.deleteOrders(selected);selected=emptySet();confirmDelete=false;deleteMode=false}){Text("XÓA",color=Color(0xFFE21B1B))}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("HỦY")}})
}
'''
s=s[:start]+orders+s[end:]

# ViewModel helpers
marker='    fun updateOrder(updated: Order) {'
pos=s.index(marker)
helpers=r'''    fun importOrdersCsv(text: String): Int {
        val lines=text.lineSequence().filter{it.isNotBlank()}.toList(); if(lines.isEmpty()) return 0
        fun cells(line:String):List<String>{ val out=mutableListOf<String>();val b=StringBuilder();var q=false;var i=0;while(i<line.length){val c=line[i];if(c=='"'){if(q&&i+1<line.length&&line[i+1]=='"'){b.append('"');i++}else q=!q}else if(c==','&&!q){out+=b.toString().trim();b.setLength(0)}else b.append(c);i++};out+=b.toString().trim();return out }
        val h=cells(lines.first()).map{java.text.Normalizer.normalize(it.lowercase(),java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").replace("đ","d")}
        fun idx(vararg keys:String)=h.indexOfFirst{x->keys.any{x.contains(it)}}
        val ic=idx("mvd","ma van don"); if(ic<0)return 0
        val ishop=idx("shop");val ip=idx("sdt","so dien thoai");val iname=idx("ten khach");val icod=idx("cod");val ia=idx("dia chi");val ii=idx("hang hoa");val ist=idx("trang thai");val isv=idx("dich vu")
        var n=0
        lines.drop(1).forEach{line->val c=cells(line);fun g(i:Int)=if(i>=0&&i<c.size)c[i]else"";val code=g(ic).trim();if(code.isNotBlank()){val o=Order(code,g(iname),g(ip),g(ia),g(ii),g(icod),g(isv).split(' ',';',',','|').map(String::trim).filter(String::isNotBlank),shop=g(ishop),status=g(ist).ifBlank{"Chưa giao"});val k=orderState.indexOfFirst{it.code.equals(code,true)};if(k>=0)orderState[k]=o else orderState.add(o);n++}}
        if(n>0)savePersistentData();return n
    }
    fun deleteOrders(codes:Set<String>){ if(orderState.removeAll{it.code in codes})savePersistentData() }
    fun reorderOrders(codes:List<String>){ val rank=codes.withIndex().associate{it.value to it.index};val old=orderState.toList();orderState.clear();orderState.addAll(old.sortedWith(compareBy<Order>{rank[it.code]?:Int.MAX_VALUE}.thenBy{old.indexOf(it)}));savePersistentData() }

'''
if 'fun importOrdersCsv(text: String)' not in s:s=s[:pos]+helpers+s[pos:]

# Customer backup UI: remove instructional text while keeping progress/status.
s=s.replace('                    Text("Định dạng ZIP tương thích cấu trúc: manifest.json + customers.json + photos/customer_gate/.", color = TextGray, fontSize = 12.sp)\n                    Text("Đối chiếu theo SĐT. Khách đã có sẽ được gộp thêm tên phụ, SĐT phụ, địa chỉ, tọa độ và ảnh cổng mới; dữ liệu trống không ghi đè dữ liệu cũ.", color = TextGray, fontSize = 12.sp)\n','')
p.write_text(s)
