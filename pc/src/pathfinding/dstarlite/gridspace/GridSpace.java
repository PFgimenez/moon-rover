/*
Copyright (C) 2016 Pierre-François Gimenez

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

package pathfinding.dstarlite.gridspace;

import graphic.Fenetre;
import graphic.PrintBuffer;
import graphic.printable.Couleur;
import graphic.printable.Layer;
import graphic.printable.Printable;

import java.awt.Graphics;
import java.util.ArrayList;
import java.util.BitSet;

import obstacles.ObstaclesFixes;
import obstacles.memory.ObstaclesIteratorPresent;
import obstacles.memory.ObstaclesMemory;
import obstacles.types.ObstacleProximity;
import robot.RobotReal;
import utils.Config;
import utils.ConfigInfo;
import utils.Log;
import utils.Vec2RO;
import container.Service;

/**
 * La classe qui contient la grille utilisée par le pathfinding.
 * Utilisée uniquement pour le pathfinding DStarLite.
 * Notifie quand il y a un changement d'obstacles
 * @author pf
 *
 */

public class GridSpace implements Service, Printable
{
	protected Log log;
	private ObstaclesIteratorPresent iteratorDStarLite;
	private ObstaclesIteratorPresent iteratorRemoveNearby;
	private ObstaclesMemory obstaclesMemory;
	private PointGridSpaceManager pointManager;
	private MasqueManager masquemanager;
	private PrintBuffer buffer;

	private int distanceMinimaleEntreProximite;
	private boolean printObsCapteurs;
	
	// cette grille est constante, c'est-à-dire qu'elle ne contient que les obstacles fixes
	private BitSet grilleStatique = new BitSet(PointGridSpace.NB_POINTS);
	private Couleur[] grid = new Couleur[PointGridSpace.NB_POINTS];

	private long deathDateLastObstacle;
	
	public GridSpace(Log log, ObstaclesIteratorPresent iteratorDStarLite, ObstaclesIteratorPresent iteratorRemoveNearby, ObstaclesMemory obstaclesMemory, PointGridSpaceManager pointManager, PrintBuffer buffer, MasqueManager masquemanager)
	{
		this.obstaclesMemory = obstaclesMemory;
		this.log = log;
		this.pointManager = pointManager;
		this.iteratorDStarLite = iteratorDStarLite;
		this.iteratorRemoveNearby = iteratorRemoveNearby;
		this.buffer = buffer;
		this.masquemanager = masquemanager;
	}
	

	@Override
	public void useConfig(Config config)
	{
		distanceMinimaleEntreProximite = config.getInt(ConfigInfo.DISTANCE_BETWEEN_PROXIMITY_OBSTACLES);
		printObsCapteurs = config.getBoolean(ConfigInfo.GRAPHIC_PROXIMITY_OBSTACLES);
		
		// on ajoute les obstacles fixes une fois pour toute si c'est demandé
		if(config.getBoolean(ConfigInfo.GRAPHIC_FIXED_OBSTACLES))
			for(ObstaclesFixes o : ObstaclesFixes.values())
				buffer.add(o.getObstacle());
		
		// l'affichage du d* lite est géré par le gridspace
		if(config.getBoolean(ConfigInfo.GRAPHIC_D_STAR_LITE))
		{
			buffer.add(this);
			reinitgrid();
		}

		log.debug("Grille statique initialisée");

	}

	/**
	 * Réinitialise la grille d'affichage
	 */
	public void reinitgrid()
	{
		synchronized(buffer)
		{
			for(int i = 0; i < PointGridSpace.NB_POINTS; i++)
			{
				grid[pointManager.get(i).hashCode()] = null;
	
				for(ObstaclesFixes o : ObstaclesFixes.values())
					if(o.getObstacle().squaredDistance(pointManager.get(i).computeVec2())
							<= (int)(PointGridSpace.DISTANCE_ENTRE_DEUX_POINTS * PointGridSpace.DISTANCE_ENTRE_DEUX_POINTS)/2)
					{
						grilleStatique.set(i);
						grid[pointManager.get(i).hashCode()] = Couleur.NOIR;
						break; // on ne vérifie pas les autres obstacles
					}
			}
			buffer.notify();
		}
	}


	@Override
	public void updateConfig(Config config)
	{}

	/**
	 * Renvoie la distance en fonction de la direction.
	 * Attention ! Ne prend pas en compte les obstacles dynamiques
	 * @param i
	 * @return
	 */
	public int distanceStatique(PointDirige point)
	{
		// s'il y a un obstacle statique
		if(grilleStatique.get(point.point.hashCode())) // TODO : vérifier le point d'arrivée aussi ?
			return Integer.MAX_VALUE;
		if(point.dir.isDiagonal())
			return 1414;
		return 1000;
	}

