package database.querys;

<<<<<<< HEAD
import java.io.IOException;

=======
>>>>>>> 2efd75eb383cfcfe52622098e67722a31ae3861f
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;

<<<<<<< HEAD:PhdPrototype/src/database/querys/MainQuery.java
=======
<<<<<<< HEAD
import io.FileUtil;

>>>>>>> 35eb70e6a97b8ef16fe55a3f0ce9611eb967a81c:PhdPrototype/src/querys/MainQuery.java
public class MainQuery {
	private static final String ALL_METHODS_OF_ALL_CLASSES_QUERY = "START c=node:node_auto_index(nodeType='JCClassDecl') MATCH c-[r:DECLARES_METHOD]->m RETURN ('Class '+ c.fullyQualifiedName+ ' with method ' + m.name) AS warning";
	private static final String ALL_CLASSES_WITH_EQUALS = "START c=node:node_auto_index(nodeType='JCClassDecl') MATCH c-[r:DECLARES_METHOD]->(m {name:'equals'}) RETURN ('Class '+ c.fullyQualifiedName+ ' has a method named equals.') AS warning";
	public static final String ALL_NODES = "START n=node(*) MATCH n-[r]->m RETURN n,r,m";
	private static final String ALL_CLASSES_OVERRITING_EQUALS = "START m=node:node_auto_index(nodeType='JCMethodDecl') MATCH c-[:DECLARES_METHOD]->(m{name:'equals'})-[:HAS_METHODDECL_PARAMETERS]->p WITH c AS c,m AS m,count(p) AS paramCount WHERE paramCount=1 RETURN 'Clase ' +c.fullyQualifiedName+ ' has an equals method overriding its superclass.' AS warning";
	public static final String DELETE_ALL = "MATCH (n) DETACH DELETE n";
	private static final String WARNING_EQUALS_HASHCODE = "START m=node:node_auto_index(nodeType='JCMethodDecl') MATCH c-[:DECLARES_METHOD]->(m{name:'equals'})-[:HAS_METHODDECL_PARAMETERS]->p WITH c AS c,m AS m,count(p) AS paramCount WHERE paramCount=1 AND NOT (c)-[:DECLARES_METHOD]->({name:'hashCode'}) RETURN 'Clase ' +c.fullyQualifiedName+ ' has an equals method overriding its superclass.' AS warning";
	private static final String WARNING_EQUALS_HASHCODE_R = "START m=node:node_auto_index(nodeType='JCMethodDecl') MATCH c-[:DECLARES_METHOD]->(m{name:'equals'})-[:HAS_METHODDECL_PARAMETERS]->p OPTIONAL MATCH (hash{name:'hashCode'})<-[:DECLARES_METHOD]-c WITH hash AS hash ,c AS c,m AS m,count(p) AS paramCount WHERE paramCount=1 AND (hash IS NULL OR NOT hash-[:HAS_METHODDECL_PARAMETERS]->())RETURN '[WARNING] Class ' +c.fullyQualifiedName+ ' has an equals method overriding its superclass. An appropiate hashCode method must be redefined too.' AS warning";

	private static final String ALL_NODES_EXTENDS = "START n=node(*) MATCH n-[r:IS_SUBTYPE_IMPLEMENTS|HAS_CLASS_IMPLEMENTS]->m RETURN n,r,m";

	private static final String ALL_NODES_EXTENDS_AND_MORE = "START n=node(*) "
			+ "MATCH n-[r:IS_SUBTYPE_IMPLEMENTS|HAS_CLASS_IMPLEMENTS]->m " + "MATCH m-[r2]->o " + "RETURN n,r,m,r2,o";
	private static final String ALL_NODES_EXTENDS_AND_MORE_BIS = "START n=node(*) "
			+ "MATCH n-[r:IS_SUBTYPE_IMPLEMENTS|HAS_CLASS_IMPLEMENTS]->m " + "MATCH o-[r2]->n " + "RETURN n,r,m,r2,o";
	private static final String TYPE_HIERARCHY = "START n=node(*) MATCH n-[r:IS_SUBTYPE_IMPLEMENTS | IS_SUBTYPE_EXTENDS]->m RETURN n,r,m";

