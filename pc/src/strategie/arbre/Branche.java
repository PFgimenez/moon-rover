package strategie.arbre;

import java.util.ArrayList;

import robot.RobotChrono;
import scripts.Script;
import smartMath.Vec2;
import strategie.GameState;

/**
 * Classe formalisant le concept d'une branche (ou d'une sous-branche) de l'arbre des possibles.
 * En termes de graphes, l'arbre est défini commme suit :
 * 		chaque sommet de l'arbre est une table de jeu dans un certain état.
 * 		chaque arc de l'arbre est une action qui modifie l'état d'une table
 * Une branche est un arc avec une note suivit d'un sommet duquel partent à leurs tours une mutitude de sous-branches.
 * Une branche possède une note, égale a une combinaison des notes de ses sous-branches.
 * 		(ie branche = triplet note-action-table)
 * Le role de Stratégie est de faire calculer les notes de toutes les branches de l'arbres en les considérants jusqu'à une certaine profondeur.
 * 
 * @author karton
 *
 */

public class Branche 
{
	
	// Paramètres généraux
	private boolean useCachedPathfinding; 	// utiliser un pathfinding en cache ou calculée spécialement pour l'occasion ?
	// TODO : changer en profondeur restante
	public int profondeur;					// Cette branche est-elle la dernière à évaluer, ou faut-il prendre en compte des sous-branches ? 
	public long date;						// date à laquelle cette branche est parcourue
	
	// Notes
	public float note;				// note de toute la branche, prenant en comte les notes des sous-branches
	public float localNote; 		// note de l'action effectuée au début de cette branche
	public boolean isNoteComputed;	// l'attribut "note" a-t-il été calculé ?
	
	// Action a executer entre le sommet juste avant cette branche et l'état final
	public Script script;							// script de l'action
	public int metaversion;							// metaversion du script
	public long dureeScript;						// durée nécessaire pour effectuer le script
	private int scoreScript;						// durée nécessaire pour effectuer le script
	public boolean isActionCharacteisticsComputed;  // la durée et le score du script ont-ils étés calculés ?
	
	GameState<RobotChrono> state;
	
	// Sous branches contenant toutes les autres actions possibles a partir de l'état final
	public ArrayList<Branche> sousBranches;
	
	
	/**
	 * @param useCachedPathfinding
	 * @param prodondeur
	 * @param date
	 * @param script
	 * @param metaversion
	 * @param etatInitial
	 * @param robot
	 * @param pathfinder
	 */
	public Branche(boolean useCachedPathfinding, int profondeur, Script script, int metaversion, GameState<RobotChrono> state)
	{
	    this.state = state;
		this.useCachedPathfinding = useCachedPathfinding;
		this.profondeur = profondeur;
		this.script = script;
		this.metaversion = metaversion;
		sousBranches = new ArrayList<Branche>();
		isNoteComputed  = false;
		isActionCharacteisticsComputed = false;
		computeActionCharacteristics();
		
	}

	
	public void computeActionCharacteristics()
	{
		if(!isActionCharacteisticsComputed)
		{
			//scoreScript = script.meta_score(metaversion, state);
			//dureeScript = script.metacalcule(metaversion, state, useCachedPathfinding);
			
			// Substitut de test en attendant que les scipts buggent moins.
			scoreScript = 2;
			dureeScript = 12000;	// 12 sec !
			
			isActionCharacteisticsComputed = true;
		}
	}
	
	
	/** Méthode qui prend les notes de chaque sous branche (en supposant qu'elles sont déjà calculés et qu'il n'y a plus qu'a
	 * les considérer) et les mélange popur produire la note de  toute cette branche
	 * Il n'y a que cette méthode qui doit modifier this.note
	 */
	public void computeNote()
	{
		computeLocalNote();
		
		if(profondeur == 0)
			// Pas de prise en compte d'actions futures si on est déjà a profondeur maximale 
			note = localNote;
		else
		{
			// Si il reste de la profondeur en aval, on prend en compte les sous branches
			// TODO : mixer les notes des sous-branches avec noteLocale
			
			// pour l'instant, un simple max ira bien :         note = noteLocale + max( note des sous branches )
			note = -1;	// Valeur incorecte pour forcer la réattribution
			for (int i = 0; i< sousBranches.size(); ++i)
				if(sousBranches.get(i).note > this.note)
					note = sousBranches.get(i).note; 
			note += localNote;
		}
		
		// marque la note comme calculée 
		isNoteComputed = true;
		
	}


	/** Méthode qui calcule la note d'une action. La note calculée ici prend en compte une unique action, pas un enchainement
	 * La note d'un script est fonction de son score, de sa durée, de la distance de l'ennemi à l'action 
	 * @param score
	 * @param duree
	 * @param id
	 * @param script
	 * @return note
	 */
	private void computeLocalNote()
	{ 
		// la note de la branche se base sur certaines caractéristiques de l'action par laquelle elle débute.
		if(!isActionCharacteisticsComputed)
			computeActionCharacteristics();
		// TODO
		int id = script.version_asso(metaversion).get(0);
		int A = 1;
		int B = 1;
		float prob = script.proba_reussite();
		
		//abandon de prob_deja_fait
		Vec2[] position_ennemie = state.table.get_positions_ennemis();
		float pos = (float)1.0 - (float)(Math.exp(-Math.pow((double)(script.point_entree(id).distance(position_ennemie[0])),(double)2.0)));
		// pos est une valeur qui décroît de manière exponentielle en fonction de la distance entre le robot adverse et là où on veut aller
		localNote = (scoreScript*A*prob/dureeScript+pos*B)*prob;
		
//		log.debug((float)(Math.exp(-Math.pow((double)(script.point_entree(id).distance(position_ennemie[0])),(double)2.0))), this);
		
	}
	
	
	
	
	
}