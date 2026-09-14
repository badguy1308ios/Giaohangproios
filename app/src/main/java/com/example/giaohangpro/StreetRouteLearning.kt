package com.example.giaohangpro

import java.util.Locale
import kotlin.math.*

/** Pure, read-only route suggestions. Never mutates customers, orders or route STT. */
internal object StreetRouteLearning {
    data class Point(val lat: Double, val lng: Double) {
        fun valid() = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    }
    data class Sample(val customerId: Long, val street: String, val point: Point)
    data class Stop(val key: String, val street: String, val point: Point)
    data class Visit(val customerId: Long, val street: String, val point: Point)
    fun streetKey(name: String) = name.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun distance(a: Point, b: Point): Double {
        val p = Math.toRadians(a.lat)
        val q = Math.toRadians(b.lat)
        val h = sin((q-p)/2).pow(2) +
            cos(p)*cos(q)*sin(Math.toRadians(b.lng-a.lng)/2).pow(2)
        return 6371000.0 * 2 * atan2(sqrt(h.coerceIn(0.0,1.0)), sqrt((1-h).coerceIn(0.0,1.0)))
    }

    // Corrections to the same set of stops replace the previous lesson. Repeated Save does not add votes.
    fun rememberRoute(history: List<List<Visit>>, route: List<Visit>): List<List<Visit>> {
        if (route.size > 2000) return history
        val clean = route.toList() // keep unknown stops as separators; never invent a transition across them
        val ids = clean.filter { it.customerId > 0 && it.point.valid() && streetKey(it.street).isNotBlank() }
            .map { it.customerId }.toSet()
        if (ids.size < 2) return history
        return (history.filterNot { it.filter { v -> v.customerId > 0 && v.point.valid() && streetKey(v.street).isNotBlank() }
            .map { v -> v.customerId }.toSet() == ids } + listOf(clean))
            .takeLast(24)
    }

    class Trace internal constructor(val points: List<Point>) {
        private val lengths = points.zipWithNext { a,b -> distance(a,b) }
        val lengthMeters = lengths.sum()
        // Projection onto a curved trace, not a latitude/longitude bounding box.
        fun position(p: Point): Double {
            var best = Double.POSITIVE_INFINITY
            var along = 0.0
            var offset = 0.0
            for (i in lengths.indices) {
                val a=points[i]; val b=points[i+1]
                val scale = cos(Math.toRadians(a.lat))
                val bx=Math.toRadians(b.lng-a.lng)*scale*6371000
                val by=Math.toRadians(b.lat-a.lat)*6371000
                val px=Math.toRadians(p.lng-a.lng)*scale*6371000
                val py=Math.toRadians(p.lat-a.lat)*6371000
                val d=bx*bx+by*by
                val t=if(d>0) ((px*bx+py*by)/d).coerceIn(0.0,1.0) else 0.0
                val error=(px-t*bx).pow(2)+(py-t*by).pow(2)
                if(error<best) { best=error; along=offset+t*lengths[i] }
                offset += lengths[i]
            }
            return along
        }
    }

    fun trace(samples: List<Sample>): Trace? {
        val points=samples.map { it.point }.filter { it.valid() }.distinct()
            .sortedWith(compareBy<Point> { it.lat }.thenBy { it.lng })
        if(points.size<3) return null
        // Deterministic sampling bounds quadratic work for unusually large customer databases.
        val p=if(points.size<=160) points else (0 until 160).map { points[it*(points.size-1)/159] }
        val n=p.size
        val parent=IntArray(n){-1}; val best=DoubleArray(n){Double.POSITIVE_INFINITY}
        val used=BooleanArray(n); best[0]=0.0
        val edges=Array(n){mutableListOf<Pair<Int,Double>>()}
        repeat(n) {
            val u=(0 until n).filterNot { used[it] }.minByOrNull { best[it] }!!
            used[u]=true
            if(parent[u]>=0) {
                edges[u].add(parent[u] to best[u]); edges[parent[u]].add(u to best[u])
            }
            for(v in 0 until n) if(!used[v]) {
                val d=distance(p[u],p[v])
                if(d<best[v]) { best[v]=d; parent[v]=u }
            }
        }
        val lengths=edges.flatMap { it.map { e -> e.second } }.sorted()
        val median=lengths[lengths.size/2]
        // Disconnected/outlier samples are not evidence that a road crosses the gap.
        if(lengths.last()>max(350.0,median*6.0)) return null
        fun farthest(start:Int): Pair<Int,IntArray> {
            val prev=IntArray(n){-1}; val dist=DoubleArray(n){-1.0}
            val queue=ArrayDeque<Int>(); queue.add(start); dist[start]=0.0
            while(queue.isNotEmpty()) {
                val u=queue.removeFirst()
                edges[u].forEach { (v,w) -> if(dist[v]<0) { dist[v]=dist[u]+w; prev[v]=u; queue.add(v) } }
            }
            return (0 until n).maxByOrNull { dist[it] }!! to prev
        }
        val a=farthest(0).first
        val (b,prev)=farthest(a)
        val path=mutableListOf<Int>(); var node=b
        while(node>=0) { path.add(node); node=prev[node] }
        if(path.first()>path.last()) path.reverse()
        val result=Trace(path.map { p[it] })
        return result.takeIf { it.lengthMeters>=30.0 }
    }

