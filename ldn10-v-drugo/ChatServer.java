import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.*;

public class ChatServer {

	protected int serverPort = 1234;
	protected List<SSLSocket> clients = new ArrayList<SSLSocket>(); // list of clients
	protected Map<SSLSocket, String> socket2User = new HashMap<SSLSocket, String>();
	protected Map<String, SSLSocket> user2Socket = new HashMap<String, SSLSocket>();

	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		SSLServerSocket serverSocket = null;

		// create socket
		try {
			//serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket

			String passphrase = "5318008";

			// preberi datoteko z odjemalskimi certifikati
			KeyStore clientKeyStore = KeyStore.getInstance("JKS"); // KeyStore za shranjevanje odjemalčevih javnih ključev (certifikatov)
			clientKeyStore.load(new FileInputStream("clients.public"), passphrase.toCharArray());

			// preberi datoteko s svojim certifikatom in tajnim ključem
			KeyStore serverKeyStore = KeyStore.getInstance("JKS"); // KeyStore za shranjevanje strežnikovega tajnega in javnega ključa
			serverKeyStore.load(new FileInputStream("server.private"), passphrase.toCharArray());

			// vzpostavi SSL kontekst (komu zaupamo, kakšni so moji tajni ključi in certifikati)
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(clientKeyStore);
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(serverKeyStore, passphrase.toCharArray());
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), (new SecureRandom()));

			// kreiramo socket
			SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
			serverSocket = (SSLServerSocket) factory.createServerSocket(this.serverPort);
			serverSocket.setNeedClientAuth(true); // tudi odjemalec se MORA predstaviti s certifikatom
			serverSocket.setEnabledCipherSuites(new String[] {"TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"});
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		try {
			while (true) {
				SSLSocket newClientSocket = (SSLSocket) serverSocket.accept(); // wait for a new client connection
				newClientSocket.startHandshake();
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
		Iterator<SSLSocket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			SSLSocket socket = (SSLSocket) i.next(); // get the socket for communicating with this client
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	public void sendToSelf (String msg, String senderName) {
		

		   	SSLSocket vtic = user2Socket.get(senderName);
			vtic = user2Socket.get(senderName);
			String msg_send = msg;

			try {
				DataOutputStream out = new DataOutputStream(vtic.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(msg_send); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
	}

	public void sendToSomeone (String msg_received, String senderName, String username) {
		   String msg_send = "Private|    " + senderName + " said : " + msg_received;

		   SSLSocket vtic = this.user2Socket.get(username);
		//    System.out.println(user2Socket);
		//    System.out.println(this.user2Socket.get(username));
		//    System.out.println(this.user2Socket.containsKey(username));


		   if (this.user2Socket.containsKey(username)) {
			   vtic = user2Socket.get(username);
		   } else {
			   msg_send = "Uporabnik s taksnim uporabniskim imenom ni aktiven.";
			   sendToSelf(msg_send, senderName);
			   return;
		   }


			try {
				DataOutputStream out = new DataOutputStream(vtic.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(msg_send); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
	}

	public void removeClient(SSLSocket socket) {
		synchronized(this) {
			clients.remove(socket);
		}
	}
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private SSLSocket socket;
	private String username = null;

	public ChatServerConnector(ChatServer server, SSLSocket socket) {
		this.server = server;
		this.socket = socket;
		try {
			username = this.socket.getSession().getPeerPrincipal().getName();
			String[] razbitje = username.split("=");
			username = razbitje[1];
		} catch (SSLPeerUnverifiedException e) {
			e.printStackTrace();
		}

		//server.clients.add(socket);
		server.socket2User.put(socket, username);
		server.user2Socket.put(username, socket);
	}

	public void run() {
		System.out.println("[system] connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());

		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket);
			return;
		}

		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			String msg_received;
			try {
				msg_received = in.readUTF(); // read the message from the client
			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort() + ", removing client");
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket);
				return;
			}

			if (msg_received.length() == 0) // invalid message
				continue;

			System.out.println("[RKchat] [" + this.socket.getPort() + "] : " + msg_received); // print the incoming message in the console

			if (msg_received.startsWith("@")) {

				String[] razbitje = msg_received.split("@");
				//System.out.println(razbitje.length);
				if (razbitje.length < 3) {
					this.server.sendToSelf("Narobe formatirano privatno sporocilo.", this.username);
					continue;
				} else {
					String user = razbitje[1];
					String msg = razbitje[2];
					// System.err.println(msg);
					// System.out.println(user);
					this.server.sendToSomeone(msg, this.username, user);
					// if (this.server.user2Socket.containsKey(user)) {
					// 	this.server.sendToSomeone(msg, user);
					// } else {
					// 	System.out.print("Uporabnik s taksnim uporabniskim imenom ne obstaja.");
					// 	System.out.print(this.server.user2Socket);
					// 	continue;
					// }
				}

			} else {
				String msg_send = username + " said: " + msg_received; // TODO

				try {
					this.server.sendToAllClients(msg_send); // send message to all clients
				} catch (Exception e) {
					System.err.println("[system] there was a problem while sending the message to all clients");
					e.printStackTrace(System.err);
					continue;
			}
			}
		}
	}
}
