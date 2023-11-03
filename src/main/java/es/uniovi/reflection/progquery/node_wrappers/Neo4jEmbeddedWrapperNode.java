package es.uniovi.reflection.progquery.node_wrappers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import es.uniovi.reflection.progquery.database.nodes.NodeUtils;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;

public class Neo4jEmbeddedWrapperNode implements NodeWrapper {
	private Node node;

	public Neo4jEmbeddedWrapperNode(Node node) {
		this.node = node;
	}
	
	public Node getNode() {
		return node;
	}

	@Override
	public void setProp(String name, Object value) {
		node.setProperty(name, value);
	}

	@Override
	public boolean hasProperty(String string) {
		return node.hasProperty(string);
	}

	@Override
	public Object getProperty(String string) {
		return node.getProperty(string);
	}

	@Override
	public Set<Entry<String, Object>> getAllProperties() {
		return node.getAllProperties().entrySet();
	}

	@Override
	public Long getId() {
		return node.getId();
	}

	@Override
	public RelationshipWrapper createRelationshipTo(NodeWrapper end, RelationTypesInterface relType) {
		return new Neo4jEmbeddedWrapperRel(node.createRelationshipTo(((Neo4jEmbeddedWrapperNode) end).node, relType));
	}

	private List<RelationshipWrapper> fromIterToList(Iterable<Relationship> iter) {
		List<RelationshipWrapper> res = new ArrayList<RelationshipWrapper>();
		for (Relationship r : iter)
			res.add(new Neo4jEmbeddedWrapperRel(r));
		return res;
	}

	@Override
	public List<RelationshipWrapper> getRelationships() {
		return fromIterToList(node.getRelationships());
	}

	@Override
	public RelationshipWrapper getSingleRelationship(Direction direction, RelationTypesInterface relType) {
		Relationship rel = node.getSingleRelationship(relType, direction);
		return rel == null ? null : new Neo4jEmbeddedWrapperRel(rel);
	}


	@Override
	public List<RelationshipWrapper> getRelationships(Direction direction,
			RelationTypesInterface... possibleRelTypes) {
		return fromIterToList(node.getRelationships(direction, possibleRelTypes));
	}

	@Override
	public boolean hasRelationship(RelationTypesInterface relType, Direction direction) {
		return node.hasRelationship(direction, relType);
	}

	@Override
	public Set<Label> getLabels() {
		HashSet<Label> nodeTypes = new HashSet<>();
		node.getLabels().forEach(l -> nodeTypes.add(l));
		return nodeTypes;
	}

	@Override
	public boolean hasLabel(Label label) {
		return node.hasLabel(label);
	}

	@Override
	public void removeLabel(Label label) {
		node.removeLabel(label);
	}

	@Override
	public void addLabel(Label label) {
		node.addLabel(label);
	}

	@Override
	public void delete() {
		node.delete();
	}

	@Override
	public void setId(long id) {
		throw new IllegalStateException("The id cannot be set for embedded JIT nodes.");
	}

	@Override
	public List<RelationshipWrapper> getRelationships(Direction direction) {
		return fromIterToList(node.getRelationships(direction));
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((node == null) ? 0 : node.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Neo4jEmbeddedWrapperNode other = (Neo4jEmbeddedWrapperNode) obj;
		if (node == null) {
			if (other.node != null)
				return false;
		} else if (!node.equals(other.node))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return NodeUtils.reducedClassMethodToString(this);
	}
}
