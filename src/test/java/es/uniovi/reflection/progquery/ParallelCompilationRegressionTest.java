package es.uniovi.reflection.progquery;

import es.uniovi.reflection.progquery.ast.ASTAuxiliarStorage;
import es.uniovi.reflection.progquery.cache.DefinitionCache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/** Executable stress regression: ordering, shared bindings and failure shutdown. */
public final class ParallelCompilationRegressionTest {
    public static void main(String[] args) {
        ProgQuery.LOGGER.setLevel(java.util.logging.Level.OFF);
        for (int repeat = 0; repeat < 30; repeat++) {
            ProgQuery.createCaches(new ASTAuxiliarStorage());
            try {
                Object sharedTypes = DefinitionCache.TYPE_CACHE.get();
                ParallelCompilation session = new ParallelCompilation(24);
                List<Integer> visits = new ArrayList<>();
                CountDownLatch firstWave = new CountDownLatch(8);
                AtomicInteger visiting = new AtomicInteger();
                List<CompilationResult> results = session.run(24, 8, index -> {
                    if (DefinitionCache.TYPE_CACHE.get() != sharedTypes)
                        throw new AssertionError("Worker lost shared session bindings");
                    if (index < 8) {
                        firstWave.countDown();
                        try {
                            if (!firstWave.await(5, TimeUnit.SECONDS))
                                throw new AssertionError("Eight workers did not execute concurrently");
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    for (int event = 0; event < 3; event++)
                        session.visitGraph(index, () -> {
                            if (visiting.incrementAndGet() != 1)
                                throw new AssertionError("Concurrent graph writers");
                            visits.add(index);
                            visiting.decrementAndGet();
                        });
                    return new CompilationResult(Integer.toString(index));
                });
                List<Integer> expected = IntStream.range(0, 24)
                        .boxed().flatMap(i -> java.util.stream.Stream.of(i, i, i)).toList();
                if (!visits.equals(expected) || results.size() != 24)
                    throw new AssertionError("Non-deterministic graph or result order");
                for (int i = 0; i < results.size(); i++)
                    if (!results.get(i).getJavacOptions().equals(Integer.toString(i)))
                        throw new AssertionError("Result order changed");

                ParallelCompilation broken = new ParallelCompilation(24);
                AtomicInteger active = new AtomicInteger();
                try {
                    broken.run(24, 8, index -> {
                        active.incrementAndGet();
                        try {
                            if (index == 3)
                                throw new IllegalArgumentException("test failure");
                            broken.visitGraph(index, () -> {});
                            return new CompilationResult("");
                        } finally {
                            active.decrementAndGet();
                        }
                    });
                    throw new AssertionError("Worker failure was swallowed");
                } catch (IllegalStateException expectedFailure) {
                    if (!(expectedFailure.getCause() instanceof IllegalArgumentException)
                            || !"test failure".equals(expectedFailure.getCause().getMessage()))
                        throw new AssertionError("Original worker failure was lost", expectedFailure);
                    if (active.get() != 0)
                        throw new AssertionError("Workers still mutate state after failure");
                }
                if (DefinitionCache.TYPE_CACHE.get() != sharedTypes)
                    throw new AssertionError("Coordinator bindings were removed");

                ParallelCompilation early = new ParallelCompilation(2);
                CountDownLatch secondGraphDone = new CountDownLatch(1);
                early.run(2, 2, index -> {
                    early.visitGraph(index, () -> {
                        if (index == 0)
                            early.complete(index);
                        else
                            secondGraphDone.countDown();
                    });
                    if (index == 0) {
                        try {
                            if (!secondGraphDone.await(5, TimeUnit.SECONDS))
                                throw new AssertionError("Generation prevented the next graph visit");
                        } catch (InterruptedException exception) {
                            throw new RuntimeException(exception);
                        }
                    }
                    return new CompilationResult("");
                });

                ParallelCompilation emptyTasks = new ParallelCompilation(24);
                List<Integer> nonemptyVisits = new ArrayList<>();
                emptyTasks.run(24, 8, index -> {
                    if (index % 2 == 0)
                        emptyTasks.visitGraph(index, () -> nonemptyVisits.add(index));
                    return new CompilationResult("");
                });
                if (!nonemptyVisits.equals(IntStream.range(0, 24).filter(i -> i % 2 == 0).boxed().toList()))
                    throw new AssertionError("Empty compilations blocked graph progress");
            } finally {
                ProgQuery.resetCaches();
            }
        }
        System.out.println("Passed 30 stress runs: eight-worker ordering, bindings, failure shutdown, early release and empty tasks.");
    }
}
