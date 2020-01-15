package cpsc441.a4.router;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InterfaceAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import cpsc441.a4.router.DvPacket.DvTable;
import cpsc441.a4.router.DvPacket;


public class Router extends BasicRouter{
	private DatagramSocket broadcastSocket;
	private ConcurrentHashMap<String, String> fwTable; // forwarding table
	private ConcurrentHashMap<String, DvTable> dvMap; // a map of all DVs
	private ConcurrentHashMap<String, Future> futures;
	private ScheduledThreadPoolExecutor scheduler;		// thread manager for timeouts
	private boolean shutFlag = false;
	private final int THREAD_COUNT = 4;
	
	public Router(String routerName, int routerPort, int keepAliveTime, int inactivityInterval) {
		// no need to save parameters since accessible from parent class
		super(routerName, routerPort, keepAliveTime, inactivityInterval);
		
		// init tables
		fwTable = new ConcurrentHashMap<>(); 
		dvMap = new ConcurrentHashMap<>(); 
		futures = new ConcurrentHashMap<>();
		scheduler = new ScheduledThreadPoolExecutor(THREAD_COUNT);
		
		// init udp socket
		try {
			broadcastSocket = new DatagramSocket(routerPort);
			broadcastSocket.setBroadcast(true);
		} catch (SocketException se) {
			System.out.println("Failed to initialize udp socket, restart program");		// should probably exit
			se.printStackTrace();
		}
		
	}

	@Override
	public void run() {
		// init our table and forwarding 
		DvTable myTable = new DvPacket().new DvTable();
		// link cost to ourself = 0
		myTable.put(super.name, 0);
		dvMap.put(super.name, myTable);
		// our message belongs to us (trivial)
		fwTable.put(super.name, super.name);
		
		// broadcast existence to neighbours
		udpBroadcast(broadcastSocket, dvMap.get(super.name));
		
		// receive and process messages
		byte[] datagramBuff = new byte[1024*4];
		while (!shutFlag) {
			try {
				// possible bug, might use garbage data from previous messages (untested)
				DatagramPacket newPack = new DatagramPacket(datagramBuff, datagramBuff.length);
				broadcastSocket.receive(newPack);
				DvPacket receivedDV = new DvPacket(newPack);
				// start/restart timer; add new neighbour
				processPacket(receivedDV);
				// update our dv table
				if (calculateDV()) {
					// table changed therefore broadcast
//					System.out.println(super.name + " dv table changed");
					udpBroadcast(broadcastSocket, dvMap.get(super.name));
				}
				
			} catch (IOException ioe) {
				if (ioe instanceof SocketException || ioe instanceof SocketTimeoutException) {
					System.out.println("Socket timed out, maybe due to thread interrupt; expected");
					
				} else {
					System.out.println("Receiving data error");
					ioe.printStackTrace();
				}
				
			} catch (ClassNotFoundException cnfe) {
				System.out.println("Converting datagram to DvPacket error, system might not support library in use");
				cnfe.printStackTrace();
			} 
		}
		
	}

	@Override
	public void shutdown() {
		shutFlag = true;
		
		// clean up
		broadcastSocket.close();
		scheduler.shutdownNow();
		
	}

	@Override
	public Map<String, Integer> getDvTable() {
		return dvMap.get(super.name);
	}

	@Override
	public Map<String, String> getFwTable() {
		return fwTable;
	}
	
	// remove neighbour and all its connections
	// recalculates dv
	// broadcasts if necessary
	public boolean processInactiveNeighbour(String neighbourName) {
		// remove neighbour from dvMap and fwTable
		dvMap.remove(neighbourName);
		Iterator<Entry<String, String>> fwTabIter = fwTable.entrySet().iterator();
		while (fwTabIter.hasNext()) {
			Entry<String, String> currentEnt = fwTabIter.next();
			// if key forwards to neighbour then remove
			if (currentEnt.getValue().equalsIgnoreCase(neighbourName)) {
				fwTabIter.remove();
			}
		}
		// remove from my table
		dvMap.get(super.name).remove(neighbourName);
		// remove from timer map
		futures.remove(neighbourName);
		
		// recalculate DV
		if (calculateDV()) {
			udpBroadcast(broadcastSocket, dvMap.get(super.name));
		}
		
		return true;
	}
	
	// manages the timer and handles new/old neighbours
	// true if packet isn't empty
	// false if packet is empty
	private boolean processPacket(DvPacket newPack) {
		if (newPack.getSource().equalsIgnoreCase(super.name)) {
			System.out.println("Received my own packet therefore skip");
			return true;
		}
		printPacket(newPack);
		// stop timer assuming it exists
		if (futures.get(newPack.getSource()) != null) {
			// stop task if its not running yet
			System.out.println("Cancelling timer for " + newPack.getSource());
			try {
				futures.get(newPack.getSource()).cancel(false);
			} catch (NullPointerException npe) {
				System.out.println(newPack.getSource() + " timer must have been removed ahead of time due to inactivity");
			} catch (Exception e) {
				System.out.println("Unexpected behaviour");
			}
		}
		// false if table is empty
		if (newPack.getDvTable().isEmpty()) {
			System.out.println("Received empty table, unexptected behaviour");
			return false;
		}
		
		// set new neighbour table
		dvMap.put(newPack.getSource(), (DvTable)newPack.getDvTable());
		
		// update table
		DvTable myTable = dvMap.get(super.name);
		// add neighbour if new
		String neighbourName = newPack.getSource();
		Integer value = myTable.get(neighbourName);
		// either new neighbour or previously neighbour of a neighbour
		if (value == null || value > 1) {
			// value 1 since neighbour
			myTable.put(neighbourName, 1);
			// trivial forward
			fwTable.put(neighbourName, neighbourName);
		}
		
		// start/restart timer
		System.out.println("Starting timer for neighbour " + newPack.getSource());
		Future scheduledTask = scheduler.schedule(new InactiveThread(this, neighbourName), super.inactivity, TimeUnit.MILLISECONDS);
		futures.put(neighbourName, scheduledTask);
		
		return true;
	}
	
