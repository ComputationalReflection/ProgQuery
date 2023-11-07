package es.uniovi.reflection.progquery.utils.dataTransferClasses;

import java.util.Set;

import es.uniovi.reflection.progquery.node_wrappers.NodeWrapper;

public class VisitorResultImpl implements ASTVisitorResult {
	private boolean isInstance;
	private Set<NodeWrapper> paramsPreviouslyModifiedInCases;
	public VisitorResultImpl(boolean isInstance, Set<NodeWrapper> paramsPreviouslyModifiedInCases) {
		super();
		this.isInstance = isInstance;
		this.paramsPreviouslyModifiedInCases = paramsPreviouslyModifiedInCases;
	}
	public VisitorResultImpl(boolean isInstance) {
		super();
		this.isInstance = isInstance;
	}

	public VisitorResultImpl(Set<NodeWrapper> paramsPreviouslyModifiedInCases) {
		super();
		this.paramsPreviouslyModifiedInCases = paramsPreviouslyModifiedInCases;
	}
	@Override
	public boolean isInstance() {
		return isInstance;
	}

	public void setInstance(boolean isInstance) {
		this.isInstance = isInstance;
	}
	@Override
	public Set<NodeWrapper> paramsPreviouslyModifiedForSwitch() {
		return paramsPreviouslyModifiedInCases;
	}
}
