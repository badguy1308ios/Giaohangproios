from pathlib import Path

p = Path('app/src/main/java/com/example/giaohangpro/MainActivity.kt')
s = p.read_text()

needle = '''class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("giaohangpro_persistent_data_v1", android.content.Context.MODE_PRIVATE)
'''
if needle not in s:
    raise SystemExit('MainViewModel prefs anchor not found')

if 'fun routeAnchorPoint()' not in s:
    addition = '''    var routeNumberingEnabled by mutableStateOf(prefs.getBoolean("route_numbering_enabled_v1", false))
        private set
    private var routeAnchorLat by mutableStateOf(prefs.getString("route_anchor_lat_v1", "").orEmpty())
    private var routeAnchorLng by mutableStateOf(prefs.getString("route_anchor_lng_v1", "").orEmpty())

    fun routeAnchorPoint(): MapPoint? {
        routeAnchorLat = prefs.getString("route_anchor_lat_v1", routeAnchorLat).orEmpty()
        routeAnchorLng = prefs.getString("route_anchor_lng_v1", routeAnchorLng).orEmpty()
        return pointFromStrings(routeAnchorLat, routeAnchorLng)
    }

    fun setRouteAnchor(point: MapPoint) {
        routeAnchorLat = "%.6f".format(java.util.Locale.US, point.latitude)
        routeAnchorLng = "%.6f".format(java.util.Locale.US, point.longitude)
        prefs.edit().putString("route_anchor_lat_v1", routeAnchorLat).putString("route_anchor_lng_v1", routeAnchorLng).apply()
    }

    fun enableRouteNumbering() {
        routeNumberingEnabled = true
        prefs.edit().putBoolean("route_numbering_enabled_v1", true).apply()
    }

    fun learnRoutePattern(points: List<MapPoint>) {
        if (points.size < 2) return
        val obj = runCatching { JSONObject(prefs.getString("route_learning_v1", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val scale = 500.0
        fun cell(p: MapPoint) = "${kotlin.math.floor(p.latitude * scale).toInt()},${kotlin.math.floor(p.longitude * scale).toInt()}"
        for (i in 0 until points.lastIndex) {
            val key = "${cell(points[i])}>${cell(points[i + 1])}"
            obj.put(key, obj.optDouble(key, 0.0) + 1.0)
        }
        prefs.edit().putString("route_learning_v1", obj.toString()).apply()
    }

    fun learnedRouteWeight(from: MapPoint, to: MapPoint): Double {
        val obj = runCatching { JSONObject(prefs.getString("route_learning_v1", "{}") ?: "{}") }.getOrElse { JSONObject() }
        val scale = 500.0
        fun cell(p: MapPoint) = "${kotlin.math.floor(p.latitude * scale).toInt()},${kotlin.math.floor(p.longitude * scale).toInt()}"
        return obj.optDouble("${cell(from)}>${cell(to)}", 0.0)
    }

'''
    s = s.replace(needle, needle + addition, 1)

p.write_text(s)
print('route ViewModel state/methods ensured v2')
