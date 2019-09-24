
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Random;

public class DnsClient {
	enum QueryType {
		A, MX, NS, CNAME, OTHER
	};
	public static void main(String args[]) throws Exception {

		//default values 
		QueryType type = QueryType.A;
		int timeoutVal = 5;
		int maxRetries = 3; 
		int portNumber = 53;
		
		String server = "";
		String name = "";
		int domainNameLength = 0;
		
		boolean authoritative = false;
		String auth_name = "";

		// byte arrays to store sending and receiving data
		byte[] sendData = new byte[1024];
		byte[] receiveData = new byte[1024];

		DatagramPacket sendPacket = null;
		DatagramPacket receivePacket = null;

		//values to be set 
		int QR, AA, TC, RD, RA, Z;
		int RCODE, QDCOUNT, ANCOUNT, NSCOUNT, ARCOUNT;

		// random number for every query header ID number
		int[] rID = new int[2];
		byte[] qID = new byte[2];
		new Random().nextBytes(qID);
		
		// parse input from the command line
		for (int i = 0; i < args.length; i++) {

			if (args[i].equals("-t")) {
				try {
					timeoutVal = Integer.parseInt(args[i + 1]);
				} catch (Exception e) {
					throw new IllegalArgumentException(
							"ERROR\tIncorrect input syntax: Timeout value is not an integer, please retry");
				}
			}

			else if (args[i].equals("-r")) {
				try {
					maxRetries = Integer.parseInt(args[i + 1]);
				} catch (Exception e) {
					throw new IllegalArgumentException(
							"ERROR\tIncorrect input syntax: Max retries is not an integer, please retry");
				}
			}

			else if (args[i].equals("-p")) {
				try {
					portNumber = Integer.parseInt(args[i + 1]);
				} catch (Exception e) {
					throw new IllegalArgumentException(
							"ERROR\tIncorrect input syntax: Port number is not an integer, please retry.");
				}
			}

			else if (args[i].equals("-mx")) {
				type = QueryType.MX;
			} 
			else if (args[i].equals("-ns")) {
				type = QueryType.NS;
			}
			else if (i == (args.length - 2)) {
				server = args[i];
			}
			else if (i == (args.length - 1)) {
				name = args[i];
			}
		}

		// byte array for parsed server name
		byte[] ipAddr = new byte[4];
		if (!server.substring(0, 1).equals("@")) {
			throw new IllegalArgumentException(
					"ERROR\tIncorrect input syntax: Incorrect syntax for server IP Address.");
		}
		server = server.substring(1);
		String[] ipAddrStr = server.split("\\.", 4);
		int k;

		for (k = 0; k < ipAddrStr.length; k++) {
			try {
				ipAddr[k] = (byte) Integer.parseInt(ipAddrStr[k]);
			} catch (Exception e) {
				throw new IllegalArgumentException(
						"ERROR\tIncorrect input syntax: Incorrect syntax for server IP Address.");
			}
		}

		if (k != 4) {
			throw new IllegalArgumentException(
					"ERROR\tIncorrect input syntac: Incorrect syntax for server IP Address.");
		}

		String[] sectionsOfName = name.split("\\."); // this gets the total different amount parts of the name seperated by "."
		for (int i = 0; i < sectionsOfName.length; i++) {
			domainNameLength += sectionsOfName[i].length() + 1; // includes a byte for every character and the number
																// that will be used in the name for every section
		}

		// create the header byte buffer for the DNS query
		ByteBuffer header = ByteBuffer.allocate(12); // the header contains 12 bytes

		header.put(qID); // random ID number
		header.put((byte) 0x01); // header line 2 (QR, OPcode, AA, TC, RD, RA, Z, RCODE)
		header.put((byte) 0x00); // buffer line 2
		header.put((byte) 0x00); // buffer qd
		header.put((byte) 0x01); // qd set to 1
		//ANCOUNT, NSCOUNT, ARCOUNT in the query are initialized to 0, they dont need to be explicitly set

		// create the question byte buffer for the DNS query

		ByteBuffer question = ByteBuffer.allocate(domainNameLength + 5); // the length of the question is the name and
																			// the additional bytes for the terminating
																			// byte, qtype and qclass

		// QNAME
		for (int i = 0; i < sectionsOfName.length; i++) {
			question.put((byte) sectionsOfName[i].length());
			for (int j = 0; j < sectionsOfName[i].length(); j++) {
				question.put((byte) ((int) sectionsOfName[i].charAt(j)));
			}
		}
		question.put((byte) 0x00); // terminating byte for qname
		question.put((byte) 0x00); //buffer for qtype 
		
		// QTYPE
		if (type == QueryType.A) {
			question.put((byte) 0x0001); // A type query
		} else if (type == QueryType.MX) {
			question.put((byte) 0x000f); // Mail server query
		} else {
			question.put((byte) 0x0002); // Name server query
		}

		// QCLASS
		question.put((byte) 0x0000); //buffer for qclass
		question.put((byte) 0x0001); // same for every query 

		ByteBuffer query = ByteBuffer.allocate(12 + domainNameLength + 5); // query is the size of the question (12) and
																			// header (domain + 5)
		query.put(header.array()); // add the header to the query
		query.put(question.array()); // add the question to the query

		InetAddress addr = InetAddress.getByAddress(ipAddr); //get internet address 
		DatagramSocket clientSocket = new DatagramSocket(); //create socket 

		sendData = query.array(); //add query to sendData query 
		
		System.out.println("DnsClient sending request for " + name);
		System.out.println("Server: " + server);
		System.out.println("Request Type: " + type.toString());

		// variables to time packet send and receive 
		long startingTime = 0;
		long endingTime = 0;

		int i;
		
		for (i = 0; i < maxRetries; i++) { //try to receive packet for the length of timeout time, for max amount of retries 
			//send the packet through client socket 
			sendPacket = new DatagramPacket(sendData, sendData.length, addr, portNumber);
			clientSocket.send(sendPacket);
			receivePacket = new DatagramPacket(receiveData, receiveData.length);
			clientSocket.setSoTimeout(timeoutVal * 1000); // set the client socket timeout time (convert to milliseconds)
			
			try { //try to receive the packet 
				startingTime = System.currentTimeMillis();
				clientSocket.receive(receivePacket);
				endingTime = System.currentTimeMillis();
			} catch (Exception e) { //will throw a timeout exception once exceeded set timeout 
				continue; //continue to try again to receive the packet 
			}
			if (receivePacket != null) { // means you have received a packet
				break; //break out of loop 
			}
		}

		clientSocket.close(); //close the socket 
		
		if (maxRetries == i) {
			throw new RuntimeException("ERROR\tMaximum number of retries " + maxRetries + " exceeded");
		}
		//i dont think this is needed??? TO DO 
		if (receivePacket == null) {
			throw new Exception("NOTFOUND");
		}

		byte[] response = receivePacket.getData();

		//parse the header values of the response 
		rID[0] = response[0] & 0xff; //response id 
		rID[1] = response[1] & 0xff; //response id 
		QR = bitSet(response[2], 7) & 0xff; //QR bit
		AA = bitSet(response[2], 2) & 0xff; //AA bit
		TC = bitSet(response[2], 1) & 0xff; //TC bit
		RD = bitSet(response[2], 0) & 0xff; //RD bit
		RA = bitSet(response[3], 7) & 0xff; //RA bit 
		RCODE = response[3] & 0x0f; //RCODE byte
		QDCOUNT = twoBytesToShort(response[4], response[5]); //QDCOUNT 2 bytes
		ANCOUNT = twoBytesToShort(response[6], response[7]); //ANCOUNT 2 bytes
		NSCOUNT = twoBytesToShort(response[8], response[9]); //NSCOUNT 2 bytes
		ARCOUNT = twoBytesToShort(response[10], response[11]); //ARCOUNT 2 bytes 

		// debug
		// System.out.println("Response QR is: " + QR);
		// System.out.println("Response AA is: " + AA);
		// System.out.println("Response TC is: " + TC);
		// System.out.println("Response RD is: " + RD);
		// System.out.println("Response RA is: " + RA);
		// System.out.println("Response RCODE is: " + RCODE);
		// System.out.println("Response QDCOUNT is: " + QDCOUNT);
		// System.out.println("Response ANCOUNT is: " + ANCOUNT);
		// System.out.println("Response NSCOUNT is: " + NSCOUNT);
		// System.out.println("Response ARCOUNT is: " + ARCOUNT);

		authoritative = checkResponseHeaderValues(QR, AA, RA); // check QR, AA values
		
		if (authoritative) { //set authoritative variable 
			auth_name = "auth";
		} else {
			auth_name = "nonauth";
		}

		checkRCodeValue(RCODE); // check RCODE value 
		
		System.out.println(
				"Response received after " + ((endingTime - startingTime) / 1000.0 + " seconds (" + i + " retries)"));

		int indexResponse = sendPacket.getLength(); // response answer starts at end of header, get index, 32 bits 
		
		System.out.println("*** Answer Section (" + ANCOUNT + " records) ***");
		
		int recordSize = 0;
		if(ANCOUNT <= 0){
			System.out.println("NOTFOUND"); //no answer records found 
		} else {
			for(int r = 0; r < ANCOUNT; r++){ //parse all answer records 
				recordSize = (parseRecords(receivePacket, indexResponse, auth_name)) + 12; //size of rdata + record values(name, type, class, ttl, rlength)(12)
				indexResponse += recordSize; //increment by size of previous record 	
			}
		}
		for(int q = 0; q < NSCOUNT; q++){//parse all the authority records (no output, to index correctly)
			recordSize = NSRecordsSize(receivePacket, indexResponse) + 12;
			indexResponse += recordSize; //increment by size of previous record 
		}

		System.out.println("*** Additional Section (" + ARCOUNT + " records) ***");
		
		if(ARCOUNT <= 0){
			System.out.println("NOTFOUND"); //no additional records found
		}else{
			for(int s = 0; s < ARCOUNT; s++){ //parse all additional records 
				recordSize = (parseRecords(receivePacket, indexResponse, auth_name)) + 12; //size of rdata + record values(name, type, class, ttl, rlength)(12)
				indexResponse += recordSize; //increment by size of previous record
			}
		}
	}

