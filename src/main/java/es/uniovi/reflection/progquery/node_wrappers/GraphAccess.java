package es.uniovi.reflection.progquery.node_wrappers;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/** Per-program topology barrier: ordinary edge operations share the read side;
 * declaration replacement briefly excludes them on the write side. */
public final class GraphAccess {
    static final GraphAccess STANDALONE = new GraphAccess();
    final ReentrantReadWriteLock topology = new ReentrantReadWriteLock();

    public void replaceDeclaration(Runnable action) {
        var lock = topology.writeLock();
        lock.lock();
        try { action.run(); } finally { lock.unlock(); }
    }
}
