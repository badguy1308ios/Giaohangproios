package com.example.giaohangpro

import org.junit.Assert.*
import org.junit.Test
import com.example.giaohangpro.StreetRouteLearning.Point
import com.example.giaohangpro.StreetRouteLearning.Sample
import com.example.giaohangpro.StreetRouteLearning.Stop
import com.example.giaohangpro.StreetRouteLearning.Visit

class StreetRouteLearningTest {
    private fun p(x: Double, y: Double = 0.0) = Point(10.0+y/111000.0, 107.0+x/109300.0)
    private fun samples() = (0..4).map { Sample(it+1L, "N25", p(it*50.0)) }
    private fun visits(s:List<Sample>) = s.map { Visit(it.customerId,it.street,it.point) }

    @Test fun insufficientSamplesUseLegacyDistanceOrder() {
        val s=samples().take(2)
        assertNull(StreetRouteLearning.trace(s))
        val model=StreetRouteLearning.Model(s,emptyList())
        val stops=listOf(Stop("far","N25",p(150.0)),Stop("near","N25",p(20.0)))
        assertEquals(listOf("near","far"),model.order(stops,p(0.0)))
        assertEquals(listOf("far","near"),model.order(stops,p(0.0)){_,to -> if(to==p(150.0)) -500.0 else 0.0})
    }

    @Test fun curvedTraceUsesPathLengthInsteadOfEndpointDistance() {
        val s=listOf(p(0.0),p(40.0),p(80.0),p(80.0,40.0),p(80.0,80.0))
            .mapIndexed { i,point -> Sample(i+1L,"N25",point) }
        val trace=StreetRouteLearning.trace(s)!!
        assertTrue(trace.lengthMeters > StreetRouteLearning.distance(trace.points.first(),trace.points.last())+20.0)
        assertEquals(0.0,trace.position(trace.points.first()),0.1)
        assertEquals(trace.lengthMeters,trace.position(trace.points.last()),0.1)
    }

    @Test fun isolatedCoordinateDoesNotBecomeRoadEnd() {
        val s=samples()+Sample(99L,"N25",p(10000.0))
        assertNull(StreetRouteLearning.trace(s))
    }

    @Test fun invalidAndDuplicateCoordinatesAreNotExtraEvidence() {
        val s=listOf(Sample(1,"N25",p(0.0)),Sample(2,"N25",p(0.0)),Sample(3,"N25",Point(Double.NaN,107.0)))
        assertNull(StreetRouteLearning.trace(s))
    }

    @Test fun repeatedSaveAndCorrectionReplaceLesson() {
        val v=visits(samples())
        val once=StreetRouteLearning.rememberRoute(emptyList(),v)
        assertEquals(once,StreetRouteLearning.rememberRoute(once,v))
        val corrected=StreetRouteLearning.rememberRoute(once,v.reversed())
        assertEquals(1,corrected.size)
        assertEquals(v.reversed(),corrected.single())
    }

    @Test fun confirmedDirectionChangesSuggestionForNewCustomer() {
        val s=samples()
        val model=StreetRouteLearning.Model(s,listOf(visits(s.reversed())))
        val first=Stop("first","N25",p(100.0))
        val lower=Stop("newLower","N25",p(80.0))
        val higher=Stop("newHigher","N25",p(120.0))
        assertTrue(model.adjustment(p(100.0),first.point,first,lower) <
            model.adjustment(p(100.0),first.point,first,higher))
        val order=model.order(listOf(first,higher,lower),p(100.0))
        assertEquals(listOf("first","newLower","newHigher"),order)
    }

