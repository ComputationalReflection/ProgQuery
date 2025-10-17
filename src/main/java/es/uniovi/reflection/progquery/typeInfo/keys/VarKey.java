package es.uniovi.reflection.progquery.typeInfo.keys;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;
import es.uniovi.reflection.progquery.visitors.PDGProcessing;

public interface VarKey extends ElementKey {

    void putDecInCache(PDGProcessing pdgProcessing, NodeWrapper definitionNode);

    NodeWrapper getNode(PDGProcessing pdgProcessing);
}
