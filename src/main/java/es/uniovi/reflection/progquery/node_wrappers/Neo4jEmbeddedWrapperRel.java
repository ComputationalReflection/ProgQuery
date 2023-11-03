package es.uniovi.reflection.progquery.node_wrappers;

import java.util.Map.Entry;
import java.util.Set;

import org.neo4j.graphdb.Relationship;

import es.uniovi.reflection.progquery.database.relations.CDGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.CGRelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypes;
import es.uniovi.reflection.progquery.database.relations.RelationTypesInterface;
import es.uniovi.reflection.progquery.database.relations.TypeRelations;

public class Neo4jEmbeddedWrapperRel implements RelationshipWrapper {
	private Relationship relationship;

	public Neo4jEmbeddedWrapperRel(Relationship relationship) {
		super();
		this.relationship = relationship;
	}

	@Override
	public void setProp(String name, Object value) {
		relationship.setProperty(name, value);
	}

	@Override
	public boolean hasProperty(String string) {
		return relationship.hasProperty(string);
	}

	@Override
	public Object getProperty(String string) {
		return relationship.getProperty(string);
	}

	@Override
	public Set<Entry<String, Object>> getAllProperties() {
		return relationship.getAllProperties().entrySet();
	}

	@Override
	public RelationTypesInterface getType() {
		return nameToEnum(relationship.getType().name());
	}

	static RelationTypesInterface nameToEnum(String name) {
		try {
			return RelationTypes.valueOf(name);
		} catch (IllegalArgumentException e) {
			try {
				return TypeRelations.valueOf(name);
			} catch (IllegalArgumentException e2) {
				try {
					return CDGRelationTypes.valueOf(name);
				} catch (IllegalArgumentException e3) {

					return CGRelationTypes.valueOf(name);
				}
			}
		}
	}

	@Override
	public NodeWrapper getStartNode() {
		return new Neo4jEmbeddedWrapperNode(relationship.getStartNode());
	}

	@Override
	public NodeWrapper getEndNode() {
		return new Neo4jEmbeddedWrapperNode(relationship.getEndNode());
	}

	@Override
	public void delete() {
		relationship.delete();
		}

	@Override
	public String getTypeString() {
		return relationship.getType().name();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((relationship == null) ? 0 : relationship.hashCode());
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
		Neo4jEmbeddedWrapperRel other = (Neo4jEmbeddedWrapperRel) obj;
		if (relationship == null) {
			if (other.relationship != null)
				return false;
		} else if (!relationship.equals(other.relationship))
			return false;
		return true;
	}
}
