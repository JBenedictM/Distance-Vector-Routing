
/**
 * A driver for Router class
 * 
 * CPSC 441
 * Assignment 4
 * 
 * @author 	Majid Ghaderi
 * @version 2019
 *
 */

package cpsc441.a4.router;

import java.io.*;
import java.util.*;
import java.util.logging.*;


public class RouterDriver {
	
	private static final Logger logger = Logger.getLogger(BasicRouter.GLOBAL_LOGGER_NAME);
	private final static int TERM_WAIT_TIME = 5 * 1000; // milli-seconds
	
	
	/**
	 * main method
	 */
	public static void main(String[] args) {
		
		try {
			// parse command line args
			HashMap<String, String> params = parseCommandLine(args);
			
			// input router id is required
			if (!params.containsKey("-n")) {
				System.out.println("incorrect usage, router name is required");
				System.out.println("try again");
				System.exit(0);
			}
				
			// set the parameters
			Level logLevel = Level.parse( params.getOrDefault("-v", "info").toUpperCase() ); // log levels: all, info, off
			String routerName = params.get("-n"); // router name
			int routerPort = Integer.parseInt( params.getOrDefault("-p", "2525") ); // router port number
			int keepaliveInterval = Integer.parseInt( params.getOrDefault("-t", "2000") ); // duraiton of keepalive interval 
			int inactivityInterval = Integer.parseInt( params.getOrDefault("-i", "5000") ); // duraiton of inactivity interval 

			// standard output
			setLogLevel(logLevel);

			System.out.printf("starting router %s with parameters:\n", routerName);
			System.out.printf("router local port number: %d\n", routerPort);
			System.out.printf("keepalive update interval: %d (milli-seconds)\n", keepaliveInterval);
			System.out.printf("inactivity interval: %d (milli-seconds)\n", inactivityInterval);
			System.out.println();
			
			BasicRouter router = new Router(routerName, routerPort, keepaliveInterval, inactivityInterval);
			router.start();
			
			System.out.println("router started. Type \"quit\" to stop");
			System.out.println(".....................................");

			Scanner keyboard = new Scanner(System.in);
			while ( !keyboard.next().equals("quit") );
			
			System.out.println();
			System.out.println("sending shutdown signal");
			router.shutdown();
			
			try {
	            router.join(TERM_WAIT_TIME);
	        } catch (InterruptedException e) {
	            // Ok, ignore
	        	//		 -maghid 2019
	        }
		
			System.out.println("router stopped");
			System.out.println();
			System.out.println("routing table at: " + routerName);
			System.out.print(prettyPrint(router));
			
			keyboard.close();
			
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

		// get rid of any lingering threads/timers
		System.exit(0);
	}

	
	// parse command line arguments
	private static HashMap<String, String> parseCommandLine(String[] args) {
		HashMap<String, String> params = new HashMap<String, String>();

		int i = 0;
		while ((i + 1) < args.length) {
			params.put(args[i], args[i+1]);
			i += 2;
		}
		
		return params;
	}
	
	
	// set the global log level and format
	private static void setLogLevel(Level level) {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s %n");
		
		ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(level);
        logger.addHandler(handler);		
		logger.setLevel(level);
        logger.setUseParentHandlers(false);
	}
	
	
	// format routing table as String
	private static String prettyPrint(BasicRouter router) {
		String table = "";
		
		table += "-------------------------\n";
		
		// print in sorted order
		Map<String, Integer> dvt = router.getDvTable();
		Map<String, String> fwt = router.getFwTable();
		
		List<String> routerList = new ArrayList<String>(dvt.keySet());
		Collections.sort(routerList);
		
		for (String dest: routerList) 
			table += String.format("  mincost[%s] = %d via %s\n", dest, dvt.get(dest), fwt.get(dest));
		
		table += "-------------------------\n";
		
		return table;
	}
	
}
