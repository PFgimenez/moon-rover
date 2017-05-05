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

package capteurs;

import graphic.PrintBufferInterface;
import obstacles.types.ObstacleProximity;
import obstacles.types.ObstacleRobot;
import obstacles.types.ObstaclesFixes;
import pathfinding.chemin.CheminPathfinding;
import pathfinding.dstarlite.DStarLite;
import pathfinding.dstarlite.gridspace.GridSpace;
import robot.Cinematique;
import robot.RobotReal;
import serie.BufferOutgoingOrder;
import config.Config;
import config.ConfigInfo;
import container.Container;
import container.Service;
import container.dependances.HighPFClass;
import container.dependances.LowPFClass;
import exceptions.ContainerException;
import table.GameElementNames;
import table.Table;
import table.EtatElement;
import utils.Log;
import utils.Vec2RO;
import utils.Vec2RW;

/**
 * Cette classe contient les informations sur la situation
 * spatiale des capteurs sur le robot.
 * @author pf
 *
 */

public class CapteursProcess implements Service, LowPFClass, HighPFClass
{
	protected Log log;
	private GridSpace gridspace;
	private Table table;
	private DStarLite dstarlite;
	private CheminPathfinding chemin;
	private RobotReal robot;
	private BufferOutgoingOrder serie;
	
	private int nbCapteurs;
	private int rayonEnnemi;
	private int distanceApproximation;
	private ObstacleRobot obstacleRobot;
	private Capteur[] capteurs;
	private double imprecisionMaxPos;
	private double imprecisionMaxAngle;
	private boolean debugCapteurs;
	
	private long dateLastMesureCorrection = -1;
	private long peremptionCorrection;
	private boolean enableCorrection;
	private int indexCorrection = 0;
	private Cinematique[] bufferCorrection;
	
	public CapteursProcess(Container container, Log log, GridSpace gridspace, Table table, DStarLite dstarlite, CheminPathfinding chemin, PrintBufferInterface buffer, RobotReal robot, BufferOutgoingOrder serie, Config config)
	{
		this.table = table;
		this.log = log;
		this.gridspace = gridspace;
		this.dstarlite = dstarlite;
		this.chemin = chemin;
		this.robot = robot;
		this.serie = serie;

		rayonEnnemi = config.getInt(ConfigInfo.RAYON_ROBOT_ADVERSE);
		distanceApproximation = config.getInt(ConfigInfo.DISTANCE_MAX_ENTRE_MESURE_ET_OBJET);		
		nbCapteurs = CapteursRobot.values().length;
		imprecisionMaxPos = config.getDouble(ConfigInfo.IMPRECISION_MAX_POSITION);
		imprecisionMaxAngle = config.getDouble(ConfigInfo.IMPRECISION_MAX_ORIENTATION);
		bufferCorrection = new Cinematique[config.getInt(ConfigInfo.TAILLE_BUFFER_RECALAGE)];
		peremptionCorrection = config.getInt(ConfigInfo.PEREMPTION_CORRECTION);
		enableCorrection = config.getBoolean(ConfigInfo.ENABLE_CORRECTION);
		debugCapteurs = config.getBoolean(ConfigInfo.DEBUG_CAPTEURS);
		
		int demieLargeurNonDeploye = config.getInt(ConfigInfo.LARGEUR_NON_DEPLOYE)/2;
		int demieLongueurArriere = config.getInt(ConfigInfo.DEMI_LONGUEUR_NON_DEPLOYE_ARRIERE);
		int demieLongueurAvant = config.getInt(ConfigInfo.DEMI_LONGUEUR_NON_DEPLOYE_AVANT);
		obstacleRobot = new ObstacleRobot(demieLargeurNonDeploye, demieLongueurArriere, demieLongueurAvant);

		capteurs = new Capteur[nbCapteurs];
				
		try {
			for(int i = 0; i < nbCapteurs; i++)
			{
				CapteursRobot c = CapteursRobot.values()[i];
				capteurs[i] = container.make(c.classe, c.pos, c.angle, c.type, c.sureleve);
			}			
		} catch(ContainerException e)
		{
			log.critical(e);
		}
		
		if(config.getBoolean(ConfigInfo.GRAPHIC_ROBOT_AND_SENSORS))
			for(Capteur c : capteurs)
				buffer.add(c);
	}

