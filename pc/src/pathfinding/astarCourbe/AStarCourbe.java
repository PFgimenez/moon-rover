package pathfinding.astarCourbe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

import obstacles.types.ObstacleRectangular;
import pathfinding.CheminPathfinding;
import pathfinding.RealGameState;
import pathfinding.astarCourbe.arcs.ArcManager;
import pathfinding.astarCourbe.arcs.ArcCourbe;
import pathfinding.astarCourbe.arcs.ArcCourbeCubique;
import pathfinding.dstarlite.DStarLite;
import pathfinding.dstarlite.GridSpace;
import container.Service;
import debug.Fenetre;
import exceptions.PathfindingException;
import robot.Cinematique;
import robot.DirectionStrategy;
import robot.RobotChrono;
import robot.Speed;
import utils.Config;
import utils.Log;

/**
 * AStar* simplifié, qui lisse le résultat du D* Lite et fournit une trajectoire courbe
 * On suppose qu'il n'y a jamais collision de noeuds
 * (je parle de collision dans le sens "égalité", pas "robot qui fonce dans le mur"…)
 * @author pf
 *
 */

public class AStarCourbe implements Service
{
	protected DirectionStrategy directionstrategyactuelle;
	protected Log log;
	private ArcManager arcmanager;
	private DStarLite dstarlite;
	private RealGameState state;
	private MemoryManager memorymanager;
	protected Fenetre fenetre;
	private Cinematique arrivee;
	private AStarCourbeNode depart;
	private CheminPathfinding chemin;
	private Speed vitesseMax;
	
	private volatile boolean partialPathNeeded = false;
	private volatile boolean isUpdating = false;
	
	/**
	 * Comparateur de noeud utilisé par la priority queue
	 * @author pf
	 *
	 */
	private class AStarCourbeNodeComparator implements Comparator<AStarCourbeNode>
	{
		@Override
		public int compare(AStarCourbeNode arg0, AStarCourbeNode arg1)
		{
			int out = (int) Math.signum((arg0.f_score - arg1.f_score));// << 1;
//			if(arg0.g_score > arg1.g_score)
//				out++;
			return out;
		}
	}

	private final ArrayList<Integer> closedset = new ArrayList<Integer>();
	private final PriorityQueue<AStarCourbeNode> openset = new PriorityQueue<AStarCourbeNode>(GridSpace.NB_POINTS, new AStarCourbeNodeComparator());

	/**
	 * Constructeur du AStarCourbe
	 */
	public AStarCourbe(Log log, DStarLite dstarlite, ArcManager arcmanager, RealGameState state, CheminPathfinding chemin, MemoryManager memorymanager)
	{
		this.log = log;
		this.arcmanager = arcmanager;
//		this.state = state;
		this.memorymanager = memorymanager;
		this.chemin = chemin;
		depart = new AStarCourbeNode();
		this.state = state;
		this.dstarlite = dstarlite;
	}
	
