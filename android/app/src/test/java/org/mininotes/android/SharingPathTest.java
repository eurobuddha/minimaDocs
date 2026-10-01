// SPDX-License-Identifier: GPL-3.0-or-later
// Mininotes is free software: GNU General Public License, version 3 or later. See LICENSE.
package org.mininotes.android;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/** A rule reaches everything under the thing it is set on, however deep; and nothing beside it. */
public class SharingPathTest {
    private static final String FRIEND="Mx-friend", TABLET="Mx-tablet";
    private static Sharing.Rule rule(Sharing.Scope scope,String target,String address) {
        return new Sharing.Rule(scope,target,address,Sharing.Level.READ,1L,"");
    }

    @Test public void aRuleOnACollectionReachesEverythingInsideItAtAnyDepth() {
        List<Sharing.Rule> rules=List.of(rule(Sharing.Scope.THING,"deep",FRIEND));
        assertTrue(Sharing.audience(rules,List.of("work","deep","deeper","deepest","note")).containsKey(FRIEND));
        assertTrue(Sharing.audience(rules,List.of("work","deep")).containsKey(FRIEND));
        assertFalse("the collection above it",Sharing.audience(rules,List.of("work")).containsKey(FRIEND));
        assertFalse("beside it",Sharing.audience(rules,List.of("work","other","note")).containsKey(FRIEND));
    }

    /** A rule set on a book before books were collections goes on reaching what is inside that collection. */
    @Test public void aRuleFromBeforeKeepsItsReachWhateverItsScopeSays() {
        List<Sharing.Rule> rules=List.of(rule(Sharing.Scope.BOOK,"meetings",FRIEND),rule(Sharing.Scope.COLLECTION,"work",TABLET));
        assertEquals(2,Sharing.audience(rules,List.of("work","meetings","monday")).size());
        assertEquals(2,Sharing.audience(rules,List.of("work","meetings","sub","deeper-note")).size());
        assertEquals(List.of(TABLET),List.copyOf(Sharing.audience(rules,List.of("work","other","n")).keySet()));
        // And the three-level forms say exactly what the path does.
        assertEquals(Sharing.audience(rules,List.of("work","meetings","monday")),Sharing.audience(rules,"work","meetings","monday"));
    }

    @Test public void theLibraryRuleReachesEverythingAndAnEmptyTargetNothing() {
        List<Sharing.Rule> rules=List.of(rule(Sharing.Scope.LIBRARY,Sharing.EVERYTHING,TABLET),rule(Sharing.Scope.THING,"",FRIEND));
        assertEquals(List.of(TABLET),List.copyOf(Sharing.audience(rules,List.of("loose")).keySet()));
    }

    @Test public void standingIsTheMostAnyRuleAlongThePathSays() {
        List<Sharing.Rule> rules=List.of(
            new Sharing.Rule(Sharing.Scope.THING,"work",FRIEND,Sharing.Level.READ,1L,"k"),
            new Sharing.Rule(Sharing.Scope.THING,"deep",FRIEND,Sharing.Level.WRITE,2L,"k"),
            new Sharing.Rule(Sharing.Scope.PAGE,"elsewhere",FRIEND,Sharing.Level.ADMIN,3L,"k"));
        assertEquals(Sharing.Level.WRITE,Sharing.standing(rules,List.of("work","deep","n"),List.of(FRIEND),"k").level);
        assertEquals(Sharing.Level.READ,Sharing.standing(rules,List.of("work","n"),List.of("Mx-moved"),"k").level);
        assertNull(Sharing.standing(rules,List.of("home-note"),List.of(FRIEND),"k"));
    }

    @Test public void movingDeepSaysWhoStartsAndWhoStops() {
        List<Sharing.Rule> rules=List.of(rule(Sharing.Scope.THING,"deep",FRIEND),rule(Sharing.Scope.THING,"moved",TABLET));
        Sharing.Change into=Sharing.moving(rules,List.of("work","moved"),List.of("work","deep","deeper","moved"));
        assertEquals(List.of(FRIEND),List.copyOf(into.gained.keySet()));
        assertTrue("its own rule follows it",into.lost.isEmpty());
        Sharing.Change out=Sharing.moving(rules,List.of("work","deep","moved"),List.of("moved"));
        assertEquals(List.of(FRIEND),List.copyOf(out.lost.keySet()));
    }

    @Test public void everyKindOfCollectionIsSaidAsACollection() {
        for(Sharing.Scope scope:new Sharing.Scope[]{Sharing.Scope.COLLECTION,Sharing.Scope.BOOK,Sharing.Scope.THING}) {
            assertEquals("Recipes collection",Sharing.shortly(scope,"Recipes"));
            assertEquals("the collection Recipes",Sharing.travelling(scope,"Recipes"));
            assertEquals("the collection Recipes, and everything in it",Sharing.describe(scope,"Recipes"));
        }
        assertEquals("every collection and note",Sharing.describe(Sharing.Scope.LIBRARY,""));
    }
}