    class Model(samples: List<Sample>, history: List<List<Visit>>) {
        private val validSamples=samples.filter { it.point.valid() && streetKey(it.street).isNotBlank() }
        private val byId=validSamples.associateBy { it.customerId }
        val traces=validSamples.groupBy { streetKey(it.street) }.mapNotNull { (name, group) ->
            trace(group)?.let { name to it }
        }.toMap()
        private val directions=mutableMapOf<String,Double>()
        private val transitions=mutableMapOf<Pair<String,String>,Double>()
        private val departures=mutableMapOf<String,Double>()

        init {
            history.takeLast(24).forEachIndexed { index, route ->
                val weight=0.92.pow(history.takeLast(24).lastIndex-index)
                // Votes are per route, not per parcel/customer density.
                val routeDirections=mutableMapOf<String,Double>()
                val routeTransitions=mutableSetOf<Pair<String,String>>()
                route.zipWithNext().forEach { (a,b) ->
                    fun current(v:Visit):Boolean {
                        val s=byId[v.customerId] ?: return false
                        return streetKey(s.street)==streetKey(v.street) && distance(s.point,v.point)<=5.0
                    }
                    if(current(a) && current(b)) {
                        val from=streetKey(a.street); val to=streetKey(b.street)
                        if(from==to) {
                            traces[from]?.let { t ->
                                val delta=t.position(b.point)-t.position(a.point)
                                if(abs(delta)>15) routeDirections[from]=(routeDirections[from] ?: 0.0)+sign(delta)
                            }
                        } else routeTransitions.add(from to to)
                    }
                }
                routeDirections.forEach { (name,vote) ->
                    directions[name]=(directions[name] ?: 0.0)+sign(vote)*weight
                }
                routeTransitions.forEach { edge ->
                    transitions[edge]=(transitions[edge] ?: 0.0)+weight
                    departures[edge.first]=(departures[edge.first] ?: 0.0)+weight
                }
            }
        }

        /** Metre-equivalent preference, bounded so a lesson cannot cause an arbitrarily long detour. */
        fun adjustment(origin: Point, current: Point, previous: Stop?, candidate: Stop): Double {
            val street=streetKey(candidate.street)
            val t=traces[street] ?: return 0.0
            val from=previous?.let { streetKey(it.street) }
            val directionVote=directions[street] ?: 0.0
            val learnedConfidence=abs(directionVote)/(abs(directionVote)+2.0)
            val direction=if(abs(directionVote)>0.01) sign(directionVote)
                else if(distance(origin,t.points.first())<=distance(origin,t.points.last())) 1.0 else -1.0
            val pos=t.position(candidate.point)
            var result=0.0
            if(from==street) {
                val delta=pos-t.position(current)
                result -= 20.0 // modest continuity preference
                if(delta*direction < -15.0) result += min(abs(delta),150.0)*(0.3+0.7*learnedConfidence)
            } else {
                val entry=if(direction>0) pos else t.lengthMeters-pos
                result += min(entry,180.0)*(0.15+0.6*learnedConfidence)
                if(from!=null) {
                    val count=transitions[from to street] ?: 0.0
                    val total=departures[from] ?: 0.0
                    result -= 100.0*count/(total+2.0)
                }
            }
            return result.coerceIn(-100.0,150.0)
        }

        fun order(stops: List<Stop>, origin: Point,
                  legacy: (Point,Point)->Double = { _,_ -> 0.0 }): List<String> {
            val remaining=stops.toMutableList()
            val out=mutableListOf<String>()
            var current=origin
            var previous:Stop?=null
            while(remaining.isNotEmpty()) {
                val next=remaining.minByOrNull {
                    distance(current,it.point)+legacy(current,it.point)+adjustment(origin,current,previous,it)
                }!!
                out.add(next.key); current=next.point; previous=next; remaining.remove(next)
            }
            return out
        }
    }
}