	/**
	 * Le calcul du AStarCourbe
	 * @param depart
	 * @return
	 */
	@SuppressWarnings("unused")
	protected final void process()
	{
		depart.came_from = null;
		depart.came_from_arc = null;
		depart.g_score = 0;
		depart.f_score = arcmanager.heuristicCost(depart);

		openset.clear();
		openset.add(depart);	// Les nœuds à évaluer
		closedset.clear();
		
		AStarCourbeNode current, successeur;

		do
		{
			current = openset.poll();
			
			int hash = ((RobotChrono)current.state.robot).getCinematique().hashCode();
			if(closedset.contains(hash))
				continue;

			closedset.add(hash);
			
			if(Config.graphicObstacles && current.came_from_arc != null)
				for(int i = 0; i < current.came_from_arc.getNbPoints(); i++)
				{
//					Sleep.sleep(20);
					Fenetre.getInstance().addObstacleEnBiais(new ObstacleRectangular(current.came_from_arc.getPoint(i).getPosition(), 4, 4, 0));
				}
			
			// ce calcul étant un peu lourd, on ne le fait que si le noeud a été choisi, et pas à la sélection des voisins (dans hasNext par exemple)
			if(!arcmanager.isReachable(current))
			{
				memorymanager.destroyNode(current);
				continue; // collision mécanique attendue. On passe au suivant !
			}
			
			// Si on est arrivé, on reconstruit le chemin
			// On est arrivé seulement si on vient d'un arc cubique
			if(current.came_from_arc instanceof ArcCourbeCubique || memorymanager.getSize() > 10000)
			{
				if(memorymanager.getSize() > 10000) // étant donné qu'il peut continuer jusqu'à l'infini...
				{
					memorymanager.empty();
					log.critical("AStarCourbe n'a pas trouvé de chemin !");
					return;
				}

				log.debug("On est arrivé !");
				partialReconstruct(current, true);
				log.debug(memorymanager.getSize());
				memorymanager.empty();
				return;
			}

			// On parcourt les voisins de current

			arcmanager.reinitIterator(current, directionstrategyactuelle);
			while(arcmanager.hasNext())
			{
				if(partialPathNeeded)
				{
					partialPathNeeded = false;
					partialReconstruct(current, false);
					// Il est nécessaire de copier current dans depart car current
					// est effacé quand le memorymanager est vidé. Cette copie n'est effectuée qu'ici
					current.copyReconstruct(depart);
					memorymanager.empty();
					openset.clear();
					openset.add(depart);
					break;
				}

				successeur = memorymanager.getNewNode();

				// S'il y a un problème, on passe au suivant (interpolation cubique impossible par exemple)
				if(!arcmanager.next(successeur, vitesseMax, arrivee))
				{
					memorymanager.destroyNode(successeur);
					continue;
				}

				successeur.g_score = current.g_score + arcmanager.distanceTo(successeur);
				
				successeur.f_score = successeur.g_score + arcmanager.heuristicCost(successeur);// / successeur.came_from_arc.getVitesseTr();

				successeur.came_from = current;

				openset.add(successeur);
				
			}

		} while(!openset.isEmpty());
		
		/**
		 * Impossible car un nombre infini de nœuds !
		 */
		memorymanager.empty();
		log.critical("AStarCourbe n'a pas trouvé de chemin !");
		return;
	}
	
	/**
	 * Reconstruit le chemin. Il peut reconstruire le chemin même si celui-ci n'est pas fini.
	 * En effet, en faisant "openset.clear()", il force le pathfinding a continuer sur sa lancée sans
	 * remettre en cause la trajectoire déjà calculée
	 * @param best
	 * @param last
	 */
	private final void partialReconstruct(AStarCourbeNode best, boolean last)
	{
		synchronized(chemin)
		{
			AStarCourbeNode noeud_parent = best;
			ArcCourbe arc_parent = best.came_from_arc;
			while(noeud_parent.came_from != null)
			{
				chemin.add(arc_parent);
				noeud_parent = noeud_parent.came_from;
				arc_parent = noeud_parent.came_from_arc;
			}
//			chemin.setFinish(last);
			chemin.notify(); // on prévient le thread d'évitement qu'un chemin est disponible
		}
	}

	@Override
	public void updateConfig(Config config)
	{}

	@Override
	public void useConfig(Config config)
	{}
				
	/**
	 * Calcul d'un chemin à partir d'un certain état (state) et d'un point d'arrivée (endNode).
	 * Le boolean permet de signaler au pathfinding si on autorise ou non le shootage d'élément de jeu pas déjà pris.
	 * @param state
	 * @param endNode
	 * @param shoot_game_element
	 * @return
	 * @throws PathfindingException 
	 */
	public void computeNewPath(Cinematique arrivee, boolean ejecteGameElement, DirectionStrategy directionstrategy) throws PathfindingException
	{
//		if(Config.graphicAStarCourbe)
//			fenetre.setColor(arrivee, Fenetre.Couleur.VIOLET);
		vitesseMax = Speed.STANDARD;
		this.directionstrategyactuelle = directionstrategy;
		arcmanager.setEjecteGameElement(ejecteGameElement);
		this.arrivee = arrivee;
		depart.init();
		state.copyAStarCourbe(depart.state);
		
		dstarlite.computeNewPath(((RobotChrono)depart.state.robot).getCinematique().getPosition(), arrivee.getPosition());
		process();
	}
	
	public synchronized void updatePath() throws PathfindingException
	{
		isUpdating = true;
		synchronized(state)
		{
			depart.init();
			state.copyAStarCourbe(depart.state);
		}
		vitesseMax = Speed.REPLANIF;
		
		dstarlite.updatePath(((RobotChrono)depart.state.robot).getCinematique().getPosition());
		chemin.clear();
		process();
		isUpdating = false;
	}

	public void givePartialPath()
	{
		partialPathNeeded = true;
	}

	public boolean isUpdating()
	{
		return isUpdating;
	}

	
}