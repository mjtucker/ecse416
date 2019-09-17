
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Random;

public class DnsClient {
	enum QueryType {A, MX, NS};
	
	public static void main(String args[]) throws Exception {
		
		QueryType type = QueryType.A;
		
		int timeoutVal = -127;
		int maxRetries = -127;
		int portNumber = -127;
		String server = "";
		String name = "";
		int domainNameLength = 0;
		
		byte[] sendData = new byte[1024];		
		byte[] receiveData = new byte[1024];
		
		//Random randNum = new Random();
		//int qid = randNum.nextInt(1 + Short.MAX_VALUE - Short.MIN_VALUE);
		
		//random number for query header ID number 
		byte[] qID = new byte[2]; 
		new Random().nextBytes(qID);
		
		//parse input from the command line 
		for (int i = 0; i < args.length; i++) {

			if (args[i].equals("-t")) {
				try {
					timeoutVal = Integer.parseInt(args[i + 1]);
				} catch (Exception e) {
					System.out.println("Timeout value is not an integer, please retry");
				}
			}

			else if (args[i].equals("-r")) {
				try {
					maxRetries = Integer.parseInt(args[i + 1]);
				} catch (Exception e) {
					System.out.println("Max retries is not an integer, please retry");
				}
			}

			else if (args[i].equals("-p")) {
				try {
					portNumber = Integer.parseInt(args[i + 1]);
				} catch (Exception e) {
					System.out.println("Port number is not an integer, please retry");
				}
			}

			else if (args[i].equals("-mx")) {

				type = QueryType.MX;

			} else if (args[i].equals("-ns")) {

				type = QueryType.NS;
			}

			else if (i == (args.length - 2)) {
				server = args[i];
			}

			else if (i == (args.length - 1)) {
				name = args[i];
			}
		}

		System.out.println("Timeout = " + timeoutVal);
		System.out.println("Max Retries = " + maxRetries);
		System.out.println("Port Number = " + portNumber);
		System.out.println("Type = " + type.toString());
		System.out.println("Server: = " + server);
		System.out.println("Name: = " + name);

		// setting default values
		if (timeoutVal == -127)
			timeoutVal = 5;
		if (maxRetries == -127)
			maxRetries = 3;
		if (portNumber == -127)
			portNumber = 53;

		// create byte array of server
		byte[] ipAddr = new byte[4];
		if (!server.substring(0, 1).equals("@")) {
			throw new Exception("Incorrect syntax for server IP Address.");
		}
		server = server.substring(1);

		String[] ipAddrStr = server.split("\\.", 4);

		for (int k = 0; k < ipAddrStr.length; k++) {

			try {
				ipAddr[k] = (byte) Integer.parseInt(ipAddrStr[k]);
			} catch (Exception e) {
				System.out.println("Incorrect syntax for server IP Address.");
			}
		}
		
		//find byte size of domain name 
		String[] sectionsOfName = name.split("\\."); //this gets the total different amount parts of the name seperated by .
		for(int i=0; i < sectionsOfName.length; i++){
			domainNameLength += sectionsOfName[i].length() + 1; //includes a byte for every character and the number that will be used in the name for every section
		}
		
		
		//create the header for the DNS query 
		ByteBuffer header = ByteBuffer.allocate(12); //the header contains 96 bits = 12 bytes 
		
		//populate the header 
		header.put(qID); //random ID number
		header.put((byte)0x0100); //Header line 2 (QR, OPcode, AA, TC, RD, RA, Z, RCODE)
		header.put((byte)0x0001); //QR COUNT
		header.put((byte)0x0000); //AN COUNT
		header.put((byte)0x0000); //NS COUNT
		header.put((byte)0x0000); //AR COUNT
		
		//create the question for the DNS query 

		ByteBuffer question = ByteBuffer.allocate(domainNameLength + 5); //the length of the question is the name and the additional bytes for the terminating byte, qtype and qclass 
		
		//QNAME 
			for(int i=0; i < sectionsOfName.length; i++){
				question.put((byte)sectionsOfName[i].length());
					for(int j = 0; j < sectionsOfName[i].length(); j++){
						question.put((byte)(int)(sectionsOfName[i].charAt(j)));
					}
			}
		question.put((byte)0x0000); //terminating byte
		
		//QTYPE
		if(type == QueryType.A){
			question.put((byte)0x0001); //A type query
		}else if(type == QueryType.MX){
			question.put((byte)0x0002); //Mail server query
		}else{
			question.put((byte)0x000f); //Name server query
		}
		
		//QCLASS
		question.put((byte)0x0001); //QCLASS same for every query 
		
		ByteBuffer query = ByteBuffer.allocate(12 + domainNameLength + 5); //this is the size of the question and header 
		query.put(header.array()); //add the header to the query 
		query.put(question.array()); //add the question to the query 
		
		InetAddress addr = InetAddress.getByAddress(ipAddr);
		DatagramSocket clientSocket = new DatagramSocket();
		
		sendData = query.array();
		
		DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, addr, portNumber);
		
		// Send the packet
		clientSocket.send(sendPacket);

		// Create a packet structure to store data sent back by the server
		DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

		// Receive data from the server
		clientSocket.receive(receivePacket);
		

		// Extract the sentence (as a String object) from the received byte stream
		String modifiedSentence = new String(receivePacket.getData());
		System.out.println("From Server: " + modifiedSentence);
				
		// Close the socket
		clientSocket.close();

	}

}