	private static final String METHOD_INV_RELATIONS_OUT = "START n=node:node_auto_index(nodeType='METHOD_INVOCATION') MATCH n-[r]->m RETURN n,r,m";
	private static final String METHOD_INV_RELATIONS_IN = "START m=node:node_auto_index(nodeType='METHOD_INVOCATION') MATCH n-[r]->m RETURN n,r,m";
	private static final String ALL_CALLS = "START n=node(*) MATCH n-[r:CALLS]->mi-[r2:HAS_DEC]->md RETURN n,r,md";
	private static final String CFG_RELS = "start n=node(*) MATCH m-[r:CFG_ENTRY | CFG_END_OF |	 CFG_NEXT_CONDITION | CFG_NEXT_STATEMENT | CFG_NEXT_COND_IF_TRUE | CFG_NEXT_STATEMENT_IF_TRUE | CFG_NEXT_COND_IF_FALSE | CFG_NEXT_STATEMENT_IF_FALSE ]->n RETURN m,r,n";
	private static final String PDG_RELS = "start n=node(*) MATCH m-[r:USED_BY | MODIFIED_BY | STATE_MODIFIED_BY ]->n RETURN m,r,n";
	private static final String STAT_MOD_RELS = "start n=node(*) MATCH m-[r: STATE_MODIFIED_BY |  STATE_MAY_BE_MODIFIED ]->n RETURN m,r,n";
	private static final String USED_BY_RELS = "start n=node(*) MATCH m-[r: USED_BY ]->n RETURN m,r,n";
	private static final String THIS_RELS = "START m=node:node_auto_index(nodeType='THIS_REF') MATCH m-[r]->n RETURN m,r,n";

	private static final String METHOD_MOD_RELS = "START m=node:node_auto_index(nodeType='METHOD_DEC') MATCH m<-[r:STATE_MODIFIED_BY | STATE_MAY_BE_MODIFIED]-n RETURN m,r,n";
	private static final String ALL_METHOD_DECS_RELS = "START m=node:node_auto_index(nodeType='METHOD_DEC') MATCH m-[r]-n RETURN m,r,n";

