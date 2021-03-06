/*
 * Copyright (C) 2013-2017 Pierre-François Gimenez
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package pathfinding.dstarlite.gridspace;

import graphic.Fenetre;
import graphic.PrintBufferInterface;
import graphic.printable.Couleur;
import graphic.printable.Layer;
import graphic.printable.Printable;
import java.awt.Graphics;
import java.util.BitSet;
import java.util.List;
import obstacles.memory.ObstaclesIteratorPresent;
import obstacles.memory.ObstaclesMemory;
import obstacles.types.Obstacle;
import obstacles.types.ObstacleProximity;
import obstacles.types.ObstaclesFixes;
import robot.RobotReal;
import utils.Log;
import utils.Vec2RO;
import config.Config;
import config.ConfigInfo;
import container.Service;
import container.dependances.LowPFClass;

/**
 * La classe qui contient la grille utilisée par le pathfinding.
 * Utilisée uniquement pour le pathfinding DStarLite.
 * Notifie quand il y a un changement d'obstacles
 * 
 * @author pf
 *
 */

public class GridSpace implements Service, Printable, LowPFClass
{
	protected Log log;
	private ObstaclesIteratorPresent iteratorDStarLiteFirst;
	private ObstaclesIteratorPresent iteratorDStarLiteLast;
	private ObstaclesIteratorPresent iteratorRemoveNearby;
	private ObstaclesMemory obstaclesMemory;
	private PointGridSpaceManager pointManager;
	private MasqueManager masquemanager;
	private PrintBufferInterface buffer;

	private int distanceMinimaleEntreProximite;
	private int rayonRobot, rayonRobotObstaclesFixes;

	// cette grille est constante, c'est-à-dire qu'elle ne contient que les
	// obstacles fixes
	private BitSet grilleStatique = new BitSet(PointGridSpace.NB_POINTS);
	private BitSet grilleStatiqueModif = new BitSet(PointGridSpace.NB_POINTS);
	private BitSet newObstacles = new BitSet(PointGridSpace.NB_POINTS * 8);
	private BitSet oldObstacles = new BitSet(PointGridSpace.NB_POINTS * 8);
	private BitSet intersect = new BitSet(PointGridSpace.NB_POINTS * 8);
	private BitSet[] newOldObstacles = new BitSet[2];
	private Couleur[] grid = new Couleur[PointGridSpace.NB_POINTS];

	public GridSpace(Log log, ObstaclesIteratorPresent iteratorDStarLiteFirst, ObstaclesIteratorPresent iteratorDStarLiteLast, ObstaclesIteratorPresent iteratorRemoveNearby, ObstaclesMemory obstaclesMemory, PointGridSpaceManager pointManager, PrintBufferInterface buffer, MasqueManager masquemanager, Config config)
	{
		this.obstaclesMemory = obstaclesMemory;
		this.log = log;
		this.pointManager = pointManager;
		this.iteratorDStarLiteFirst = iteratorDStarLiteFirst;
		this.iteratorDStarLiteLast = iteratorDStarLiteLast;
		this.iteratorRemoveNearby = iteratorRemoveNearby;
		this.buffer = buffer;
		this.masquemanager = masquemanager;
		newOldObstacles[0] = oldObstacles;
		newOldObstacles[1] = newObstacles;

		distanceMinimaleEntreProximite = config.getInt(ConfigInfo.DISTANCE_BETWEEN_PROXIMITY_OBSTACLES);
		rayonRobot = config.getInt(ConfigInfo.DILATATION_ROBOT_DSTARLITE);
		rayonRobotObstaclesFixes = config.getInt(ConfigInfo.RAYON_ROBOT_SUPPRESSION_OBSTACLES_FIXES);

		// on ajoute les obstacles fixes une fois pour toute si c'est demandé
		if(config.getBoolean(ConfigInfo.GRAPHIC_FIXED_OBSTACLES))
			for(ObstaclesFixes o : ObstaclesFixes.values())
				buffer.add(o.getObstacle());

		log.debug("Grille statique initialisée");

		double distance = rayonRobot + PointGridSpace.DISTANCE_ENTRE_DEUX_POINTS / 2;
		distance = distance * distance;

		for(int i = 0; i < PointGridSpace.NB_POINTS; i++)
		{
			for(ObstaclesFixes o : ObstaclesFixes.values())
				if(o.getObstacle().squaredDistance(pointManager.get(i).computeVec2()) <= (int) (distance))
				{
					// Pour le D* Lite, il faut dilater les obstacles du rayon
					// du robot
					grilleStatique.set(i);
					break; // on ne vérifie pas les autres obstacles
				}
		}

		grilleStatiqueModif.clear();
		grilleStatiqueModif.or(grilleStatique);

		// l'affichage du d* lite est géré par le gridspace
		if(config.getBoolean(ConfigInfo.GRAPHIC_D_STAR_LITE) || config.getBoolean(ConfigInfo.GRAPHIC_D_STAR_LITE_FINAL))
		{
			buffer.add(this);
			reinitGraphicGrid();
		}

	}

