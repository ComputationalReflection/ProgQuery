package examples.pdg;

import examples.cmu.A;

public class LocalVars {

	private  int a;

	public void m() {
		new LocalVars().a--;
		a--;
		System.out.println(new A().m);
		int c=0;
		c++;
		//
//		{
//			final int a = 2, b = 3, c = new A().m;
//			System.out.println(a + b + c);
//			m();
//		}
//		while (true) {
//			String c, a = "", b;
//			a.split("IHJ");
//			break;
//		}
//		if ("IJIJ".contains("B")) {
//			char a;
//			a = 'v';
//			this.a++;
//		}
//		a++;
//		class a {
//			int a;
//
//			public void m2() {
//				new a();
//				a++;
//				String a = "K";
//				System.out.println(a);
//				this.a--;
//				System.out.println(LocalVars.a);
//			}
//		}
//
//		System.out.println(new a().a);
	}
}
