package examples.CFG;

public class BreakTest {

	public static void main(final String[] args) {
		final int index=0;
		// System.out.println("A");
		// // break;
		// System.out.println("B");
		// b: {
		// if ("B".length() == 1)
		// break b;
		// // break a sin label da error de compilaci�n, s�lo deja dentro de un
		// // loop o switch
		// System.out.println("UNREACHEABLE");
		// if (true)
		// ;
		// // ERROR COMPILACI�N
		// // continue b;
		// }
		//
		// b: do {
		// if (true) {
		// // ERROR duplicate label b
		// // b:;
		// a: ;
		// // ERROR misssing label a
		// // break a;
		// }
		//
		// continue b;
		// } while (args[0].contains("a"));
		switch (args[0]) {
		}
		a: {
			System.out.println("BLOC K INI");
			if ("A".contains("A"))
				break a;
			System.out.println("BLOCK END");

		}

		a: if (true)
			b: {
				if ("A".contains("A"))
					break a;
				else
					break b;
			}
		s: synchronized (args) {

		}
		int i = 1;
		while (i < 20) {
			b: {

				if (i % 3 == 0)
					break b;
				System.out.println("BLOCK END" + i);

			}

			if (i % 5 == 0)
				break;
			System.out.println("LOOP END" + i);
			i++;
		}
		final String s2 = "J";
		String s = "D";
		switch (s) {
		case "A":
			System.out.println("A");
		case "B": {
		}
			System.out.println("B");
			// while (true)
			// if ("A".contains("A"))
			// break;
			// else
			// break sw;
		case "D":
			switch (2 + 5) {
			case 3:
				break;
			case 89:
			}
		case "C": {
			System.out.println("C");
			break;
		}
		case "A"+"B":
		default:
			System.out.println("DEFAULT");

			// Aqu� podemos dar fallo , no tiene sentido, es como a�adir m�s
			// sentencias al default
		case "JH":
			System.out.println("JH");
		}
	}

	static String a = "B";

	private static String getC() {
		System.out.println("EVAL GETC");
		String ret = a;
		if (a.contentEquals("X"))
			a = "C";
		return ret;
	}
}