	/**
	 * Réinitialise la grille d'affichage
	 */
	public void reinitGraphicGrid()
	{
		synchronized(buffer)
		{
			for(int i = 0; i < PointGridSpace.NB_POINTS; i++)
				if(grilleStatique.get(i))
					grid[pointManager.get(i).hashcode] = Couleur.NOIR;
				else
					grid[pointManager.get(i).hashcode] = null;

			buffer.notify();
		}
	}

	/**
	 * Renvoie la distance en fonction de la direction.
	 * Attention ! Ne prend pas en compte les obstacles dynamiques
	 * 
	 * @param i
	 * @return
	 */
	public int distanceStatique(PointDirige point)
	{
		// s'il y a un obstacle statique
		PointGridSpace voisin = pointManager.getGridPointVoisin(point);
		if(grilleStatiqueModif.get(point.point.hashcode) || voisin == null || grilleStatiqueModif.get(voisin.hashcode))
			return Integer.MAX_VALUE;
		return point.dir.distance;
	}

	public boolean isInGrilleStatique(PointGridSpace p)
	{
		return grilleStatiqueModif.get(p.hashcode);
	}

	/**
	 * Un nouveau DStarLite commence. Il faut lui fournir les obstacles actuels
	 * 
	 * @return
	 */
	public BitSet getCurrentObstacles()
	{
		iteratorDStarLiteFirst.reinit();
		iteratorDStarLiteLast.reinit();
		newObstacles.clear();

		while(iteratorDStarLiteLast.hasNext())
		{
			List<PointDirige> tmp = iteratorDStarLiteLast.next().getMasque().masque;
			for(PointDirige p : tmp)
				// si on est déjà dans un obstacle, la distance ne change pas
				if(distanceStatique(p) != Integer.MAX_VALUE)
					newObstacles.set(p.hashCode());
		}

		return newObstacles;
	}

	/**
	 * Retourne les obstacles à supprimer (indice 0) et ceux à ajouter (indice
	 * 1) dans le DStarLite
	 */
	public BitSet[] getOldAndNewObstacles()
	{
		synchronized(obstaclesMemory)
		{
			oldObstacles.clear();
			newObstacles.clear();

			while(iteratorDStarLiteFirst.hasNextDead())
			{
				// log.debug("Mort");
				List<PointDirige> tmp = iteratorDStarLiteFirst.next().getMasque().masque;
				for(PointDirige p : tmp)
					if(distanceStatique(p) != Integer.MAX_VALUE)
						oldObstacles.set(p.hashCode());
			}

			ObstacleProximity o;
			while((o = obstaclesMemory.pollMortTot()) != null)
			{
				// log.debug("Mort tôt");
				List<PointDirige> tmp = o.getMasque().masque;
				for(PointDirige p : tmp)
					if(distanceStatique(p) != Integer.MAX_VALUE)
						oldObstacles.set(p.hashCode());
			}

			while(iteratorDStarLiteLast.hasNext())
			{
				// log.debug("Nouveau");
				List<PointDirige> tmp = iteratorDStarLiteLast.next().getMasque().masque;
				for(PointDirige p : tmp)
					if(distanceStatique(p) != Integer.MAX_VALUE)
						newObstacles.set(p.hashCode());
			}

			/**
			 * On ne va pas enlever un point pour le remettre juste après…
			 */

			intersect.clear();
			intersect.or(newObstacles);
			intersect.and(oldObstacles);
			newObstacles.andNot(intersect);
			oldObstacles.andNot(intersect);

			return newOldObstacles;
		}
	}

	/**
	 * Appelé par le thread des capteurs par l'intermédiaire de la classe
	 * capteurs
	 * Ajoute l'obstacle à la mémoire et dans le gridspace
	 * Supprime les obstacles mobiles proches
	 * Ça allège le nombre d'obstacles.
	 * Utilisé par les capteurs
	 * 
	 * @param position
	 * @return
	 * @return
	 */
	public ObstacleProximity addObstacleAndRemoveNearbyObstacles(Obstacle obstacle)
	{
		iteratorRemoveNearby.reinit();
		while(iteratorRemoveNearby.hasNext())
		{
			ObstacleProximity o = iteratorRemoveNearby.next();
			if(o.isProcheCentre(obstacle.getPosition(), distanceMinimaleEntreProximite))
				iteratorRemoveNearby.remove();
		}

		return obstaclesMemory.add(obstacle, masquemanager.getMasqueEnnemi(obstacle));
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
	 * 
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
		return Layer.MIDDLE;
	}

	public void disableObstaclesFixes(Vec2RO position, Obstacle obstacle)
	{
		// on initialise comme la grille statique classique
		grilleStatiqueModif.clear();
		grilleStatiqueModif.or(grilleStatique);
		for(int i = 0; i < PointGridSpace.NB_POINTS; i++)
			if(grilleStatiqueModif.get(i) && ((position != null && pointManager.get(i).computeVec2().distanceFast(position) < rayonRobotObstaclesFixes) || obstacle.squaredDistance(pointManager.get(i).computeVec2()) == 0))
				grilleStatiqueModif.clear(i);
	}

}
