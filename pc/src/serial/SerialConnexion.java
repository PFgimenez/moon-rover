package serial;

import exceptions.FinMatchException;
import exceptions.SerialConnexionException;
import gnu.io.CommPortIdentifier;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.TooManyListenersException;

import utils.Config;
import utils.ConfigInfo;
import utils.Log;
import utils.Sleep;
import container.Service;

/**
 * La connexion série vers la STM
 * @author pf
 *
 */

public class SerialConnexion implements SerialPortEventListener, Service
{
	private SerialPort serialPort;
	protected Log log;
	
	private boolean isClosed;
	private int baudrate;
	private int canBeRead = 0;
	
	/**
	 * A BufferedReader which will be fed by a InputStreamReader 
	 * converting the bytes into characters 
	 * making the displayed results codepage independent
	 */
	private BufferedReader input;

	/** The output stream to the port */
	private OutputStream output;

	/** Milliseconds to block while waiting for port open */
	private static final int TIME_OUT = 2000;

	/**
	 * Constructeur pour la série de test
	 * @param log
	 */
	public SerialConnexion(Log log)
	{
		this.log = log;
	}

	private synchronized boolean searchPort()
	{
		log.debug("Recherche de la série à "+baudrate+" baud");
		Enumeration<?> ports = CommPortIdentifier.getPortIdentifiers();

		while(ports.hasMoreElements())
		{
			CommPortIdentifier port = (CommPortIdentifier) ports.nextElement();

			log.debug("Essai du port " + port.getName());
			
			// Test du port
			if(!initialize(port, baudrate))
				continue;
			
			if(ping())
			{
				log.debug("STM sur " + port.getName());
				// Il ne faut activer le listener que maintenant, sinon
				// ça pose des problèmes avec le ping
				serialPort.notifyOnDataAvailable(true);
				notifyAll();
				return true;
			}
			else
				log.debug(port.getName()+": non");
				
			// Ce n'est pas cette série, on la ferme donc
			serialPort.close();
		}
		// La série n'a pas été trouvée
		return false;
	}
	
	/**
	 * Il donne à la série tout ce qu'il faut pour fonctionner
	 * @param port_name
	 * 					Le port où est connecté la carte
	 * @param baudrate
	 * 					Le baudrate que la carte utilise
	 * @throws SerialManagerException 
	 * @throws SerialConnexionException 
	 */
	private boolean initialize(CommPortIdentifier portId, int baudrate)
	{
		try
		{
			serialPort = (SerialPort) portId.open("TechTheTroll", TIME_OUT);
			// set port parameters
			serialPort.setSerialPortParams(baudrate,
					SerialPort.DATABITS_8,
					SerialPort.STOPBITS_1,
					SerialPort.PARITY_NONE);

			// Configuration du Listener
			try {
				serialPort.addEventListener(this);
			} catch (TooManyListenersException e) {
				e.printStackTrace();
			}

			// open the streams
			input = new BufferedReader(new InputStreamReader(serialPort.getInputStream()));
			output = serialPort.getOutputStream();

			serialPort.enableReceiveTimeout(1000);
			
			isClosed = false;
			return true;
		}
		catch (PortInUseException | UnsupportedCommOperationException | IOException e2)
		{
			log.critical(e2);
			return false;
		}
	}

	/**
	 * Envoi d'un itinéraire
	 * @param messages
	 * @throws SerialConnexionException
	 * @throws FinMatchException
	 */
//	public void communiquer(ArrayList<LocomotionArc> chemin)
//	{
//		String[] messages = new String[chemin.size()+1];
//		messages[0] = String.valueOf(chemin.size());
//		for(int i = 0; i < chemin.size(); i++)
//			messages[i+1] = chemin.toString();
//		communiquer(messages);
//	}

	/**
	 * Méthode pour envoyer un message à la carte
	 * @param messages
	 * @return
	 * @throws SerialConnexionException
	 * @throws FinMatchException
	 */
	public synchronized void communiquer(String[] messages)
	{
		/**
		 * Un appel à une série fermée ne devrait jamais être effectué.
		 */
/*		while(!ready)
		{
			log.debug("Avant envoi");
			try {
				wait();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}*/

		if(isClosed)
			return; // TODO
//			throw new FinMatchException();
		
		try
		{
			for (String m : messages)
			{
				if(Config.debugSerie)
					log.debug("OUT: "+m);
					// TODO : ajouter un output.write("\r" en byte) à la place ?
				m += "\r";
				output.write(m.getBytes());
			}
		}
		catch (Exception e)
		{
			/**
			 * Si la STM ne répond vraiment pas, on recommence de manière infinie.
			 * De toute façon, on n'a pas d'autre choix...
			 */
			log.critical("Ne peut pas parler à la STM. Tentative de reconnexion.");
			while(!searchPort())
			{
				log.critical("Pas trouvé... On recommence");
				// On laisse la série respirer un peu
				Sleep.sleep(1000);
			}
		}
	}

