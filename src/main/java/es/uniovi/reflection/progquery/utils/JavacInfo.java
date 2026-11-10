package es.uniovi.reflection.progquery.utils;

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
import es.uniovi.reflection.progquery.typeInfo.keys.TypeKey;
import es.uniovi.reflection.progquery.typeInfo.keys.type.ParameterizedTypeKey;
import es.uniovi.reflection.progquery.typeInfo.keys.type.TypeDefinitionKey;
import es.uniovi.reflection.progquery.typeInfo.keys.type.TypeVariableKey;
import es.uniovi.reflection.progquery.visitors.KeyTypeVisitor;

import javax.lang.model.element.Element;
import javax.lang.model.type.*;
import javax.lang.model.util.Types;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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

    public boolean isSubSignatureOwn(ExecutableType sub, ExecutableType sup) {
        return isSubSignatureOwn(signatureOf(sub), signatureOf(sup));
    }

    public boolean isSubSignatureOwn(MethodSignature sub, MethodSignature sup) {
        // Names are checked by MethodSigInfo before reaching this method.
        if (sub.parameterTypes().equals(sup.parameterTypes())
                && sub.typeParameterBounds().equals(sup.typeParameterBounds()))
            return true;

        // JLS 8.4.2: a non-generic signature may match the erasure of the
        // super signature. Erasing both sides would also accept name clashes.
        return sub.typeParameterBounds().isEmpty()
                && sub.parameterTypes().equals(sup.erasedParameterTypes());
    }

    /** Capture while the originating javac task is alive; keys may complete symbols. */
    public static MethodSignature signatureOf(ExecutableType signature) {
        SignatureKeyVisitor keys = new SignatureKeyVisitor(signature, false);
        SignatureKeyVisitor erasedKeys = new SignatureKeyVisitor(signature, true);
        return new MethodSignature(
                signature.getParameterTypes().stream().map(keys::key).toList(),
                signature.getTypeVariables().stream().map(variable -> keys.key(variable.getUpperBound())).toList(),
                signature.getParameterTypes().stream().map(erasedKeys::key).toList());
    }

    public record MethodSignature(List<TypeKey> parameterTypes, List<TypeKey> typeParameterBounds,
                                  List<TypeKey> erasedParameterTypes) {
        public MethodSignature {
            parameterTypes = List.copyOf(parameterTypes);
            typeParameterBounds = List.copyOf(typeParameterBounds);
            erasedParameterTypes = List.copyOf(erasedParameterTypes);
        }
    }

    private static final class SignatureKeyVisitor extends KeyTypeVisitor {
        private final Map<Element, TypeKey> methodVariables = new IdentityHashMap<>();
        private final boolean erase;

        SignatureKeyVisitor(ExecutableType signature, boolean erase) {
            this.erase = erase;
            for (int i = 0; i < signature.getTypeVariables().size(); i++)
                methodVariables.put(signature.getTypeVariables().get(i).asElement(), new SignatureVariableKey(i));
        }

        TypeKey key(TypeMirror type) {
            return type.accept(this, null);
        }

        @Override
        public TypeKey visitDeclared(DeclaredType type, Object param) {
            TypeKey definition = new TypeDefinitionKey(((Type) type).tsym);
            if (erase)
                return definition;
            TypeKey result = type.getTypeArguments().isEmpty() ? definition
                    : new ParameterizedTypeKey(type.getTypeArguments().stream().map(this::key).toList(), definition);
            return type.getEnclosingType().getKind() == TypeKind.NONE ? result
                    : new SignatureMemberTypeKey(key(type.getEnclosingType()), result);
        }

        @Override
        public TypeKey visitTypeVariable(TypeVariable type, Object param) {
            if (erase)
                return key(type.getUpperBound());
            TypeKey variable = methodVariables.get(type.asElement());
            if (variable != null)
                return variable;
            // The owner is a declaration, not its parameterized type: visiting
            // Class<T> again here would recurse through T indefinitely.
            Symbol owner = ((Type) type).tsym.owner;
            if (!(type instanceof Type.CapturedType) && owner instanceof Symbol.ClassSymbol)
                return new TypeVariableKey(type, new TypeDefinitionKey(owner));
            return super.visitTypeVariable(type, param);
        }

        @Override
        public TypeKey visitIntersection(IntersectionType type, Object param) {
            return erase ? key(type.getBounds().getFirst()) : super.visitIntersection(type, param);
        }
    }

    // These keys are local to signature comparison; declaration/cache identity is unchanged.
    private record SignatureVariableKey(int position) implements TypeKey {}

    private record SignatureMemberTypeKey(TypeKey enclosing, TypeKey member) implements TypeKey {}

    private static Object[] getPosition(Object line, Object column, Object position) {
        return new Object[]{LINE_NUMBER, line, COLUMN, column, POSITION, position};
    }
}
