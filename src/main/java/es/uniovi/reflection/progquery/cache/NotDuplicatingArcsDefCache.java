package es.uniovi.reflection.progquery.cache;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;

public class NotDuplicatingArcsDefCache<TKEY> extends DefinitionCache<TKEY> {
	@Override
	public synchronized void putDefinition(TKEY k, NodeWrapper v) {
		markShared(v);
		definitionNodeCache.put(k, v);
	}
}
