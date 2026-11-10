package es.uniovi.reflection.progquery;

import es.uniovi.reflection.progquery.cache.DefinitionCache;
import es.uniovi.reflection.progquery.database.DatabaseFacade;
import es.uniovi.reflection.progquery.database.insertion.lazy.InfoToInsert;
import es.uniovi.reflection.progquery.pg.PackageManager;
import es.uniovi.reflection.progquery.typeInfo.keys.CallableKey;
import es.uniovi.reflection.progquery.typeInfo.keys.ComponentKey;
import es.uniovi.reflection.progquery.typeInfo.keys.TypeKey;
import es.uniovi.reflection.progquery.utils.JavacInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntFunction;

/**
 * Runs independent javac contexts concurrently. Graph visits retain input order:
 * the existing declaration replacement protocol cannot safely interleave writers.
 * No monitor is held while javac or a graph visitor is executing.
 */
final class ParallelCompilation {
    private final DefinitionCache<TypeKey> types = DefinitionCache.TYPE_CACHE.get();
    private final DefinitionCache<CallableKey> callables = DefinitionCache.CALLABLE_DEC_CACHE.get();
    private final DefinitionCache<ComponentKey> components = DefinitionCache.COMPONENT_CACHE.get();
    private final PackageManager packages = PackageManager.PACKAGE_MANAGER.get();
    private final InfoToInsert pending = InfoToInsert.INFO_TO_INSERT.get();
    private final DatabaseFacade database = DatabaseFacade.CURRENT_DB_FACADE.get();
    private final boolean[] completed;
    private final JavacInfo[] javacInfos;
    private int graphOwner;
    private Throwable failure;

    ParallelCompilation(int taskCount) {
        completed = new boolean[taskCount];
        javacInfos = new JavacInfo[taskCount];
    }

    void visitGraph(int index, Runnable action) {
        synchronized (this) {
            while (index != graphOwner && failure == null) {
                try {
                    wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new CancellationException("Interrupted waiting for graph ownership");
                }
            }
            if (failure != null)
                throw new CancellationException("A compilation failed");
        }
        action.run();
    }

    synchronized void complete(int index) {
        completed[index] = true;
        while (graphOwner < completed.length && completed[graphOwner])
            graphOwner++;
        notifyAll();
    }

    private synchronized void fail(Throwable exception) {
        if (failure == null)
            failure = exception;
        notifyAll();
    }

    private void attach() {
        DefinitionCache.TYPE_CACHE.set(types);
        DefinitionCache.CALLABLE_DEC_CACHE.set(callables);
        DefinitionCache.COMPONENT_CACHE.set(components);
        PackageManager.PACKAGE_MANAGER.set(packages);
        InfoToInsert.INFO_TO_INSERT.set(pending);
        DatabaseFacade.CURRENT_DB_FACADE.set(database);
    }

    private void detach() {
        DefinitionCache.TYPE_CACHE.remove();
        DefinitionCache.CALLABLE_DEC_CACHE.remove();
        DefinitionCache.COMPONENT_CACHE.remove();
        PackageManager.PACKAGE_MANAGER.remove();
        InfoToInsert.INFO_TO_INSERT.remove();
        DatabaseFacade.CURRENT_DB_FACADE.remove();
        JavacInfo.currentJavacInfo.remove();
    }

    List<CompilationResult> run(CompilationScheduler scheduler, List<String> options, int workers) {
        return run(options.size(), workers, index -> scheduler.newCompilationTask(options.get(index), this, index));
    }

    List<CompilationResult> run(int taskCount, int workers, IntFunction<CompilationResult> compilation) {
        ExecutorService executor = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "ProgQuery-compiler");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<CompilationResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < taskCount; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    attach();
                    try {
                        return compilation.apply(index);
                    } catch (Throwable exception) {
                        fail(exception);
                        throw exception;
                    } finally {
                        javacInfos[index] = JavacInfo.currentJavacInfo.get();
                        complete(index);
                        detach();
                    }
                }));
            }
            List<CompilationResult> results = new ArrayList<>();
            for (Future<CompilationResult> future : futures)
                results.add(future.get());
            for (int i = javacInfos.length - 1; i >= 0; i--)
                if (javacInfos[i] != null) {
                    JavacInfo.setJavacInfo(javacInfos[i]);
                    break;
                }
            return results;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted compiling modules", exception);
        } catch (ExecutionException exception) {
            // An earlier future may only report cancellation caused by another
            // worker. Keep the original error that stopped the compilation.
            Throwable cause;
            synchronized (this) {
                cause = failure == null ? exception.getCause() : failure;
            }
            throw new IllegalStateException("Module compilation failed", cause);
        } finally {
            fail(new CancellationException("Compilation scope closed"));
            executor.shutdownNow();
            // Workers must release all shared state before the caller tears it down.
            boolean interrupted = Thread.interrupted();
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
