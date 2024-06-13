import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;  
import java.util.Date;

public class ChatServer {

	protected int serverPort = 1234;
	protected List<Socket> clients = new ArrayList<Socket>(); // list of clients
	protected Map<String, Socket> uporabniki = new HashMap<String, Socket>();

	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		ServerSocket serverSocket = null;

		// create socket
		try {
			serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		try {
			while (true) {
				Socket newClientSocket = serverSocket.accept(); // wait for a new client connection
				synchronized(this) {
					clients.add(newClientSocket); // add client to the list of clients
					
				}
				ChatServerConnector conn = new ChatServerConnector(this, newClientSocket); // create a new thread for communication with the new client
				conn.start(); // run the new thread
			}
		} catch (Exception e) {
			System.err.println("[error] Accept failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// close socket
		System.out.println("[system] closing server socket ...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	// send a message to all clients connected to the server
	public void sendToAllClients(String message) throws Exception {
		Iterator<Socket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			Socket socket = (Socket) i.next(); // get the socket for communicating with this client
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	public void removeClient(Socket socket, String userName) {
		synchronized(this) {
			clients.remove(socket);
			uporabniki.remove(userName);
		}
	}
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;

	private boolean loggedIn = false;
	private String userName = "";

	public ChatServerConnector(ChatServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
	}

	private void sendMessageToSelf (String message) {
		try {
			DataOutputStream out = new DataOutputStream(this.socket.getOutputStream()); // create output stream for sending messages to the client
			out.writeUTF(message); // send message to the client
		} catch (Exception e) {
			System.err.println("[system] could not send message to a client");
			e.printStackTrace(System.err);
		}
	}

	private void sendMessageToSomeone (String message, String name) {
		Socket socket2 = server.uporabniki.get(name);
		try {
			message = this.userName + " said: " + message;
			DataOutputStream out = new DataOutputStream(socket2.getOutputStream()); // create output stream for sending messages to the client
			out.writeUTF(message); // send message to the client
		} catch (Exception e) {
			System.err.println("[system] could not send message to a client");
			e.printStackTrace(System.err);
		}
	}

	public void run() {
		System.out.println("[system] connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());

		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket, this.userName);
			return;
		}

		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			String msg_received;
			try {
				msg_received = in.readUTF(); // read the message from the client
			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort() + ", removing client");
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket, this.userName);
				return;
			}

			if (msg_received.length() == 0) { // invalid message
				continue;
			}

			//2 pomeni, da sta maksimalno 2 stringa, zato ko je to dosezeno, se ne splita vec
			String[] razdelitev = msg_received.split(" ", 2);

			if (razdelitev[0].equals("{ime}")) {
				
				if (this.server.uporabniki.containsValue(this.socket)) {
					String message = "Vasa naprava je na tem portu ze prijavljena z uporabnmiskim imenom.";
					sendMessageToSelf(message);
					continue;
				} else if (this.server.uporabniki.containsKey(razdelitev[1])) {
					String message = "Uporabnisko ime ze obstaja. Za vpis z drugim imenom zacnite vrstico z '{ime} '";
					sendMessageToSelf(message);
					continue;
				} else {
					this.server.uporabniki.put(razdelitev[1], socket);
					this.loggedIn = true;
					this.userName = razdelitev[1];
				}
			}


			SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");  
    		Date date = new Date();
			String casPosiljanja = formatter.format(date);

			if (!loggedIn) {
				String message = "Niste se prijavljeni. Prijavite se tako, da zacnete vrstico z '{ime} '";
				sendMessageToSelf(message);
				continue;
			}


			if (razdelitev[0].equals("[javno]")) {

				String msg_send = "At " + casPosiljanja + " " + this.userName + " said: " + razdelitev[1];

				try {
					this.server.sendToAllClients(msg_send); // send message to all clients
				} catch (Exception e) {
					System.err.println("[system] there was a problem while sending the message to all clients");
					e.printStackTrace(System.err);
					continue;
				}
			}

			if (razdelitev[0].startsWith("*")) {
				//prvi backslash je escape sequence za backslash v stringu, da je prepoznan v stringu.
				//Ko se nato ta string prenese v metodo split, se drugi backslash,
				// ki je sedaj edini veljaven, uporabi kot escape sequence v regex
				String[] naslovnik = razdelitev[0].split("\\*");
				System.out.println(naslovnik);
				
				if (server.uporabniki.get(naslovnik[1]) == null) {
					sendMessageToSelf("At " + casPosiljanja + ": " + "Naslovnik ni prijavljen v sistem.");
				} else {
					String sporocilo = "At " + casPosiljanja + " " + this.userName + " said: " + razdelitev[1];
					sendMessageToSomeone(sporocilo, naslovnik[1]);
				}

				

			}





			System.out.println("[RKchat] [" + this.socket.getPort() + "] : " + msg_received); // print the incoming message in the console

			// String msg_send = "someone said: " + msg_received.toUpperCase(); // TODO

			// try {
			// 	this.server.sendToAllClients(msg_send); // send message to all clients
			// } catch (Exception e) {
			// 	System.err.println("[system] there was a problem while sending the message to all clients");
			// 	e.printStackTrace(System.err);
			// 	continue;
			// }
		}
	}
}
