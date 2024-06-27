package es.uniovi.reflection.progquery.database.nodes;

import org.neo4j.graphdb.Label;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum NodeTypes implements Label {
    /*Package nodes*/
    PROGRAM(NodeCategory.PACKAGE_NODE),
    PACKAGE(NodeCategory.PACKAGE_NODE),

    /*Type nodes*/
    ERROR_TYPE(NodeCategory.TYPE_NODE),
    CALLABLE_TYPE(NodeCategory.TYPE_NODE),
    NULL_TYPE(NodeCategory.TYPE_NODE),
    TYPE_VARIABLE(NodeCategory.TYPE_NODE),
    UNKNOWN_TYPE(NodeCategory.TYPE_NODE),
    VOID_TYPE(NodeCategory.TYPE_NODE),

    /*Polymorphic AST Types OR Type Nodes*/
    ARRAY_TYPE,
    GENERIC_TYPE,
    INTERSECTION_TYPE,
    PARAMETERIZED_TYPE,
    PRIMITIVE_TYPE,
    UNION_TYPE,
    WILDCARD_TYPE,

    /* Program Dependency Graph Nodes*/
    THIS_REF(NodeCategory.PDG_NODE),

    /*Control Flow Graph Nodes*/
    CFG_NORMAL_END(NodeCategory.CFG_NODE),
    CFG_START(NodeCategory.CFG_NODE),
    CFG_EXCEPTIONAL_END(NodeCategory.CFG_NODE),
    CFG_FINALLY_END(NodeCategory.CFG_NODE),

    /*Polymorphic Declarations/Definitions AST Nodes or not */
    ANNOTATION_DEC(NodeCategory.TYPE_DEC),
    ATTR_DEC(NodeCategory.VARIABLE_DEC),
    CLASS_DEC(NodeCategory.TYPE_DEC),
    CONSTRUCTOR_DEC(NodeCategory.CALLABLE_DEC),
    ENUM_DEC(NodeCategory.TYPE_DEC),
    ENUM_ELEMENT(NodeCategory.VARIABLE_DEC),
    INTERFACE_DEC(NodeCategory.TYPE_DEC),
    METHOD_DEC(NodeCategory.CALLABLE_DEC),
    RECORD_COMPONENT(NodeCategory.DECLARATION),
    RECORD_DEC(NodeCategory.TYPE_DEC),

    /*AST nodes*/
    ANNOTATION(NodeCategory.AST_NODE),
    COMPILATION_UNIT(NodeCategory.AST_NODE),
    CU_SKIPPED_DEC(NodeCategory.AST_NODE),
    ERRONEOUS_NODE (NodeCategory.AST_NODE),
    IMPORT(NodeCategory.AST_NODE),
    INITIALIZATION(NodeCategory.ANY_ASSIGNMENT),
    PACKAGE_IDENTIFIER(NodeCategory.IDENTIFIER),
    PACKAGE_SELECTION(NodeCategory.IDENTIFIER_SELECTION),
    PARAMETER_DEC(NodeCategory.LOCAL_DEC),
    UNKNOWN_IDENTIFIER (NodeCategory.IDENTIFIER),

    /*AST Types*/
    ANNOTATED_TYPE(NodeCategory.AST_TYPE),
    TYPE_IDENTIFIER(NodeCategory.IDENTIFIER, NodeCategory.AST_TYPE),
    TYPE_SELECTION(NodeCategory.IDENTIFIER_SELECTION, NodeCategory.AST_TYPE),
    TYPE_PARAM(NodeCategory.AST_TYPE),
    VAR_TYPE(NodeCategory.AST_TYPE),

    /* AST Patterns */
    TYPE_PATTERN(NodeCategory.PATTERN, NodeCategory.LOCAL_DEC),
    UNNAMED_PATTERN(NodeCategory.PATTERN),
    RECORD_PATTERN(NodeCategory.PATTERN),

    /*AST Statements OR Statement Sections*/
    ASSERT_STATEMENT(NodeCategory.STATEMENT),
    BLOCK(NodeCategory.STATEMENT),
    BREAK_STATEMENT(NodeCategory.STATEMENT),
    CASE_COLON(NodeCategory.CASE_SECTION),
    CASE_ARROW(NodeCategory.CASE_SECTION),
    CATCH_BLOCK(NodeCategory.AST_NODE),
    CONTINUE_STATEMENT(NodeCategory.STATEMENT),
    DO_WHILE_LOOP(NodeCategory.LOOP),
    EMPTY_STATEMENT(NodeCategory.STATEMENT),
    FOR_EACH_LOOP(NodeCategory.LOOP),
    EXPRESSION_STATEMENT(NodeCategory.STATEMENT),
    FINALLY_BLOCK(NodeCategory.AST_NODE),
    FOR_LOOP(NodeCategory.LOOP),
    IF_STATEMENT(NodeCategory.STATEMENT),
    LABELED_STATEMENT(NodeCategory.STATEMENT),
    LOCAL_VAR_DEC(NodeCategory.LOCAL_DEC, NodeCategory.STATEMENT),
    RETURN_STATEMENT(NodeCategory.STATEMENT),
    SWITCH_STATEMENT(NodeCategory.STATEMENT, NodeCategory.SWITCH),
    SYNCHRONIZED_BLOCK(NodeCategory.STATEMENT),
    THROW_STATEMENT(NodeCategory.STATEMENT),
    TRY_STATEMENT(NodeCategory.STATEMENT),
    WHILE_LOOP(NodeCategory.LOOP),
    YIELD_STATEMENT(NodeCategory.STATEMENT),

    /*AST Expressions*/
    ARRAY_ACCESS(NodeCategory.LVALUE),
    ASSIGNMENT(NodeCategory.ANY_ASSIGNMENT, NodeCategory.EXPRESSION),
    BINARY_OPERATION(NodeCategory.EXPRESSION),
    CALLABLE_REFERENCE(NodeCategory.EXPRESSION),
    COMPOUND_ASSIGNMENT(NodeCategory.ANY_ASSIGNMENT, NodeCategory.EXPRESSION),
    CONDITIONAL_EXPRESSION(NodeCategory.EXPRESSION),
    INSTANCE_OF(NodeCategory.EXPRESSION),
    LAMBDA_EXPRESSION(NodeCategory.EXPRESSION),
    LITERAL(NodeCategory.EXPRESSION),
    MEMBER_SELECTION(NodeCategory.IDENTIFIER_SELECTION, NodeCategory.LVALUE),
    METHOD_INVOCATION(NodeCategory.CALL),
    NEW_ARRAY(NodeCategory.EXPRESSION),
    NEW_INSTANCE(NodeCategory.CALL),
    SWITCH_EXPRESSION(NodeCategory.EXPRESSION, NodeCategory.SWITCH),
    TYPE_CAST(NodeCategory.EXPRESSION),
    UNARY_OPERATION(NodeCategory.EXPRESSION),
    VARIABLE(NodeCategory.IDENTIFIER, NodeCategory.LVALUE);


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
