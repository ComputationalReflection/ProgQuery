package tests.news.classesDependencies;

import java.util.ArrayList;
import java.util.Collections;

import tests.news.OtherClass;

public class ClassUse implements Interface {

	private ArrayList<String> l;

	public int m() {
		return Collections.emptyMap().size();
	}

	public int m(Integer i) {
		return Collections.emptyMap().size();
	}

	public int m(String s) {
		return Collections.emptyMap().size();
	}

	public int m(Object o) {
		return Collections.emptyMap().size();
	}

	public int mm() {
		m("JBO�L");
		m(1);
		m("MLL".getClass());
		return new ExtraClass().OTHER.n;
	}

	public int mmm() {

		return getE().getO().n;
	}

	public ExtraClass getE() {
		return new ExtraClass();
	}

	public Object getI() {
		return OtherClass.InnerClass.n;
	}
}
