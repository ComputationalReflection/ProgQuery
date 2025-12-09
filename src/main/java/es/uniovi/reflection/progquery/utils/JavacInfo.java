package es.uniovi.reflection.progquery.utils;

import com.google.common.collect.Maps;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeInfo;
import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;

import javax.lang.model.element.Element;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.Types;

import static es.uniovi.reflection.progquery.database.nodes.NodeProperties.*;

public class JavacInfo {

    public static ThreadLocal<JavacInfo> currentJavacInfo = new ThreadLocal<>();
    private final SourcePositions sourcePositions;
    private final Trees trees;
    private final Types javaxTypes;
    private final com.sun.tools.javac.code.Types types;
    private final Symtab symTab;

    private CompilationUnitTree currCompilationUnit;

    public JavacInfo(JavacTask task) {
        this.trees = Trees.instance(task);
        javaxTypes = task.getTypes();
        types = com.sun.tools.javac.code.Types.instance(((com.sun.tools.javac.api.BasicJavacTask) task).getContext());
        this.sourcePositions = trees.getSourcePositions();
        symTab = Symtab.instance(((com.sun.tools.javac.api.BasicJavacTask) task).getContext());
    }

    public static boolean isInitialized() {
        return currentJavacInfo.get() != null;
    }

    public static void setJavacInfo(JavacInfo javacInfo) {
        currentJavacInfo.set(javacInfo);
    }

    public static Object[] getPosition(Tree tree) {
        LineMap lineMap = currentJavacInfo.get().currCompilationUnit.getLineMap();
        if (lineMap == null)
            return new Object[0];
        long position =
                currentJavacInfo.get().sourcePositions.getStartPosition(currentJavacInfo.get().currCompilationUnit,
                        tree);

        long line = -1;
        long column = -1;
        if (position != -1) {
            line = lineMap.getLineNumber(position);
            column = lineMap.getColumnNumber(position);
        }

        return getPosition(line, column, position);

    }

    public static Object[] getPosition(NodeWrapper node) {
        return getPosition(node.getProperty(LINE_NUMBER), node.getProperty(COLUMN), node.getProperty(POSITION));
    }

    public static long getSize(Tree tree) {
        return currentJavacInfo.get().sourcePositions.getEndPosition(currentJavacInfo.get().currCompilationUnit, tree) -
                currentJavacInfo.get().sourcePositions.getStartPosition(currentJavacInfo.get().currCompilationUnit,
                        tree);
    }

    public static TreePath getPath(Tree tree) {
        return TreePath.getPath(currentJavacInfo.get().currCompilationUnit, tree);
    }

    public static TypeMirror getTypeMirror(Tree tree, TreePath path) {
        return currentJavacInfo.get().trees.getTypeMirror(path);
    }

    public static TypeMirror getTypeMirror(Tree tree) {
        return currentJavacInfo.get().trees.getTypeMirror(getPath(tree));
    }

    public static Type getTypeDirect(ExpressionTree tree) {

        return ((JCExpression) tree).type;
    }

    public static com.sun.tools.javac.code.Type getTypeDirect(VariableTree tree) {

        return ((JCVariableDecl) tree).type;
    }

    public static TypeMirror getTypeDirect(MethodTree tree) {

        return ((JCMethodDecl) tree).type;
    }

    public static Tree getTree(Symbol s) {
        return currentJavacInfo.get().trees.getTree(s);
    }

    public static Tree getTreeFromElement(Element e) {
        return currentJavacInfo.get().trees.getTree(e);
    }

    public static Tree getTreeFromElement(Tree t) {
        return currentJavacInfo.get().trees.getTree(currentJavacInfo.get().trees.getElement(getPath(t)));
    }

    public static Scope getScope(Tree t) {
        return currentJavacInfo.get().trees.getScope(getPath(t));
    }

    public static boolean isSubType(Type t1, Type t2) {
        return currentJavacInfo.get().javaxTypes.isSubtype(t1, t2);
    }

    public static boolean isSuperType(Type t1, Type t2) {
        return currentJavacInfo.get().javaxTypes.isSubtype(t2, t1);
    }

    public static Symtab getSymtab() {
        return currentJavacInfo.get().symTab;
    }

    public static Symbol getSymbolFromTree(Tree t) {
        return TreeInfo.symbol((JCTree) t);
    }

    public static Type erasure(Type t) {
        return t.tsym.erasure(currentJavacInfo.get().types);
    }

    public void setCurrCompilationUnit(CompilationUnitTree currCompilationUnit) {
        this.currCompilationUnit = currCompilationUnit;
    }

    public static boolean isSubSignatureOwn(ExecutableType sub, ExecutableType sup) {

        com.sun.tools.javac.code.Types types = currentJavacInfo.get().types;
        for (int i = 0; i < sub.getParameterTypes().size(); i++) {
            Type supType = (Type) sup.getParameterTypes().get(i);
            boolean subtyping = false;
            if (supType instanceof TypeVariable) {
                supType = types.erasure(supType).tsym.type;
                subtyping = true;
            }
            Type subType = (Type) sub.getParameterTypes().get(i);
            if (subType instanceof TypeVariable) {
                subType = types.erasure(subType).tsym.type;
                subtyping = true;
            }
            if (subtyping) {
                if (!isSubTypeOwn(subType, supType))
                    return false;
            } else {
                if (!isSameTypeOwn(subType, supType))
                    return false;
            }
        }
        return true;
        //	return	currentJavacInfo.get().elements.overrides(sub, sup, (TypeElement) sub.getEnclosingElement());
        //		return sub.overrides(sup, (Symbol.TypeSymbol)sub.owner,currentJavacInfo.get().types, true);
    }

    private static boolean isSubTypeOwn(Type sub, Type sup) {
        if (sub instanceof Type.ClassType && sup instanceof Type.ClassType)
            return isSubTypeRec((Type.ClassType) sub, (Type.ClassType) sup);
        return false;
    }

    private static boolean isSubTypeRec(Type.ClassType sub, Type.ClassType sup) {
        if (sub == null)
            return false;
        if (isSameTypeOwn(sub, sup))
            return true;
        if (sub.toString().contentEquals("java.lang.Object"))
            return false;
        if (isSubTypeRec((Type.ClassType) sub.supertype_field, sup))
            return true;
        if (sub.interfaces_field != null)
            for (Type iface : sub.interfaces_field)
                if (isSubTypeRec((Type.ClassType) iface, sup))
                    return true;
        return false;
    }

    private static boolean isSameTypeOwn(Type t1, Type t2) {
        return t1.toString().contentEquals(t2.toString());
    }

    private static Object[] getPosition(Object line, Object column, Object position) {
        return new Object[]{LINE_NUMBER, line, COLUMN, column, POSITION, position};
    }
}
