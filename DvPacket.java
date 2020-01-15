 
/**
 * Class DvPacket
 * 
 * DvPacket defines the structure of messages used to 
 * exchange routing information between routers.
 *
 * Each packet has two members:
 * 		1. source: the name of the router sending the packet
 * 		2. dvt: the DV table of the sending router as a Map 
 *
 * DvPackdet is designed to work with UDP DatagramPackets via methods:
 * 		1. DvPacket(DatagramPacket): to de-encapsulate the DvPacket object from the datagram object
 * 		2. toDatagram(): to encapsulate the DvPacket object in a datagram object
 * 
 * 
 * @author 	Majid Ghaderi
 * @version	2019
 *
 */

package cpsc441.a4.router;

import java.util.*;
import java.io.*;
import java.net.*;


public class DvPacket implements Serializable {
	
    // routing information
	private String  source;	// name of the router sending this packet
	private DvTable dvt;	// DV table at the sending router


	
	/**
	 * Default constructor
	 * 
	 * Creates an empty packet
	 */
	public DvPacket() {
		source = "";
		dvt = new DvTable();
	}
	
	
	/**
	 * Constructor
	 * 
	 * @param router	name of the sending router
	 */
	public DvPacket(String router) {
		this();
		source = router;
	}

	
	/**
	 * Constructor
	 * 
	 * @param router	name of the sending router
	 * @param table		DV Table of the sending router as a Map
	 */
	public DvPacket(String router, Map<String, Integer> table) {
		this(router);
		dvt.putAll(table);
	}

	
	/**
	 * Copy constructor
	 * 
	 * @param packet	the dvr packet to be copied
	 */
	public DvPacket(DvPacket packet) {
		this(packet.source, packet.dvt);
	}

	
	/**
	 * Constructor
	 * 
	 * creates a DvPacket object and fills its source/dvt
	 * based on the payload of the datagram object.
	 * 
	 * @param datagram	the datagram object to be copied
	 */
	public DvPacket(DatagramPacket datagram) throws IOException, ClassNotFoundException {
		ByteArrayInputStream byteStream = new ByteArrayInputStream(datagram.getData());
		ObjectInputStream objectStream = new ObjectInputStream(byteStream);

		source = (String) objectStream.readObject(); 
		dvt = (DvTable) objectStream.readObject(); 
	}

	
	/**
	 * Creates a DatagramPacket object and copies
	 * source/dvt as the payload of the datagram object.
	 * 
	 * @param	address	IP address of the datagram destination
	 * @param	port	port number of the datagram destination
	 * 
	 * @return	a datagram encapsulating the DvPacket
	 */
	public DatagramPacket toDatagram(InetAddress address, int port) throws IOException {
		DatagramPacket datagram = toDatagram();
		
		datagram.setAddress(address);
		datagram.setPort(port);
		
		return datagram;
	}

	
	// helper method to copy source/dvt to  payload of a datagram
	private DatagramPacket toDatagram() throws IOException {
		ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
		ObjectOutputStream objectStream = new ObjectOutputStream(byteStream);
		
		objectStream.writeObject(source);
		objectStream.writeObject(dvt);
		
		return new DatagramPacket(byteStream.toByteArray(), byteStream.size());
	}
	
	
	/**
	 * Get method for source
	 * 
	 */
	public String getSource() {
		return source;
	}
	
	
	/**
	 * Get method for dvt
	 * Returns a deep copy of dvt
	 * 
	 */
	public Map<String, Integer> getDvTable() {
		return new DvTable(dvt);
	}
	
	
	/**
	 * Returns the length of the DvPacket object
	 * in units of byte.
	 * 
	 * @return length of the DvPacket object in bytes
	 * 
	 */
	public int getLength() throws IOException {
		return toDatagram().getLength();
	}
	
	/**
	 * Returns a String representation of the packet.
	 * This string can be used for logging pruposes.
	 * 
	 * @return	a string representation of the packet
	 */
	public String toString() {
		return String.format("DV[%s]: %s", source, dvt);
	}

	
	// ** HashMap Wrapper **
	// inner class definition for convenience
	// DV table at a router represented as a Map
	class DvTable extends HashMap<String, Integer> {
		public DvTable() {
			super();
		}
		
		public DvTable(Map<String, Integer> dvt) {
			super(dvt);
		}
		
		public DvTable(DvTable dvt) {
			super(dvt);
		}
	}
	
	
    /**
     * example usage
     * 
     */
	public static void main(String[] args) {
		Map<String, Integer> dvt = new HashMap<String, Integer>();
		DvPacket packet;
		
		try {
			dvt.put("r0", 0);
			packet = new DvPacket("r0", dvt);
			System.out.println(packet.toString() + " (length: " + packet.getLength() + " bytes)");
			
			dvt.put("r1", 1);
			packet = new DvPacket("r0", dvt);
			System.out.println(packet.toString() + " (length: " + packet.getLength() + " bytes)");
			
			dvt.put("r2", 1);
			packet = new DvPacket("r0", dvt);
			System.out.println(packet.toString() + " (length: " + packet.getLength() + " bytes)");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
