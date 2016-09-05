package serie;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import container.Service;
import exceptions.serie.IncorrectChecksumException;
import exceptions.serie.MissingCharacterException;
import exceptions.serie.ProtocolException;
import serie.trame.Conversation;
import serie.trame.EndOrderFrame;
import serie.trame.Frame.IncomingCode;
import serie.trame.IncomingFrame;
import serie.trame.Order;
import serie.trame.Paquet;
import utils.Config;
import utils.ConfigInfo;
import utils.Log;

/**
 * Implémentation du protocole série couche trame
 * @author pf
 *
 */

public class SerieCoucheTrame implements Service
{	
	/**
	 * Toutes les conversations
	 */
	private Conversation[] conversations = new Conversation[256];
	
	/**
	 * Liste des trames dont on attend un acquittement
	 * Les trames de cette liste sont toujours triées par date de mort (de la plus proche à la plus éloignée)
	 */
	private LinkedList<Integer> waitingFrames = new LinkedList<Integer>();
	
	/**
	 * Liste des trames d'ordre long acquittées dont on attend la fin
	 */
	private ArrayList<Integer> pendingLongFrames = new ArrayList<Integer>();

	/**
	 * Liste des trames d'ordre dont on a reçu la fin (EXECUTION_END ou REQUEST_ANSWER)
	 */
	private LinkedList<Integer> closedFrames = new LinkedList<Integer>();

	private int timeout;
	private int dernierIDutilise = 0; // dernier ID utilisé
	
	// Afin d'éviter de la créer à chaque fois
	private EndOrderFrame endOrderFrame = new EndOrderFrame();
	
	private Log log;
	private SerieCouchePhysique serieOutput;
	private BufferIncomingBytes serieInput;
	
	/**
	 * Constructeur classique
	 * @param log
	 * @param serie
	 */
	public SerieCoucheTrame(Log log, SerieCouchePhysique serieOutput, BufferIncomingBytes serieInput)
	{
		this.log = log;
		this.serieInput = serieInput;
		this.serieOutput = serieOutput;
		for(int i = 0; i < 256; i++)
			conversations[i] = new Conversation(i);
	}
	
	/**
	 * GESTION DE LA CRÉATION ET DE L'ENVOI DE TRAMES
	 */
	
	/**
	 * Renvoie la prochaine conversation disponible, basée sur l'ID.
	 * Cette méthode vérifie les ID actuellement utilisés et donne le prochain qui est libre.
	 * Si tous les ID sont occupés, attend 1ms et re-cherche.
	 * @return
	 * @throws InterruptedException 
	 */
	private synchronized Conversation getNextAvailableConversation() throws InterruptedException
	{
		int initialID = dernierIDutilise;
		dernierIDutilise++;
		while(true)
		{
			if(initialID == dernierIDutilise) // on a fait un tour complet…
			{
				log.critical("Aucun ID disponible : attente");
				Thread.sleep(1);
			}
			
			if(!conversations[dernierIDutilise].libre)
			{
				dernierIDutilise++;
				dernierIDutilise &= 0xFF;
			}
			else
				break;
		}
		conversations[dernierIDutilise].libre = false;
		waitingFrames.add(dernierIDutilise);
		return conversations[dernierIDutilise];
	}

	/**
	 * Demande l'envoi d'un ordre
	 * @param o
	 * @throws InterruptedException 
	 */
	public void sendOrder(Order o) throws InterruptedException
	{
		Conversation f = getNextAvailableConversation();
		f.update(o);

		if(Config.debugSerie)
			log.debug("Envoi d'une nouvelle trame");

		serieOutput.communiquer(f.getFirstTrame());
		f.updateResendDate();
	}

	/**
	 * GESTION DE LA RÉCEPTION DES TRAMES
	 */
	