	private static int bitSet(byte b, int bit) { // where "bit" refers to how many bits need to be shifted to access the
													// correct byte
		return (b >> bit) & 1;
	}

	public static short twoBytesToShort(byte b1, byte b2) { //method to combine two bytes to a short
		return (short) ((b1 << 8) | (b2 & 0xFF));
	}

	public static void checkRCodeValue(int rCode) {

		switch (rCode) {
		case 0: // no error condition
			break;

		case 1:
			throw new RuntimeException(
				"ERROR\tFormat error: the name server was unable to interpret the query");

		case 2:
			throw new RuntimeException(
					"ERROR\tServer failure: the name server was unable to process this query due to a problem with the name server");

		case 3: 
			throw new RuntimeException("NOTFOUND");
		case 4:
			throw new RuntimeException("ERROR\tNot implemented: the name server does not support the requested kind of query");

		case 5:
			throw new RuntimeException(
					"ERROR\tRefused: the name server refuses to perform the requested operation for policy reasons");
		}
	}

	public static boolean checkResponseHeaderValues(int QR, int AA, int RA) {
		if (QR != 1) {
			throw new RuntimeException("Error\tPacket received is not a response.");
		}
		if(RA != 1){
			System.out.println("Error\tServer does not support recursive queries"); 
		}
		if (AA == 1) { // else it stays false and is not authoritative
			return true;
		}
		return false;
	}