	private boolean calculateDV() {
		
		// copy old table for future comparison
		DvTable myOldTable = new DvPacket().new DvTable(dvMap.get(super.name));
		
		
		DvTable myTable = dvMap.get(super.name);
		// reset table cost
		Iterator<String> myTabIter = myTable.keySet().iterator();
		while (myTabIter.hasNext()) {
			// assumes every non neighbour cost > 1
			String currentKey = myTabIter.next();
			if (myTable.get(currentKey) > 1) {
				// set value to infinity
				myTable.put(currentKey, BasicRouter.COST_INFINITY);
			}
		}
			
		// recalculate new mins
		Iterator<String> dvMapIter = dvMap.keySet().iterator();
		while (dvMapIter.hasNext()) {
			String currentKey = dvMapIter.next();
			// ignore our table
			if (currentKey.equalsIgnoreCase(super.name)) continue;
				
			// calculate new mins and compare to old mins
			DvTable currentTable = (DvTable)dvMap.get(currentKey);
			Iterator<String> currTabIter = currentTable.keySet().iterator();
				
			while (currTabIter.hasNext()) {
				String currTabKey = currTabIter.next();
				int pathCost = 1 + currentTable.get(currTabKey);
				if (!myTable.containsKey(currTabKey)) {
					// new key
					myTable.put(currTabKey, pathCost);
					fwTable.put(currTabKey, currentKey);
				} else if (myTable.get(currTabKey) > 1 && pathCost < myTable.get(currTabKey)) {
					// lower cost
					myTable.put(currTabKey, pathCost);
					fwTable.put(currTabKey, currentKey);
				}
			}
		}
		
		// remove entry with infinity value
		myTabIter = myTable.keySet().iterator();
		while (myTabIter.hasNext()) {
			String currentKey = myTabIter.next();
			if (myTable.get(currentKey) == BasicRouter.COST_INFINITY) {
				myTabIter.remove();
				fwTable.remove(currentKey);
				System.out.println("Removed path to " + currentKey);
			}
		}
		
		// check for changes
		DvTable myNewTable = dvMap.get(super.name);
		Iterator<String> newTabIter = myNewTable.keySet().iterator();
		while (newTabIter.hasNext()) {
			String currentKey = newTabIter.next();
			if (!myOldTable.containsKey(currentKey)) {
				// new key implies change
				return true;
			} else if (myOldTable.get(currentKey) != myNewTable.get(currentKey)) {
				// new value
				return true;
			}
		}
		// otherwise no change
		return false;
	}
	
	// special broadcast method for a new thread
	// return WIP
	public boolean keepAliveBroadcast() {
		udpBroadcast(broadcastSocket, dvMap.get(super.name));
		// delete cancelled jobs
		scheduler.purge();
//		System.out.println("Current state of router = " + super.name);
//		System.out.println(dvMap.get(super.name).toString());
		return true;
	}
	
	private void printPacket(DvPacket aPack) {
		System.out.println("Received packet from " + aPack.getSource() + ": " + aPack.getDvTable().toString());
	}
	
	// broadcasts table to all neighbours
	// starts/restarts timer
	private boolean udpBroadcast(DatagramSocket soc, DvTable myTable) {
	
		// cancel current timer if not running
		if (futures.get(super.name) != null) {
			System.out.println("Stopped my timer");
			futures.get(super.name).cancel(false);
		}
		System.out.println("Broadcasting my table: " + myTable.toString());

		// broadcast current dv to neighbours
		try {
			List<InterfaceAddress> neigbourAdds = super.enumInterfaceAddresses();
			// convert out map into a packet
			DvPacket myPacket = new DvPacket(super.name, myTable);
			for (InterfaceAddress anAdd: neigbourAdds) {
				// dont send packet to self
//				if (anAdd.getAddress().isLinkLocalAddress() || anAdd.getAddress().isAnyLocalAddress()) continue;
				System.out.println("Broadcasting to " + anAdd.getAddress().getHostName());
				soc.send(myPacket.toDatagram(anAdd.getBroadcast(), super.port));
			}

		} catch (SocketException se) {
			System.out.println("Broadcasting error socket exception");
			se.printStackTrace();
			return false;
		} catch (IOException ioe) {
			System.out.println("Sending packet error in broadcast function");
			ioe.printStackTrace();
			return false;
		}
		
		// start/restart timer
		System.out.println("Started keepAlive timer");
		Future keepAliveTask = scheduler.schedule(new KeepAliveThread(this), super.keepalive, TimeUnit.MILLISECONDS);
		futures.put(super.name, keepAliveTask);
		
		
		return true;
	}
	

}