	/**
	 * Renvoi les données de la couche ordre (haut niveau)
	 * C'est cette méthode qui s'occupe de commander la signalisation.
	 * @return
	 */
	public Paquet readData() throws InterruptedException
	{
		IncomingFrame f = null;
		Paquet p = null;
		boolean restart;
		do {
			restart = false;
			try {
				f = readFrame();
				p = processFrame(f);
				if(p == null) // c'est une trame de signalisation
					restart = true;
			} catch (ProtocolException | IncorrectChecksumException | MissingCharacterException e) {
				log.warning(e);
				restart = true;
			}
		} while(restart);
		return p;
	}
	
	/**
	 * S'occupe du protocole : répond si besoin est, vérifie la cohérence, etc.
	 * Renvoie le ticket associé à la conversation
	 * @param f
	 * @throws InterruptedException 
	 */
	public synchronized Paquet processFrame(IncomingFrame f) throws ProtocolException, InterruptedException
	{
		Iterator<Integer> it = waitingFrames.iterator();
		while(it.hasNext())
		{
			Integer id = it.next();
			Conversation waiting = conversations[id];
			if(id == f.id)
			{
				// On a le EXECUTION_BEGIN d'une frame qui l'attendait
				if(f.code == IncomingCode.EXECUTION_BEGIN)
				{
					if(waiting.origine.type == Order.Type.LONG)
					{
						if(Config.debugSerie)
							log.debug("EXECUTION_BEGIN reçu");
						it.remove();
						pendingLongFrames.add(id);
						return null;
					}

					throw new ProtocolException(f.code+" reçu pour un ordre "+waiting.origine.type);
				}
				else if(f.code == IncomingCode.VALUE_ANSWER)
				{
					if(waiting.origine.type == Order.Type.SHORT)
					{
						if(Config.debugSerie)
							log.debug("VALUE_ANSWER reçu");

						// L'ordre court a reçu un acquittement et ne passe pas par la case "pending"
						it.remove();
						waiting.setDeathDate(); // tes jours sont comptés…
						closedFrames.add(id);
						return new Paquet(f.message, waiting.ticket, waiting.origine);
					}

					throw new ProtocolException(f.code+" reçu pour un ordre "+waiting.origine.type);
				}
				else
					throw new ProtocolException(f.code+" reçu à la place de EXECUTION_BEGIN ou VALUE_ANSWER !");
			}
		}
		
		// Cette valeur n'a pas été trouvée dans les trames en attente
		// On va donc chercher dans les trames en cours
		
		it = pendingLongFrames.iterator();
		while(it.hasNext())
		{
			Integer id = it.next();
			Conversation pending = conversations[id];
			if(id == f.id)
			{
				// On a le EXECUTION_END d'une frame
				if(f.code == IncomingCode.EXECUTION_END)
				{
					if(Config.debugSerie)
						log.debug("EXECUTION_END reçu. On répond par un END_ORDER.");

					pending.setDeathDate(); // tes jours sont comptés…
					// on envoie un END_ORDER
					endOrderFrame.updateId(f.id);
					serieOutput.communiquer(endOrderFrame);
					// et on retire la trame des trames en cours
					it.remove();
					closedFrames.add(id);
					return new Paquet(f.message, pending.ticket, pending.origine);
				}
				else if(f.code == IncomingCode.STATUS_UPDATE)
				{
					if(Config.debugSerie)
						log.debug("STATUS_UPDATE reçu");
					
					return new Paquet(f.message, pending.ticket, pending.origine);
				}
				else
					throw new ProtocolException(f.code+" reçu à la place de EXECUTION_END ou STATUS_UPDATE !");
			}
		}
		
		// On cherche parmi les trames récemment fermées
		
		it = closedFrames.iterator();
		while(it.hasNext())
		{
			Integer id = it.next();
			Conversation closed = conversations[id];
			if(id == f.id)
			{
				// On avait déjà reçu l'EXECUTION_END. On ignore ce message
				if(f.code == IncomingCode.EXECUTION_END && closed.origine.type == Order.Type.LONG)
				{
					if(Config.debugSerie)
						log.warning("EXECUTION_END déjà reçu");
					return null;
				}
				// on ne peut pas recevoir de VALUE_ANSWER
				throw new ProtocolException(f.code+" reçu pour une trame "+closed.origine.type+" finie !");
			}
		}
		
		throw new ProtocolException("ID conversation inconnu : "+f.id);
	}
	
