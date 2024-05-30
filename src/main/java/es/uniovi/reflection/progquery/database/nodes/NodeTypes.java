package es.uniovi.reflection.progquery.database.nodes;

import org.neo4j.graphdb.Label;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum NodeTypes implements Label {
    ANNOTATION(NodeCategory.AST_NODE),
    ANNOTATED_TYPE(NodeCategory.AST_TYPE),
    ARRAY_ACCESS(NodeCategory.LVALUE),
    ARRAY_TYPE,
    ASSERT_STATEMENT(NodeCategory.STATEMENT),
    ASSIGNMENT(NodeCategory.ANY_ASSIGNMENT, NodeCategory.EXPRESSION),
    ATTR_DEF(NodeCategory.VARIABLE_DEF),
    BINARY_OPERATION(NodeCategory.EXPRESSION),
    BLOCK(NodeCategory.STATEMENT),
    BREAK_STATEMENT(NodeCategory.STATEMENT),
    CASE_SECTION(NodeCategory.STATEMENT_PART),
    CATCH_BLOCK(NodeCategory.STATEMENT_PART),
    CLASS_DEF(NodeCategory.TYPE_DEFINITION),
    COMPILATION_UNIT(NodeCategory.AST_NODE),
    COMPOUND_ASSIGNMENT(NodeCategory.ANY_ASSIGNMENT, NodeCategory.EXPRESSION),
    CONDITIONAL_EXPRESSION(NodeCategory.EXPRESSION),
    CONSTRUCTOR_DEF(NodeCategory.CALLABLE_DEF),
    CONTINUE_STATEMENT(NodeCategory.STATEMENT),
    DO_WHILE_LOOP(NodeCategory.LOOP),
    ERROR_TYPE(NodeCategory.TYPE_NODE),
    EMPTY_STATEMENT(NodeCategory.STATEMENT),
    FOR_EACH_LOOP(NodeCategory.LOOP),
    ENUM_DEF(NodeCategory.TYPE_DEFINITION),
    ENUM_ELEMENT(NodeCategory.DEFINITION),
    ERRONEOUS_NODE (NodeCategory.AST_NODE),
    CALLABLE_TYPE(NodeCategory.TYPE_NODE),
    EXPRESSION_STATEMENT(NodeCategory.STATEMENT),
    FINALLY_BLOCK(NodeCategory.STATEMENT_PART),
    FOR_LOOP(NodeCategory.LOOP),
    PACKAGE_IDENTIFIER(NodeCategory.IDENTIFIER),
    VARIABLE(NodeCategory.IDENTIFIER, NodeCategory.LVALUE),
    TYPE_IDENTIFIER(NodeCategory.IDENTIFIER, NodeCategory.AST_TYPE),
    IF_STATEMENT(NodeCategory.STATEMENT),
    IMPORT(NodeCategory.AST_NODE),
    INSTANCE_OF(NodeCategory.EXPRESSION),
    INTERFACE_DEF(NodeCategory.TYPE_DEFINITION),
    INTERSECTION_TYPE,
    LABELED_STATEMENT(NodeCategory.STATEMENT),
    LAMBDA_EXPRESSION(NodeCategory.EXPRESSION),
    LITERAL(NodeCategory.EXPRESSION),
    PACKAGE_SELECTION(NodeCategory.IDENTIFIER_SELECTION),
    MEMBER_SELECTION(NodeCategory.IDENTIFIER_SELECTION, NodeCategory.LVALUE),
    TYPE_SELECTION(NodeCategory.IDENTIFIER_SELECTION, NodeCategory.AST_TYPE),
    MEMBER_REFERENCE(NodeCategory.EXPRESSION),
    METHOD_DEF(NodeCategory.CALLABLE_DEF),
    METHOD_INVOCATION(NodeCategory.CALL),
    NEW_ARRAY(NodeCategory.EXPRESSION),
    NEW_INSTANCE(NodeCategory.CALL),
    NULL_TYPE(NodeCategory.TYPE_NODE),
    PACKAGE(NodeCategory.PACKAGE_NODE),
    PACKAGE_TYPE(NodeCategory.TYPE_NODE),
    PARAMETER_DEF(NodeCategory.LOCAL_DEF),
    GENERIC_TYPE,
    PARAMETERIZED_TYPE,
    PRIMITIVE_TYPE,
    RETURN_STATEMENT(NodeCategory.STATEMENT),
    SWITCH_STATEMENT(NodeCategory.STATEMENT),
    SYNCHRONIZED_BLOCK(NodeCategory.STATEMENT),
    THIS_REF(NodeCategory.PDG_NODE),
    THROW_STATEMENT(NodeCategory.STATEMENT),
    TRY_STATEMENT(NodeCategory.STATEMENT),
    TYPE_CAST(NodeCategory.EXPRESSION),
    TYPE_PARAM(NodeCategory.AST_TYPE),
    UNARY_OPERATION(NodeCategory.EXPRESSION),
    UNION_TYPE,
    LOCAL_VAR_DEF(NodeCategory.LOCAL_DEF, NodeCategory.STATEMENT),
    WHILE_LOOP(NodeCategory.LOOP),
    WILDCARD_TYPE,
    VOID_TYPE(NodeCategory.TYPE_NODE),
    TYPE_VARIABLE(NodeCategory.TYPE_NODE),
    UNKNOWN_TYPE(NodeCategory.TYPE_NODE),
    INITIALIZATION(NodeCategory.ANY_ASSIGNMENT),
    CFG_NORMAL_END(NodeCategory.CFG_NODE),
    CFG_ENTRY(NodeCategory.CFG_NODE),
    CFG_EXCEPTIONAL_END(NodeCategory.CFG_NODE),
    CFG_LAST_STATEMENT_IN_FINALLY(NodeCategory.CFG_NODE),
    PROGRAM(NodeCategory.PACKAGE_NODE);

    NodeTypes(NodeCategory... hypernyms) {
        this();
        List<NodeCategory> hyperList = new ArrayList<>(Arrays.asList(hypernyms));
        for (int i = 0; i < hyperList.size(); i++) {
            NodeCategory superCategory = hyperList.get(i);
            if (!this.hypernyms.contains(superCategory)){
                this.hypernyms.add(superCategory);
                hyperList.addAll(superCategory.hypernyms);
            }
        }
    }


    NodeTypes() {
        hypernyms = new ArrayList<>();
        hypernyms.add(NodeCategory.PQ_NODE);
    }

    public final List<NodeCategory> hypernyms;
}