	/**
	 * Supprime les anciens obstacles et notifie si changement
	 */
	public synchronized void deleteOldObstacles()
	{
		// S'il y a effectivement suppression, on régénère la grille
		if(obstaclesMemory.deleteOldObstacles())
			notify(); // changement de la grille dynamique !
	}

	/**
	 * Fournit au thread de péremption la date de la prochaine mort
	 * @return
	 */
	public long getNextDeathDate()
	{
		return obstaclesMemory.getNextDeathDate();
	}

	/**
	 * Un nouveau DStarLite commence. Il faut lui fournir les obstacles actuels
	 * @return
	 */
	public ArrayList<PointDirige> getCurrentObstacles()
	{
		iteratorDStarLite.reinit();
		ArrayList<PointDirige> out = new ArrayList<PointDirige>();
		deathDateLastObstacle = 0;
		ObstacleProximity o = null;
		while(iteratorDStarLite.hasNext())
		{
			o = iteratorDStarLite.next();
//			log.debug("Ajout d'un obstacle au début du dstarlite");
			out.addAll(o.getMasque().masque);
			
			// le dstarlite est mis à jour jusqu'au dernier obstacle, celui qui meurt à cette date.
			// si un obstacle meurt avant : il est connu
			// s'il meurt après : il est inconnu
			deathDateLastObstacle = o.getDeathDate();
		}		
		
		iteratorDStarLite.reinit(); // on revient au début pour pouvoir assister à la mort des premiers obstacles

		return out;
	}
	
	/**
	 * Retourne les obstacles à supprimer (indice 0) et ceux à ajouter (indice 1) dans le DStarLite
	 */
	public ArrayList<ArrayList<PointDirige>> getOldAndNewObstacles()
	{
		synchronized(obstaclesMemory)
		{
			ArrayList<ArrayList<PointDirige>> out = new ArrayList<ArrayList<PointDirige>>();
			out.add(new ArrayList<PointDirige>());
			out.add(new ArrayList<PointDirige>());
	
			while(iteratorDStarLite.hasNextDead())
				out.get(0).addAll(iteratorDStarLite.next().getMasque().masque);
			
			ObstacleProximity p;
			while((p = obstaclesMemory.pollMortTot()) != null)
				out.get(0).addAll(p.getMasque().masque);
	
			long tmp = deathDateLastObstacle;
			while(iteratorDStarLite.hasNext())
			{
				ObstacleProximity o = iteratorDStarLite.next();
				long deathDate = o.getDeathDate();
				if(deathDate > deathDateLastObstacle) // si cet obstacle est effectivement nouveau pour le dstarlite
				{
					tmp = deathDate;
					out.get(1).addAll(o.getMasque().masque);
				}
			}
			
			deathDateLastObstacle = tmp;
			iteratorDStarLite.reinit(); // l'itérateur reprendra juste avant les futurs obstacles périmés
			return out;
		}
	}

    /**
	 * Appelé par le thread des capteurs par l'intermédiaire de la classe capteurs
	 * Ajoute l'obstacle à la mémoire et dans le gridspace
     * Supprime les obstacles mobiles proches
     * Ça allège le nombre d'obstacles.
     * Utilisé par les capteurs
     * @param position
     * @return 
     * @return
     */
    public ObstacleProximity addObstacleAndRemoveNearbyObstacles(Vec2RO position)
    {
    	iteratorRemoveNearby.reinit();
    	while(iteratorRemoveNearby.hasNext())
    	{
    		ObstacleProximity o = iteratorRemoveNearby.next();
        	if(o.isProcheCentre(position, distanceMinimaleEntreProximite))
        	{
        		iteratorRemoveNearby.remove();
        		if(printObsCapteurs)
        			buffer.removeSupprimable(o);
        	}
    	}

    	Masque masque = masquemanager.getMasque(position);
		ObstacleProximity o = obstaclesMemory.add(position, masque);
		
		return o;
    }

	@Override
	public void print(Graphics g, Fenetre f, RobotReal robot)
	{
		for(int i = 0; i < PointGridSpace.NB_POINTS; i++)
			if(grid[i] != null)
			{
				g.setColor(grid[i].couleur);
				pointManager.get(i).print(g, f, robot);
			}
		g.setColor(Couleur.NOIR.couleur);
	}

	/**
	 * Permet au D* Lite d'afficher des couleurs
	 * @param gridpoint
	 * @param couleur
	 */
	public void setColor(PointGridSpace gridpoint, Couleur couleur)
	{
		if(gridpoint != null)
			synchronized(buffer)
			{
				grid[gridpoint.hashcode] = couleur;
				buffer.notify();
			}
	}


	@Override
	public Layer getLayer()
	{
		return Layer.FOREGROUND;
	}
	
}
