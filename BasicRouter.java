/**
 * BasicRouter Class
 * 
 * This is a base class for class Router
 * which implements asynchronous distance-vector
 * routing algorithm.
 * 
 * CPSC 441
 * Assignment 4
 *      
 * @author 	Majid Ghaderi
 * @version	2019
 *
 */

package cpsc441.a4.router;

import java.net.*;
import java.util.*;


public abstract class BasicRouter extends Thread {

	// global logger name based on the class name
	public static final String GLOBAL_LOGGER_NAME = BasicRouter.class.getName();
	
	// if two routers are not reachable, their path cost is set to COST_INFINITY
	protected static final int COST_INFINITY = 256;
	
	
	// router information
	protected String name;	 	// router name
	protected int port;		 	// router port number
	protected int keepalive; 	// time interval for keepalive beacons 
	protected int inactivity;	// time interval to purge inactive neibhbors

	
	/**
	 * Constructor to initialize the rouer instance 
	 * 
	 * @param routerName			Unique name of the router
	 * @param routerPort			UDP port number used by the router for sending/receiving routing messages
	 * @param keepaliveInterval		Time interval for sending keepalive beacons to neighboring routers (in milli-seconds)
	 * @param inactivityInterval	Time interval to purge inactive neighbors (in milli-seconds)
	 */
	public BasicRouter(String routerName, int routerPort, int keepaliveInterval, int inactivityInterval) {
		name = routerName;
		port = routerPort;
		keepalive = keepaliveInterval;
		inactivity = inactivityInterval;
	}


	
	/**
	 * Starts the router 
	 *
	 * This is the main method that implements the DV algorithm
	 */
	public abstract void run();

	
	
	/**
	 * Signals the router to terminate 
	 *
	 */
	public abstract void shutdown();

	
	
	/**
	 * Gets the local DV table as a Map.
	 * 
	 * Each entry in the Map has a key and a value:
	 * 	key: name of a destination router
	 *	value: cost of the least-cost path to the destination specified by key
	 *
	 *@return	the local DV table at the router
	 */
	public abstract Map<String, Integer> getDvTable();
	
	
	
	/**
	 * Gets the local FW table as a Map 
	 *
	 * Each entry in the Map has a key and a value:
	 * 	key: name of a destination router
	 *	value: the next hop router to reach the destination specified by key
	 *
	 *@return	the local FW table at the router
	 */
	public abstract Map<String, String> getFwTable();

	
	
	/**
	 * Enumerates all available network interfaces on the local host, and
	 * returns a list of all active interface addresses that are not loopback.
	 * These are the interfaces that ahsould be used to broadcast
	 * routing packets to neighbors. An IterfaceAddress includes: 
	 * IP address, subnet mask and broadcast address.
	 *
	 *@return	List of all active IPv4 network interface addresses
	 */
	protected List<InterfaceAddress> enumInterfaceAddresses() throws SocketException {
		List<InterfaceAddress> addressList = new ArrayList<>();

		// get the list of available interfaces 
		Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();

		//
		// for each active interface, enumerate all its IPv4 addresses
		// each interface can have multiple IP addresses
		//
		while (netInterfaces.hasMoreElements()) {
			NetworkInterface netface = netInterfaces.nextElement();

			if (!netface.isLoopback() && netface.isUp()) {
				List<InterfaceAddress> netfaceAddresses = netface.getInterfaceAddresses();

				// we are only interested in IPv4 addresses
				for (InterfaceAddress address : netfaceAddresses) {
					if (address.getAddress() instanceof Inet4Address)
						addressList.add(address);
				}
			}
		}
		
		return addressList;
	}
	
}
