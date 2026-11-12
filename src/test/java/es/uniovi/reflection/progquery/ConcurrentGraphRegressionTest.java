package es.uniovi.reflection.progquery;

import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.insertion.lazy.InfoToInsert;
import es.uniovi.reflection.progquery.database.nodes.NodeTypes;
import es.uniovi.reflection.progquery.database.relations.ASTRelationTypes;
import es.uniovi.reflection.progquery.node_wrappers.Neo4jLazyNode;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable stress regression: actual simultaneous visitors and replacement during edge writes. */
public final class ConcurrentGraphRegressionTest {
    public static void main(String[] args) {
        ProgQuery.LOGGER.setLevel(java.util.logging.Level.OFF);
        for(int repeat=0;repeat<30;repeat++){
            ProgQuery.createCaches(new ASTAuxiliarStorage());
            try {
                Object types=DefinitionCache.TYPE_CACHE.get();
                ParallelCompilation session=new ParallelCompilation(24, true);
                CountDownLatch visitors=new CountDownLatch(8);
                AtomicInteger calls=new AtomicInteger();
                List<CompilationResult> results=session.run(24,8,index->{
                    if(DefinitionCache.TYPE_CACHE.get()!=types)throw new AssertionError("Lost shared bindings");
                    session.visitGraph(index,()->{
                        if(index<8){visitors.countDown();await(visitors);}
                        calls.incrementAndGet();
                    });
                    return new CompilationResult(Integer.toString(index));
                });
                check(session.peakVisitors()==8,"Graph visits were serialized");
                check(calls.get()==24,"Lost graph visit");
                for(int i=0;i<24;i++)check(results.get(i).getJavacOptions().equals(Integer.toString(i)),"Result order changed");

                AtomicInteger active=new AtomicInteger();
                try {
                    new ParallelCompilation(24, true).run(24,8,index->{
                        active.incrementAndGet();
                        try {if(index==3)throw new IllegalArgumentException("test failure");return new CompilationResult("");}
                        finally {active.decrementAndGet();}
                    });
                    throw new AssertionError("Failure swallowed");
                } catch(IllegalStateException expected){
                    check(expected.getCause() instanceof IllegalArgumentException,"Original failure lost");
                    check(active.get()==0,"Workers active after failure");
                }
                check(DefinitionCache.TYPE_CACHE.get()==types,"Coordinator binding removed");
            } finally {ProgQuery.resetCaches();}
        }
        for(int repeat=0;repeat<10;repeat++)exerciseGraphReplacement();
        System.out.println("Passed 30 concurrent visitor stress runs and 10 graph replacement/edge writer stress runs.");
    }
    private static void exerciseGraphReplacement(){
        ProgQuery.createCaches(new ASTAuxiliarStorage());
        try {
            var pending=InfoToInsert.INFO_TO_INSERT.get();
            var old=new Neo4jLazyNode(NodeTypes.CLASS_DEC);
            var next=new Neo4jLazyNode(NodeTypes.CLASS_DEC);
            DefinitionCache<String> cache=new DefinitionCache<>();cache.put("class",old);
            old.createRelationshipTo(old,ASTRelationTypes.DECLARES_TYPE);
            CountDownLatch ready=new CountDownLatch(8);
            new ParallelCompilation(8, true).run(8,8,index->{
                ready.countDown();await(ready);
                for(int i=0;i<300;i++){
                    var local=new Neo4jLazyNode(NodeTypes.FIELD_DEC);
                    var outgoing=old.createRelationshipTo(local,ASTRelationTypes.DECLARES_FIELD);
                    outgoing.setProperty("index",i);
                    local.createRelationshipTo(old,ASTRelationTypes.DECLARES_TYPE);
                    if(index==0&&i==100)cache.putDefinition("class",next);
                    if(i%3==0){outgoing.delete();outgoing.delete();}
                }
                return new CompilationResult("");
            });
            check(pending.getNodeSet().size()==2401,"Lost or duplicated nodes");
            check(pending.getRelSet().size()==4001,"Lost or duplicated edges");
            for(var rel:pending.getRelSet()){
                check(rel.getStartNode()!=old&&rel.getEndNode()!=old,"Edge points to replaced declaration");
                check(rel.getStartNode().getRelationships().contains(rel),"Missing source adjacency");
                check(rel.getEndNode().getRelationships().contains(rel),"Missing target adjacency");
            }
            old.setProperty("late",true);
            check(Boolean.TRUE.equals(next.getProperty("late")),"Late reference was not redirected");
        } finally {ProgQuery.resetCaches();}
    }
    private static void await(CountDownLatch latch){
        try {if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Concurrent visitors did not enter");}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