	// Controlar si se puede sobreescribir el equals metiendo primitivas al
	// parametro
	public static void main(String[] args) throws IOException {
		// GraphDatabaseService db = DatabaseFachade.getDB();
		// DatabaseFachade.setDB(db);
		// Transaction t = db.beginTx();
		// Node n = DatabaseFachade.createNode();
		// n.createRelationshipTo(DatabaseFachade.createNode(),
		// PDGRelationTypes.MAY_RETURN);
		// t.success();
		// System.out.println(n.getSingleRelationship(PDGRelationTypes.MAY_RETURN,
		// Direction.OUTGOING));
		// System.out.println(n.getSingleRelationship(PDGRelationTypes.MAY_RETURN,
		// Direction.OUTGOING).getType());
		// System.out.println(n.getSingleRelationship(PDGRelationTypes.MAY_RETURN,
		// Direction.OUTGOING)
		// .getType() == PDGRelationTypes.MAY_RETURN);
		// System.out
		// .println(n.getSingleRelationship(PDGRelationTypes.MAY_RETURN,
		// Direction.OUTGOING).getType().getClass());
		// System.out
		// .println(n.getSingleRelationship(PDGRelationTypes.MAY_RETURN,
		// Direction.OUTGOING).getType()
		// .getProperty("name"));

		GraphDatabaseService gs = loadDB();

		Result res = gs.execute(METHOD_MOD_RELS);
		// gs.execute(TYPE_HIERARCHY);

		// System.out.println(res.resultAsString());
		// res = gs.execute(METHOD_INV_RELATIONS_IN);
		// FileUtil.writeFile("selfAnalisysOutput.txt", res.resultAsString());
		System.out.println(res.resultAsString());
		gs.execute(DELETE_ALL);
		// String cad = "Hoy +";
		// cad += "apruebo";
		// System.out.println(cad.contentEquals("Hoy apruebo"));
		// System.out.println((int) Math.random() * 1);
=======
public class MainQuery {
	private static final String ALL_METHODS_OF_ALL_CLASSES_QUERY = "START c=node:node_auto_index(nodeType='JCClassDecl') MATCH c-[r:DECLARES_METHOD]->m RETURN ('Class '+ c.fullyQualifiedName+ ' with method ' + m.name) AS warning";
	private static final String ALL_CLASSES_WITH_EQUALS="START c=node:node_auto_index(nodeType='JCClassDecl') MATCH c-[r:DECLARES_METHOD]->(m {name:'equals'}) RETURN ('Class '+ c.fullyQualifiedName+ ' has a method named equals.') AS warning";
	private static final String ALL_NODES="START n=node(*) MATCH n-[r]->m RETURN n,r,m";
	private static final String ALL_CLASSES_OVERRITING_EQUALS="START m=node:node_auto_index(nodeType='JCMethodDecl') MATCH c-[:DECLARES_METHOD]->(m{name:'equals'})-[:HAS_METHODDECL_PARAMETERS]->p WITH c AS c,m AS m,count(p) AS paramCount WHERE paramCount=1 RETURN 'Clase ' +c.fullyQualifiedName+ ' has an equals method overriding its superclass.' AS warning";
	private static final String DELETE_ALL = "MATCH (n) DETACH DELETE n";
	private static final String WARNING_EQUALS_HASHCODE="START m=node:node_auto_index(nodeType='JCMethodDecl') MATCH c-[:DECLARES_METHOD]->(m{name:'equals'})-[:HAS_METHODDECL_PARAMETERS]->p WITH c AS c,m AS m,count(p) AS paramCount WHERE paramCount=1 AND NOT (c)-[:DECLARES_METHOD]->({name:'hashCode'}) RETURN 'Clase ' +c.fullyQualifiedName+ ' has an equals method overriding its superclass.' AS warning";
	private static final String WARNING_EQUALS_HASHCODE_R="START m=node:node_auto_index(nodeType='JCMethodDecl') MATCH c-[:DECLARES_METHOD]->(m{name:'equals'})-[:HAS_METHODDECL_PARAMETERS]->p OPTIONAL MATCH (hash{name:'hashCode'})<-[:DECLARES_METHOD]-c WITH hash AS hash ,c AS c,m AS m,count(p) AS paramCount WHERE paramCount=1 AND (hash IS NULL OR NOT hash-[:HAS_METHODDECL_PARAMETERS]->())RETURN '[WARNING] Class ' +c.fullyQualifiedName+ ' has an equals method overriding its superclass. An appropiate hashCode method must be redefined too.' AS warning";
	
	

//Controlar si se puede sobreescribir el equals metiendo primitivas al parametro
	public static void main(String[] args) {
		GraphDatabaseService gs=loadDB();
		Result res = gs.execute(ALL_NODES);
		System.out.println(res.resultAsString());
		gs.execute(DELETE_ALL);
>>>>>>> 2efd75eb383cfcfe52622098e67722a31ae3861f
	}

	public static GraphDatabaseService loadDB() {
		String wiggleDbPath = "./neo4j/data/wiggle.db";
		return new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(wiggleDbPath)
				.setConfig(GraphDatabaseSettings.node_keys_indexable, "nodeType")
				.setConfig(GraphDatabaseSettings.relationship_keys_indexable, "typeKind")
				.setConfig(GraphDatabaseSettings.node_auto_indexing, "true")
				.setConfig(GraphDatabaseSettings.relationship_auto_indexing, "true").newGraphDatabase();
	}
}