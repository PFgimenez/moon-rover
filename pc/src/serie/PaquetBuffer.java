package serie;

import java.util.LinkedList;
import java.util.Queue;

import utils.Config;
import utils.Log;
import container.Service;
import serie.trame.Paquet;

/**
 * Buffer qui contient les ordres provenant de la série
 * @author pf
 *
 */

public class PaquetBuffer implements Service
{
	protected Log log;
	
	public PaquetBuffer(Log log)
	{
		this.log = log;
	}
	
	/**
	 * Le buffer est-il vide?
	 * @return
	 */
	public synchronized boolean isEmpty()
	{
		return buffer.isEmpty();
	}
	
	private volatile Queue<Paquet> buffer = new LinkedList<Paquet>();
	
	/**
	 * Ajout d'un élément dans le buffer et provoque un "notify"
	 * @param elem
	 */
	public synchronized void add(Paquet elem)
	{
		buffer.add(elem);
		if(buffer.size() > 5)
		{
			buffer.poll(); // on évacue une ancienne valeur
			log.critical("Paquet traités trop lentement !");
		}
		notify();
	}
	
	/**
	 * Retire un élément du buffer
	 * @return
	 */
	public synchronized Paquet poll()
	{
		return buffer.poll();
	}
	
	@Override
	public void updateConfig(Config config)
	{}

	@Override
	public void useConfig(Config config)
	{}

}