	/**
	 * Méthode pour parler à l'avr
	 * @param messages Messages à envoyer
	 * @param nb_lignes_reponse Nombre de lignes que l'avr va répondre (sans compter les acquittements)
	 * @return Un tableau contenant le message
	 * @throws SerialConnexionException 
	 * @throws FinMatchException 
	 */
//	public String[] communiquer(String message, int nb_lignes_reponse)
//	{
//		String[] messages = {message};
//		return communiquer(messages, nb_lignes_reponse);
//	}
	
	/**
	 * Méthode pour parler à l'avr
	 * @param messages Messages à envoyer
	 * @param nb_lignes_reponse Nombre de lignes que l'avr va répondre (sans compter les acquittements)
	 * @return Un tableau contenant le message
	 * @throws SerialConnexionException 
	 * @throws FinMatchException 
	 */
	public void communiquer(String message)
	{
		String[] messages = {message};
		communiquer(messages);
	}
	
	/**
	 * Méthode pour parler à l'avr
	 * @param messages Messages à envoyer
	 * @param nb_lignes_reponse Nombre de lignes que l'avr va répondre (sans compter les acquittements)
	 * @return Un tableau contenant le message
	 */
//	public synchronized String[] communiquer(String[] messages, int nb_lignes_reponse)
//	{
//		/**
//		 * Un appel à une série fermée ne devrait jamais être effectué.
//		 */
//		while(!ready)
//		{
//			try {
//				wait();
//			} catch (InterruptedException e) {
//				e.printStackTrace();
//			}
//		}
//
//		if(isClosed)
//			return null; // TODO
////			throw new FinMatchException();
//		
//		String inputLines[] = new String[nb_lignes_reponse];
//		try
//		{
//			for (String m : messages)
//			{
//				m += "\r";
//				output.write(m.getBytes());
//			}
//			for (int i = 0 ; i < nb_lignes_reponse; i++)
//			{
//				while(!input.ready());
//				inputLines[i] = input.readLine();
//			}
//		}
//		catch (Exception e)
//		{
//			/**
//			 * Si la STM ne répond vraiment pas, on recommence de manière infinie.
//			 * De toute façon, on n'a pas d'autre choix...
//			 */
//			log.critical("Ne peut pas parler à la STM. Tentative de reconnexion.");
//			while(!searchPort())
//			{
//				log.critical("Pas trouvé... On recommence");
//				// On laisse la série respirer un peu
//				Sleep.sleep(1000);
//			}
//		}
//
//		return inputLines;
//	}

	/**
	 * Doit être appelé quand on arrête de se servir de la série
	 */
	public synchronized void close()
	{
		if (!isClosed && serialPort != null)
		{
			log.debug("Fermeture de la carte");
			serialPort.close();
			isClosed = true;
		}
		else if(isClosed)
			log.debug("Carte déjà fermée");
		else
			log.debug("Carte jamais ouverte");
	}

	/**
	 * Lit une ligne.
	 * @return
	 */
	public synchronized String read()
	{
		try {
			/**
			 * On sait qu'une donnée arrive, donc l'attente est très faible.
			 */
			while(!input.ready());
			canBeRead--;
			String m = input.readLine();
			if(Config.debugSerie)
				log.debug("IN: "+m);
			return m;
		} catch (IOException e) {
			// Impossible car on sait qu'il y a des données
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Gestion d'un évènement sur la série.
	 */
	public synchronized void serialEvent(SerialPortEvent oEvent)
	{
		canBeRead++;
		notifyAll();
	}
	
	public boolean canBeRead()
	{
		return canBeRead > 0;
	}

	/**
	 * Ping de la carte.
	 * Utilisé que par createSerial de SerialManager
	 * @return l'id de la carte
	 */
	public synchronized boolean ping()
	{
		try
		{		
			//Evacuation de l'eventuel buffer indésirable
			output.flush();
//			output.write("$0P@L1Z7\r".getBytes());

			//ping
			output.write("?\r".getBytes());

			//recuperation de l'id de la carte
			while(!input.ready());
			return input.readLine().trim().compareTo("T3") == 0;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public void useConfig(Config config)
	{
		baudrate = config.getInt(ConfigInfo.BAUDRATE);
		if(!searchPort())
		{
			/**
			 * Suppression des verrous qui empêchent parfois la connexion
			 */
			try {
				log.critical("Port série non trouvé, suppression des verrous");
				Runtime.getRuntime().exec("sudo rm -f /var/lock/LCK..tty*");
			} catch (IOException e) {
				e.printStackTrace();
			}
			while(!searchPort())
			{
				log.critical("Port série non trouvé, réessaie dans 500 ms");
				Sleep.sleep(500);
			}
		}
	}
	
	@Override
	public void updateConfig(Config config)
	{}
}