	/**
	 * Met à jour les obstacles mobiles
	 */
	public void updateObstaclesMobiles(SensorsData data)
	{
		double orientationRobot = data.cinematique.orientationReelle;
		Vec2RO positionRobot = data.cinematique.getPosition();

		obstacleRobot.update(positionRobot, orientationRobot);

		/**
		 * On update la table avec notre position
		 */
	    for(GameElementNames g: GameElementNames.values())
	        if(g.obstacle.isColliding(obstacleRobot))
	        {
//	        	if(debugCapteurs)
//	        		log.debug("Élément shooté : "+g);
	        	table.setDone(g, EtatElement.PRIS_PAR_NOUS); // on est sûr de l'avoir shooté
	        }
					
		/**
		 * Suppression des mesures qui sont hors-table ou qui voient un obstacle de table
		 */
		for(int i = 0; i < nbCapteurs; i++)
		{
			CapteursRobot c = CapteursRobot.values[i];
			
			/*
			 * Les deux capteurs ToF courts arrière voient le filet lorsqu'il est baissé
			 */
			if(robot.isFiletBaisse() && (c == CapteursRobot.ToF_ARRIERE_DROITE || c == CapteursRobot.ToF_ARRIERE_GAUCHE))
			{
//				if(debugCapteurs)
//					log.debug("Filet baissé : ToF courts arrière ignorés");
				continue;
			}
				
			/*
			 * Le ToF arrière voit le filet et permet de servir de jauge de remplissage
			 */
			if(c == CapteursRobot.ToF_LONG_ARRIERE && !robot.isFiletBaisse())
			{
				if(data.mesures[i] > 0 && data.mesures[i] < 60) // filet plein
				{
//					if(debugCapteurs)
//						log.debug("Filet plein : ToF long arrière pas écouté");
					robot.filetVuPlein();
					continue;
				}
				robot.filetVuVide(); // filet vide
			}
			
			Vec2RO positionVue = getPositionVue(capteurs[i], data.mesures[i], data.cinematique, data.angleRoueGauche, data.angleRoueDroite);
			if(positionVue == null)
				continue;
			
			/**
			 * Si ce qu'on voit est un obstacle de table, on l'ignore
			 */			
	    	for(ObstaclesFixes o: ObstaclesFixes.values())
	    		if(o.isVisible(capteurs[i].sureleve) && o.getObstacle().squaredDistance(positionVue) < distanceApproximation * distanceApproximation)
	    		{
	    			if(debugCapteurs)
	    				log.debug("Obstacle de table vu : "+o);
	                continue;
	    		}

	    	for(GameElementNames o: GameElementNames.values())
	    		if(table.isDone(o) != EtatElement.PRIS_PAR_NOUS && o.isVisible(capteurs[i].sureleve) && o.obstacle.squaredDistance(positionVue) < distanceApproximation * distanceApproximation)
	    		{
	    			if(debugCapteurs)
	    				log.debug("Élément de jeu vu : "+o);
	                continue;
	    		}

			/**
			 * Sinon, on ajoute
			 */
			Vec2RW positionEnnemi = new Vec2RW(data.mesures[i]+rayonEnnemi, capteurs[i].orientationRelativeRotate, true);
			positionEnnemi.plus(capteurs[i].positionRelativeRotate);
			positionEnnemi.rotate(orientationRobot);
			positionEnnemi.plus(positionRobot);
			
			if(positionEnnemi.isHorsTable())
			{
//				if(debugCapteurs)
//					log.debug("Hors table !");
				continue; // hors table
			}
			
			if(debugCapteurs)
				log.debug("Ajout d'un obstacle d'ennemi en "+positionEnnemi+" vu par "+c);
			
			//ObstacleProximity o =
			gridspace.addObstacleAndRemoveNearbyObstacles(positionEnnemi);
			
			/**
			 * Mise à jour de l'état de la table : un ennemi est passé
			 */
/*		    for(GameElementNames g: GameElementNames.values())
		        if(table.isDone(g) == EtatElement.INDEMNE && g.obstacle.isProcheObstacle(o, o.radius))
		        	table.setDone(g, EtatElement.PRIS_PAR_ENNEMI);
*/ // TODO
		}

		dstarlite.updateObstaclesEnnemi();
		dstarlite.updateObstaclesTable();
		chemin.checkColliding();
		if(enableCorrection)
			correctXYO(data);
	}
	
