import java.io.*;
import java.net.*;
import java.util.*;
import javax.net.ssl.*;
import java.security.*;

public class ChatClient extends Thread
{
	protected int serverPort = 1234;

	public static void main(String[] args) throws Exception {
		new ChatClient();
	}

	public ChatClient() throws Exception {
		SSLSocket socket = null;
		DataInputStream in = null;
		DataOutputStream out = null;

		// connect to the chat server
		try {
			System.out.println("[system] connecting to chat server ...");
			
			String passphrase = "5318008";
			
			String uporabnik = "";
			Scanner scan = new Scanner(System.in);
			
			while (!(uporabnik == "Ajshil" || uporabnik == "Sofoklej" || uporabnik == "Evripid")) {
				System.out.print("Vpisite ime uporabnika. (Moznosti: Ajshil, Sofoklej, Evripid):");
				uporabnik = scan.next();
			}

			String uporabniskiKljuc = uporabnik + ".private";

			scan.close();

			// uporabnik = "{ime} " + uporabnik;
			// this.sendMessage(uporabnik, out);

			
			

			// preberi datoteko s strežnikovim certifikatom
			KeyStore serverKeyStore = KeyStore.getInstance("JKS");
			serverKeyStore.load(new FileInputStream("server.public"), passphrase.toCharArray());

			// preberi datoteko s svojim certifikatom in tajnim ključem
			KeyStore clientKeyStore = KeyStore.getInstance("JKS");
			clientKeyStore.load(new FileInputStream(uporabniskiKljuc), passphrase.toCharArray());

			// vzpostavi SSL kontekst (komu zaupamo, kakšni so moji tajni ključi in certifikati)
			TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
			tmf.init(serverKeyStore);
			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(clientKeyStore, passphrase.toCharArray());
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), (new SecureRandom()));

			// kreiramo socket
			SSLSocketFactory sf = sslContext.getSocketFactory();
			socket = (SSLSocket) sf.createSocket("localhost", serverPort);
			socket.setEnabledCipherSuites(new String[] { "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" }); // dovoljeni nacin kriptiranja (CipherSuite)
			socket.startHandshake(); // eksplicitno sprozi SSL Handshake

			//socket = new Socket("localhost", serverPort); // create socket connection
			in = new DataInputStream(socket.getInputStream()); // create input stream for listening for incoming messages
			out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages
			System.out.println("[system] connected");

			ChatClientMessageReceiver message_receiver = new ChatClientMessageReceiver(in); // create a separate thread for listening to messages from the chat server
			message_receiver.start(); // run the new thread
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}

		
		
		//Obvestilo o uporabi:
		System.out.println("Za posiljanje javnega sporocila pred sporocilo napisete '[javno] '");
		System.out.println("Za posiljanje privatnega sporocila pred sporocilo napisete '*uporabniskoIme* ', kjer je uporabniskoIme uporabnisko ime osebe, ki ji hocete poslati sporocilo.");


		// read from STDIN and send messages to the chat server
		BufferedReader std_in = new BufferedReader(new InputStreamReader(System.in));
		String userInput;
		while ((userInput = std_in.readLine()) != null) { // read a line from the console
			this.sendMessage(userInput, out); // send the message to the chat server
		}

		// cleanup
		out.close();
		in.close();
		std_in.close();
		socket.close();
	}

	private void sendMessage(String message, DataOutputStream out) {
		try {
			out.writeUTF(message); // send the message to the chat server
			out.flush(); // ensure the message has been sent
		} catch (IOException e) {
			System.err.println("[system] could not send message");
			e.printStackTrace(System.err);
		}
	}
}

// wait for messages from the chat server and print the out
class ChatClientMessageReceiver extends Thread {
	private DataInputStream in;

	public ChatClientMessageReceiver(DataInputStream in) {
		this.in = in;
	}

	public void run() {
		try {
			String message;
			while ((message = this.in.readUTF()) != null) { // read new message
				System.out.println("[RKchat] " + message); // print the message to the console
			}
		} catch (Exception e) {
			System.err.println("[system] could not read message");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}