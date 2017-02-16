/*
Copyright (C) 2013-2017 Pierre-François Gimenez

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>
*/

package serie;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import robot.CinematiqueObs;
import robot.Speed;
import serie.SerialProtocol.OutOrder;
import serie.trame.Order;
import config.Config;
import config.ConfigInfo;
import config.Configurable;
import container.Service;
import container.dependances.SerialClass;
import utils.Log;
import utils.Vec2RO;

/**
 * Classe qui contient les ordres à envoyer à la série
 * Il y a trois priorité
 * - la plus haute, l'arrêt
 * - ensuite, la trajectoire courbe
 * - enfin, tout le reste
 * @author pf
 *
 */

public class BufferOutgoingOrder implements Service, Configurable, SerialClass
{
	protected Log log;
	private byte prescaler;
	private short sendPeriod;
	
	public BufferOutgoingOrder(Log log)
	{
		this.log = log;
	}
		
	private volatile LinkedList<Order> bufferBassePriorite = new LinkedList<Order>();
	private volatile LinkedList<Order> bufferTrajectoireCourbe = new LinkedList<Order>();
	private volatile Ticket stop = null;
	private boolean debugSerie;
	
	/**
	 * Le buffer est-il vide?
	 * @return
	 */
	public synchronized boolean isEmpty()
	{
		return bufferBassePriorite.isEmpty() && bufferTrajectoireCourbe.isEmpty() && stop == null;
	}

	/**
	 * Retire un élément du buffer
	 * @return
	 */
	public synchronized Order poll()
	{
		if(bufferTrajectoireCourbe.size() + bufferBassePriorite.size() > 10)
			log.warning("On n'arrive pas à envoyer les ordres assez vites (ordres TC en attente : "+bufferTrajectoireCourbe.size()+", autres en attente : "+bufferBassePriorite.size()+")");
		
		if(stop != null)
		{
			bufferTrajectoireCourbe.clear(); // on annule tout mouvement
			Order out = new Order(OutOrder.STOP, stop);
			stop = null;
			return out;
		}
		else if(!bufferTrajectoireCourbe.isEmpty())
			return bufferTrajectoireCourbe.poll();
		else
			return bufferBassePriorite.poll();
	}
	
	/**
	 * Signale la vitesse max au bas niveau
	 * @param vitesse signée
	 * @return
	 */
	public synchronized void setMaxSpeed(double vitesse)
	{
		short vitesseTr; // vitesse signée
		vitesseTr = (short)(vitesse*1000);

		ByteBuffer data = ByteBuffer.allocate(2);
		data.putShort(vitesseTr);

		bufferBassePriorite.add(new Order(data, OutOrder.SET_MAX_SPEED));
		notify();
	}
	