	/**
	 * Corrige les données et envoie la correction au robot
	 * La correction n'est pas toujours possible
	 * @param data
	 */
	private void correctXYO(SensorsData data)
	{
		int index1, index2;
		for(int k = 0; k < 2; k++)
		{
			if(k == 0)
			{
				index1 = CapteursRobot.ToF_LATERAL_GAUCHE_AVANT.ordinal();
				index2 = CapteursRobot.ToF_LATERAL_GAUCHE_ARRIERE.ordinal();
			}
			else
			{
				index1 = CapteursRobot.ToF_LATERAL_DROITE_AVANT.ordinal();
				index2 = CapteursRobot.ToF_LATERAL_DROITE_ARRIERE.ordinal();
			}
			
			Vec2RW pointVu1 = getPositionVue(capteurs[index1], data.mesures[index1], data.cinematique, data.angleRoueGauche, data.angleRoueDroite);
			if(pointVu1 == null)
				continue;

			Vec2RW pointVu2 = getPositionVue(capteurs[index2], data.mesures[index2], data.cinematique, data.angleRoueGauche, data.angleRoueDroite);
			if(pointVu2 == null)
				continue;			
			
			Mur mur1 = orientationMurProche(pointVu1);
			Mur mur2 = orientationMurProche(pointVu2);
			
//			log.debug("PointVu1 : "+pointVu1);
//			log.debug("PointVu2 : "+pointVu2);

//			log.debug("Murs : "+mur1+" "+mur2);

			// ces capteurs ne voient pas un mur proche, ou pas le même
			if(mur1 == null || mur2 == null || mur1 != mur2)
				continue;
			
			Vec2RO delta = pointVu1.minusNewVector(pointVu2);
			double deltaOrientation = mur1.orientation - delta.getArgument(); // on veut une mesure précise, donc on vite getFastArgument
			
			// le delta d'orientation qu'on cherche est entre -PI/2 et PI/2
			if(Math.abs(deltaOrientation) > Math.PI/2)
				deltaOrientation -= Math.PI;
			else if(Math.abs(deltaOrientation) < -Math.PI/2)
				deltaOrientation += Math.PI;
			
//			log.debug("Delta orientation : "+deltaOrientation);
			
			/*
			 * L'imprécision mesurée est trop grande. C'est probablement une erreur.
			 */
			if(Math.abs(deltaOrientation) > imprecisionMaxAngle)
			{
//				log.debug("Imprécision en angle trop grande ! "+Math.abs(deltaOrientation));
				continue;
			}
			
			pointVu1.rotate(deltaOrientation, data.cinematique.getPosition());
			pointVu2.rotate(deltaOrientation, data.cinematique.getPosition());
			
			double deltaX = 0;
			double deltaY = 0;
			if(mur1 == Mur.MUR_BAS)
				deltaY = -pointVu1.getY();
			else if(mur1 == Mur.MUR_HAUT)
				deltaY = -(pointVu1.getY() - 2000);
			else if(mur1 == Mur.MUR_GAUCHE)
				deltaX = -(pointVu1.getX() + 1500);
			else if(mur1 == Mur.MUR_DROIT)
				deltaX = -(pointVu1.getX() - 1500);
			
			/*
			 * L'imprécision mesurée est trop grande. C'est probablement une erreur.
			 */			
			if(Math.abs(deltaX) > imprecisionMaxPos || Math.abs(deltaY) > imprecisionMaxPos)
			{
//				log.debug("Imprécision en position trop grande !");
				continue;
			}
				
//			log.debug("Correction : "+deltaX+" "+deltaY+" "+deltaOrientation);
			
			Cinematique correction = new Cinematique(deltaX, deltaY, deltaOrientation, true, 0);
			if(System.currentTimeMillis() - dateLastMesureCorrection > peremptionCorrection) // trop de temps depuis le dernier calcul
				indexCorrection = 0;
	
			bufferCorrection[indexCorrection] = correction;
			indexCorrection++;
			if(indexCorrection == bufferCorrection.length)
			{
				Vec2RW posmoy = new Vec2RW();
				double orientationmoy = 0;
				for(int i = 0; i < bufferCorrection.length; i++)
				{
					posmoy.plus(bufferCorrection[i].getPosition());
					orientationmoy += bufferCorrection[i].orientationReelle;
				}
				posmoy.scalar(1./bufferCorrection.length);
				orientationmoy /= bufferCorrection.length;
				log.debug("Envoi d'une correction XYO : "+posmoy+" "+orientationmoy);
				serie.correctPosition(posmoy, orientationmoy);
				indexCorrection = 0;
			}
		}
		dateLastMesureCorrection = System.currentTimeMillis();
		
	}
	