    @Test fun editedLabelAndMovedCoordinateInvalidateOldLesson() {
        val s=samples()
        val old=listOf(visits(s.reversed()))
        val changed=s.map { it.copy(street="D39") }
        val a=StreetRouteLearning.Model(changed,old)
        val b=StreetRouteLearning.Model(changed,emptyList())
        val prev=Stop("p","D39",p(100.0)); val candidate=Stop("n","D39",p(50.0))
        assertEquals(b.adjustment(p(0.0),prev.point,prev,candidate),a.adjustment(p(0.0),prev.point,prev,candidate),0.001)
        val moved=s.map { it.copy(point=p((it.customerId-1)*50.0,30.0)) }
        val c=StreetRouteLearning.Model(moved,old)
        val d=StreetRouteLearning.Model(moved,emptyList())
        val mprev=Stop("p","N25",p(100.0,30.0)); val mc=Stop("c","N25",p(50.0,30.0))
        assertEquals(d.adjustment(p(0.0),mprev.point,mprev,mc),c.adjustment(p(0.0),mprev.point,mprev,mc),0.001)
    }

    @Test fun missingCustomersDoNotTeachDirection() {
        val s=samples()
        val unrelated=listOf(visits(s).map { it.copy(customerId=it.customerId+100) })
        val learned=StreetRouteLearning.Model(s,unrelated)
        val fresh=StreetRouteLearning.Model(s,emptyList())
        val prev=Stop("p","N25",p(100.0)); val next=Stop("n","N25",p(50.0))
        assertEquals(fresh.adjustment(p(0.0),prev.point,prev,next),learned.adjustment(p(0.0),prev.point,prev,next),0.001)
    }

    @Test fun unknownStopsDoNotInventStreetTransitions() {
        val a=samples()
        val b=samples().map { it.copy(customerId=it.customerId+10,street="D39",point=p((it.customerId-1)*50.0,100.0)) }
        val lesson=listOf(visits(a).first(),Visit(0,"",p(0.0)),visits(b).first())
        val h=StreetRouteLearning.rememberRoute(emptyList(),lesson)
        assertEquals(3,h.single().size)
        val withGap=StreetRouteLearning.Model(a+b,h)
        val fresh=StreetRouteLearning.Model(a+b,emptyList())
        val prev=Stop("p","N25",p(0.0)); val next=Stop("n","D39",p(0.0,100.0))
        assertEquals(fresh.adjustment(p(0.0),prev.point,prev,next),withGap.adjustment(p(0.0),prev.point,prev,next),0.001)
    }

    @Test fun knownTransitionSoftlyPrefersConfirmedNextStreet() {
        val a=samples()
        val b=samples().map { it.copy(customerId=it.customerId+10,street="D39",point=p((it.customerId-1)*50.0,100.0)) }
        val route=visits(a)+visits(b)
        val learned=StreetRouteLearning.Model(a+b,listOf(route))
        val fresh=StreetRouteLearning.Model(a+b,emptyList())
        val prev=Stop("p","N25",p(200.0)); val next=Stop("n","D39",p(0.0,100.0))
        assertTrue(learned.adjustment(p(0.0),prev.point,prev,next)<fresh.adjustment(p(0.0),prev.point,prev,next))
    }

    @Test fun allStopsPreservedAndInputsNotMutated() {
        val s=samples()
        val stops=listOf(Stop("a","N25",p(0.0)),Stop("b","N25",p(100.0)),Stop("unknown","",p(30.0)))
        val original=stops.toList()
        val model=StreetRouteLearning.Model(s,listOf(visits(s)))
        val out=model.order(stops,p(0.0))
        assertEquals(original,stops)
        assertEquals(stops.map { it.key }.toSet(),out.toSet())
        assertEquals(stops.size,out.size)
        assertEquals(emptyList<String>(),model.order(emptyList(),p(0.0)))
    }

    @Test fun learningCannotForceKilometreDetour() {
        val s=samples()
        val model=StreetRouteLearning.Model(s,List(24){visits(s)})
        val stops=listOf(Stop("far","N25",p(10000.0)),Stop("near","",p(10.0)))
        assertEquals("near",model.order(stops,p(0.0)).first())
        assertEquals("n25",StreetRouteLearning.streetKey(" N25 "))
    }
}