	/**
	 * Ordre long de suivi de trajectoire
	 * @param vitesseInitiale
	 * @param marcheAvant
	 * @return
	 */
	public synchronized Ticket beginFollowTrajectory(Speed vitesseInitiale, boolean marcheAvant)
	{
		short vitesseTr; // vitesse signée
		if(marcheAvant)
			vitesseTr = (short)(vitesseInitiale.translationalSpeed*1000);
		else
			vitesseTr = (short)(- vitesseInitiale.translationalSpeed*1000);

		ByteBuffer data = ByteBuffer.allocate(2);
		data.putShort(vitesseTr);

		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(data, OutOrder.SET_MAX_SPEED, t));
		notify();
		return t;
	}
	
	/**
	 * Ajout d'une demande d'ordre de s'arrêter
	 */
	public synchronized Ticket immobilise()
	{
		if(debugSerie)
			log.debug("Stop !");
		stop = new Ticket();
		notify();
		return stop;
	}
	
	/**
	 * Demande la couleur au bas niveau
	 */
	public synchronized Ticket demandeCouleur()
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(OutOrder.ASK_COLOR, t));
		notify();
		return t;
	}

	/**
	 * Lève le filet
	 */
	public synchronized Ticket leveFilet()
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(OutOrder.PULL_UP_NET, t));
		notify();
		return t;
	}
	
	/**
	 * Baisse le filet
	 */
	public synchronized Ticket baisseFilet()
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(OutOrder.PULL_DOWN_NET, t));
		notify();
		return t;
	}
	
	/**
	 * Met le filet en position intermédiaire
	 */
	public synchronized Ticket bougeFiletMiChemin()
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(OutOrder.PUT_NET_HALFWAY, t));
		notify();
		return t;
	}

	/**
	 * Abaisse la bascule avec le filet
	 */
	public synchronized Ticket traverseBascule()
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(OutOrder.CROSS_FLIP_FLOP, t));
		notify();
		return t;
	}
	
	/**
	 * Ouvre le filet
	 */
	public synchronized Ticket ouvreFilet()
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(OutOrder.OPEN_NET, t));
		notify();
		return t;
	}
	
	/**
	 * Ferme le filet
	 */
	public synchronized Ticket fermeFilet()
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(OutOrder.CLOSE_NET, t));
		notify();
		return t;
	}
	
	/**
	 * Ejecte les balles
	 */
	public synchronized Ticket ejecte(Boolean droite) // la réflectivité demande d'utiliser Boolean et pas boolean
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(droite ? OutOrder.EJECT_RIGHT_SIDE : OutOrder.EJECT_LEFT_SIDE, t));
		notify();
		return t;
	}

	/**
	 * Réarme le filet
	 */
	public synchronized Ticket rearme(Boolean droite)
	{
		Ticket t = new Ticket();
		bufferBassePriorite.add(new Order(droite ? OutOrder.REARM_RIGHT_SIDE : OutOrder.REARM_LEFT_SIDE, t));
		notify();
		return t;
	}

	/**
	 * Ajoute une position et un angle.
	 * Occupe 5 octets.
	 * @param data
	 * @param pos
	 * @param angle
	 */
	private void addXYO(ByteBuffer data, Vec2RO pos, double angle)
	{
		data.put((byte) (((int)(pos.getX())+1500) >> 4));
		data.put((byte) ((((int)(pos.getX())+1500) << 4) + ((int)(pos.getY()) >> 8)));
		data.put((byte) ((int)(pos.getY())));
		short theta = (short) Math.round((angle % 2*Math.PI)*1000);
		data.putShort(theta);		
	}
	
	/**
	 * Corrige la position du bas niveau
	 */
	public synchronized void correctPosition(Vec2RO deltaPos, double deltaOrientation)
	{
		ByteBuffer data = ByteBuffer.allocate(4);
		addXYO(data, deltaPos, deltaOrientation);
		bufferBassePriorite.add(new Order(data, OutOrder.EDIT_POSITION));
		notify();
	}
	
	/**
	 * Demande à être notifié du début du match
	 */
	public synchronized void waitForJumper()
	{
		bufferBassePriorite.add(new Order(OutOrder.WAIT_FOR_JUMPER));
		notify();
	}

	/**
	 * Demande à être notifié de la fin du match
	 */
	public synchronized void startMatchChrono()
	{
		bufferBassePriorite.add(new Order(OutOrder.START_MATCH_CHRONO));
		notify();
	}

	/**
	 * Démarre le stream
	 */
	public synchronized void startStream()
	{
		ByteBuffer data = ByteBuffer.allocate(3);
		data.putShort(sendPeriod);
		data.put(prescaler);
		bufferBassePriorite.add(new Order(data, OutOrder.START_STREAM_ALL));
		notify();
	}
	
	@Override
	public void useConfig(Config config)
	{
		sendPeriod = config.getShort(ConfigInfo.SENSORS_SEND_PERIOD);
		prescaler = config.getByte(ConfigInfo.SENSORS_PRESCALER);
		debugSerie = config.getBoolean(ConfigInfo.DEBUG_SERIE);
	}
	
	/**
	 * Envoi de tous les arcs élémentaires d'un arc courbe
	 * @0 arc
	 */
	public synchronized void envoieArcCourbe(List<CinematiqueObs> points, int indexTrajectory)
	{
		if(debugSerie)
			log.debug("Envoi de "+points.size()+" points");

		int index = indexTrajectory;
		int nbEnvoi = (points.size() >> 5) + 1;
		int modulo = (points.size() & 31); // pour le dernier envoi
		
		for(int i = 0; i < nbEnvoi; i++)
		{
			int nbArc = 32;
			if(i == nbEnvoi - 1) // dernier envoi
				nbArc = modulo;
			ByteBuffer data = ByteBuffer.allocate(1+7*nbArc);
			data.put((byte)index);
			
			for(int j = 0; j < nbArc; j++)
			{
				CinematiqueObs c = points.get((i<<5)+j);
				addXYO(data, c.getPosition(), c.orientationGeometrique);
				short courbure = (short) ((Math.round(c.courbureReelle)*10) & 0xEFFF);
	
				// on vérifie si on va dans le même sens que le prochain point
				// le dernier point est forcément un point d'arrêt
				if((i<<5)+j+1 == points.size() || c.enMarcheAvant != points.get((i<<5)+j+1).enMarcheAvant)
					courbure |= 0x8000; // en cas de rebroussement
				
				data.putShort(courbure);
			}
			bufferTrajectoireCourbe.add(new Order(data, OutOrder.SEND_ARC));
			index += nbArc;
		}
		notify();			
	}

}