	/**
	 * Renvoie la position vue par ce capteurs
	 * @param c
	 * @param mesure
	 * @param cinematique
	 * @return
	 */
	private Vec2RW getPositionVue(Capteur c, int mesure, Cinematique cinematique, double angleRoueGauche, double angleRoueDroite)
	{
		c.computePosOrientationRelative(cinematique, angleRoueGauche, angleRoueDroite);
		
		/**
		 * Si le capteur voit trop proche ou trop loin, on ne peut pas lui faire confiance
		 */
		if(mesure <= c.distanceMin || mesure >= c.portee)
		{
//			log.debug("Mesure d'un capteur trop loin ou trop proche.");
			return null;
		}
		
		Vec2RW positionVue = new Vec2RW(mesure, c.orientationRelativeRotate, true);
		positionVue.plus(c.positionRelativeRotate);
		positionVue.rotate(cinematique.orientationReelle);
		positionVue.plus(cinematique.getPosition());
		return positionVue;
	}
	
	private enum Mur
	{
		MUR_HAUT(0), MUR_BAS(0), MUR_GAUCHE(Math.PI/2), MUR_DROIT(Math.PI/2);
	
		private double orientation;
		
		private Mur(double orientation)
		{
			this.orientation = orientation;
		}
	}
	
	/**
	 * Renvoie l'orientation du mur le plus proche.
	 * Renvoie null si aucun mur proche ou ambiguité (dans un coin)
	 * @param pos
	 * @return
	 */
	private Mur orientationMurProche(Vec2RO pos)
	{
		double distanceMax = 3*imprecisionMaxPos; // c'est une première approximation, on peut être bourrin
		boolean murBas = Math.abs(pos.getY()) < distanceMax;
		boolean murDroit = Math.abs(pos.getX() - 1500) < distanceMax;
		boolean murHaut = Math.abs(pos.getY() - 2000) < distanceMax;
		boolean murGauche = Math.abs(pos.getX() + 1500) < distanceMax;
		
//		log.debug("État mur : "+murBas+" "+murDroit+" "+murHaut+" "+murGauche);

		if(!(murBas ^ murDroit ^ murHaut ^ murGauche)) // cette condition est fausse si on est près de 0 ou de 2 murs
			return null;
			
		if(murBas)
			return Mur.MUR_BAS;
		else if(murDroit)
			return Mur.MUR_DROIT;
		else if(murGauche)
			return Mur.MUR_GAUCHE;
		return Mur.MUR_BAS;
	}
}