	/**
	 * Lit une frame depuis la série
	 * Cette méthode est bloquante
	 * @return
	 * @throws MissingCharacterException
	 * @throws IncorrectChecksumException
	 * @throws InterruptedException 
	 */
	private IncomingFrame readFrame() throws MissingCharacterException, IncorrectChecksumException, IllegalArgumentException, InterruptedException
	{
		synchronized(serieInput)
		{
			// Attente des données…
			if(!serieInput.available())
				serieInput.wait();
			
			int code = serieInput.read();
			int longueur = serieInput.read();

			if(longueur < 4 || longueur > 255)
				throw new IllegalArgumentException("Mauvaise longueur : "+longueur);
			else if(longueur > 4 && code == IncomingCode.EXECUTION_BEGIN.code)
				throw new IllegalArgumentException("Trame EXECUTION_BEGIN de longueur incorrecte ("+longueur+")");
			
			int id = serieInput.read();
			int[] message = new int[longueur-4];
			for(int i = 0; i < message.length; i++)
				message[i] = serieInput.read();
			int checksum = serieInput.read();

			return new IncomingFrame(code, id, checksum, longueur, message);
		}
	}
	
	@Override
	public void updateConfig(Config config)
	{}

	@Override
	public void useConfig(Config config)
	{
		timeout = config.getInt(ConfigInfo.SERIAL_TIMEOUT);
		Conversation.setTimeout(timeout);
	}
	
	/**
	 * Fermeture de la série
	 */
	public synchronized void close()
	{
		serieOutput.close();
	}
	
	/**
	 * GESTION DES RENVOIS ET DES DESTRUCTIONS DE TRAMES
	 */

	/**
	 * Renvoie le temps avant qu'une trame doive être renvoyée (timeout sinon)
	 * @return
	 */
	public synchronized int timeBeforeResend()
	{
		int out;
		if(!waitingFrames.isEmpty())
			out = conversations[waitingFrames.getFirst()].timeBeforeResend();
		else
			out = timeout;
		return Math.max(out,0); // il faut envoyer un temps positif
	}
	
	/**
	 * Renvoie le temps avant qu'une trame fermée soit vraiment détruite
	 * @return
	 */
	public synchronized int timeBeforeDeath()
	{
		int out;
		if(!closedFrames.isEmpty())
			out = conversations[closedFrames.getFirst()].timeBeforeDeath();
		else
			out = 2*timeout;
		return Math.max(out,0); // il faut envoyer un temps positif
	}

	/**
	 * Renvoie la trame la plus vieille qui en a besoin (possiblement aucune)
	 * @throws InterruptedException 
	 */
	public void resend() throws InterruptedException
	{
		Conversation trame = null;

		synchronized(this)
		{
			if(!waitingFrames.isEmpty() && conversations[waitingFrames.getFirst()].needResend())
			{
				int id = waitingFrames.poll();
				trame = conversations[id];
				// On remet à la fin
				waitingFrames.add(id);
			}
		}

		if(trame != null)
		{
			if(Config.debugSerie)
				log.debug("Une trame est renvoyée");

			serieOutput.communiquer(trame.getFirstTrame());
			trame.updateResendDate(); // on remet la date de renvoi à plus tard
		}
	}

	/**
	 * Tue les vieilles trames
	 */
	public synchronized void kill()
	{
		while(!closedFrames.isEmpty() && conversations[closedFrames.getFirst()].needDeath())
		{
			int id = closedFrames.getFirst();
			conversations[id].libre = true;  // cet ID est maintenant libre
			closedFrames.removeFirst();
		}
	}

	public void init() throws InterruptedException
	{
		serieOutput.init();
	}
}