	public static String parseAliasName(DatagramPacket response, int indexOffset){ //parse the alias name, and account for packet compression and pointers within the response 
		String aliasName = "";
		byte[] responseCopy = response.getData();
		int count = 0; 
		int sectionSize = 0; //domain names are split up into sections (seperated by numbers, but represent "." in the domain outline)

		while(responseCopy[indexOffset + count] != 0){
			if(sectionSize == 0){ //the first section
				if (count != 0){ //it is not the start of the alias 
				aliasName += "."; //replace the number witsh a period as done in qname 
				}
			sectionSize = responseCopy[indexOffset + count]; //gets the size of the section we are about to parse 
			}
			else if((sectionSize & 0xC0) == 0xC0){ //indicates that the next byte is a pointer, the next byte will be the offset value
				indexOffset = ((sectionSize & 0x0000003f) << 8) + (responseCopy[indexOffset + count] & 0xff); //reset the index offset to zero, and then get the offset value from the beginning of the header 
				sectionSize = responseCopy[indexOffset]; //now at correct position that pointer was indicating 
				count = 0;
			} else { //it is a character within a section 
				aliasName += (char)responseCopy[indexOffset + count];
				sectionSize--;
			}
			count++;
		}
		return aliasName;
	}

	public static int parseRecords(DatagramPacket receivePacket, int indexResponse, String auth_name){
		
		byte[] response = receivePacket.getData();

		short typeResponseShort = twoBytesToShort(response[indexResponse + 2], response[indexResponse + 3]);
		QueryType responseType = QueryType.A; //default 

		if (typeResponseShort == (short) 0x0001) {
			responseType = QueryType.A;
		}
		else if (typeResponseShort == (short) 0x0005) {
			responseType = QueryType.CNAME;
		} 
		else if (typeResponseShort == (short) 0x0002) {
			responseType = QueryType.NS;
		} 
		else if (typeResponseShort == (short) 0x000f) {
			responseType = QueryType.MX;
		} else { 
			System.out.println("ERROR\tQuery type of response unknown.");
			responseType = QueryType.OTHER;
		}
		
		short classResponse = twoBytesToShort(response[indexResponse + 4], response[indexResponse + 5]);

		if (classResponse != (short)0x0001) {
			throw new RuntimeException("ERROR\tThe class number is not equal to 1.");
		}

		// ttl 32 bit
		byte[] TTL = { response[indexResponse + 6], response[indexResponse + 7], response[indexResponse + 8],
				response[indexResponse + 9]};
		ByteBuffer ttlWrapped = ByteBuffer.wrap(TTL);
		int ttl = ttlWrapped.getInt();
	
		short rdLengthResponse = twoBytesToShort(response[indexResponse + 10], response[indexResponse + 11]);
			if(responseType == QueryType.OTHER){
				return rdLengthResponse;
			}
			if (responseType == QueryType.A) { // rdata is the IP Address

				int domainIP_1 = response[indexResponse + 12] & 0xff;
				int domainIP_2 = response[indexResponse + 13] & 0xff;
				int domainIP_3 = response[indexResponse + 14] & 0xff;
				int domainIP_4 = response[indexResponse + 15] & 0xff;
				System.out.println("IP\t" + domainIP_1 + "." + domainIP_2 + "." + domainIP_3 + "." + domainIP_4 + "	\t" + ttl + "\t" + auth_name);
			} else if (responseType == QueryType.NS) { // the name of the server with the same format as qname
				System.out.println("NS\t" + parseAliasName(receivePacket, indexResponse + 12) + "\t" + ttl + "\t" + auth_name);
			} else if (responseType == QueryType.CNAME) {
				System.out.println("CNAME\t" + parseAliasName(receivePacket, indexResponse + 12) + "\t" + ttl + "\t" + auth_name);
			} else { 
				short preference = twoBytesToShort(response[indexResponse + 12], response[indexResponse + 13]);
				System.out.println("MX\t" + parseAliasName(receivePacket, indexResponse + 14) + "\t" + preference + "\t" + ttl + "\t" + auth_name);
			}		
			return rdLengthResponse;
	}

	public static int NSRecordsSize(DatagramPacket receivePacket, int indexResponse){
		//the size of the rddata field is held at the 10th byte from the end of the header, will return the size of the Authority Record 
		byte[] response = receivePacket.getData();
		short rdLengthResponse = twoBytesToShort(response[indexResponse + 10], response[indexResponse + 11]);
		
		return (int)rdLengthResponse;
	}
}
