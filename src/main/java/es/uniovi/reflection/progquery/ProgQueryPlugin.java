package es.uniovi.reflection.progquery;

import com.sun.source.util.JavacTask;
import org.kohsuke.MetaInfServices;
import java.time.ZonedDateTime;

@MetaInfServices(com.sun.source.util.Plugin.class)
public class ProgQueryPlugin implements com.sun.source.util.Plugin {

	private static final String PLUGIN_NAME = "es.uniovi.reflection.progquery.ProgQueryPlugin";

	@Override
	public void init(JavacTask task, String[] args) {
		Thread.currentThread().setContextClassLoader(ProgQueryPlugin.class.getClassLoader());

		final String ANONYMOUS_PROGRAM = "ANONYMOUS_PROGRAM_", ANONYMOUS_USER = "ANONYMOUS_USER";
		String programID, userID;
		if (args.length == 0) {
			programID = ANONYMOUS_PROGRAM + ZonedDateTime.now();
			userID = ANONYMOUS_USER;
		} else if (args[0].contains(";")) {
			String[] IDInfo = args[0].split(";");
			programID = IDInfo[0];
			userID = IDInfo[1];
		} else {
			// ONLY PROGRAM ID
			programID = args[0];
			userID = ANONYMOUS_USER;
		}

		ProgQuery progquery = null;
		//TODO: To be completed
	}

	@Override
	public String getName() {
		return PLUGIN_NAME;
	}	
}
