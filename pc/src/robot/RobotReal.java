package robot;

import utils.Log;
import utils.Config;
import utils.Sleep;
import utils.Vec2;
import hook.Callback;
import hook.Executable;
import hook.Hook;
import hook.methods.ThrowsChangeDirection;
import hook.types.HookDemiPlan;

import java.util.ArrayList;

import buffer.DataForSerialOutput;
import permissions.ReadOnly;
import requete.RequeteSTM;
import requete.RequeteType;
import exceptions.FinMatchException;
import exceptions.ScriptHookException;
import exceptions.SerialConnexionException;
import exceptions.UnableToMoveException;

/**
 * Effectue le lien entre le code et la réalité (permet de parler à la stm, d'interroger les capteurs, etc.)
 * @author pf
 *
 */

public class RobotReal extends Robot
{
//	private Table table;
	private DataForSerialOutput stm;
	private RequeteSTM requete;
	
	// Constructeur
	public RobotReal(DataForSerialOutput stm, Log log, RequeteSTM requete)
 	{
		super(log);
		this.stm = stm;
		this.requete = requete;
	}
	
	/*
	 * MÉTHODES PUBLIQUES
	 */
	
	@Override
	public void updateConfig(Config config)
	{
		super.updateConfig(config);
	}

	@Override
	public void useConfig(Config config)
	{
		super.useConfig(config);
	}
	
/*	@Override
	public void desactiveAsservissement()
	{
		stm.desactiveAsservissement();
	}

	@Override
	public void activeAsservissement()
	{
		stm.activeAsservissement();
	}*/

	/**
	 * Avance d'une certaine distance donnée en mm (méthode bloquante), gestion des hooks
	 * @throws UnableToMoveException 
	 * @throws FinMatchException 
	 * @throws ScriptHookException 
	 */
	@Override
    public void avancer(int distance, ArrayList<Hook> hooks, boolean mur) throws UnableToMoveException
	{
		// Il est nécessaire d'ajouter le hookFinMatch avant chaque appel de stm qui prenne un peu de temps (avancer, tourner, ...)
//		hooks.add(hookFinMatch);
		try {
			synchronized(requete)
			{
				RequeteType type;
				stm.avancer(distance, hooks, mur);
				do {
					requete.wait();
					type = requete.get();
					if(type == RequeteType.BLOCAGE_MECANIQUE)
						throw new UnableToMoveException();
				} while(type != RequeteType.TRAJET_FINI);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}	

	/**
	 * Modifie la vitesse de translation
	 * @param Speed : l'une des vitesses indexées dans enums.
	 * @throws FinMatchException 
	 * 
	 */
	@Override
	public void set_vitesse(Speed vitesse)
	{
		stm.setSpeed(vitesse);
		log.debug("Modification de la vitesse: "+vitesse);
	}
	
	/*
	 * ACTIONNEURS
	 */
	
	/* 
	 * GETTERS & SETTERS
	 */
	public void setPositionOrientationSTM(Vec2<ReadOnly> position, double orientation)
	{
		stm.setPositionOrientation(position, orientation);
	}

	public void setPositionOrientationJava(Vec2<ReadOnly> position, double orientation)
	{
		Vec2.copy(position, this.position);
		this.orientation = orientation;
	}

	public void updatePositionOrientation()
	{
	    stm.getPositionOrientation();
	}
    
    /**
	 * Méthode sleep utilisée par les scripts
     * @throws FinMatchException 
	 */
	@Override	
	public void sleep(long duree, ArrayList<Hook> hooks)
	{
		Sleep.sleep(duree);
	}

    @Override
    public void stopper()
    {
        try {
			stm.immobilise();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    @Override
    public void tourner(double angle) throws UnableToMoveException
    {
		try {
			synchronized(requete)
			{
				RequeteType type;
				stm.turn(angle, new ArrayList<Hook>());
				do {
					requete.wait();
					type = requete.get();
					if(type == RequeteType.BLOCAGE_MECANIQUE)
						throw new UnableToMoveException();
				} while(type != RequeteType.TRAJET_FINI);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
	@Override
    public RobotChrono cloneIntoRobotChrono()
    {
    	RobotChrono rc = new RobotChrono(log);
    	copy(rc);
    	return rc;
    }
    
    // Cette copie est un peu plus lente que les autres car il y a un appel série
    // Néanmoins, on ne fait cette copie qu'une fois par arbre.
    @Override
    public void copy(RobotChrono rc)
    {
        super.copy(rc);
		Vec2.copy(getPosition(), rc.position);
		rc.orientation = getOrientation();
    }

	@Override
    public long getTempsDepuisDebutMatch()
    {
    	return System.currentTimeMillis() - dateDebutMatch;
    }
	
	public boolean isEnemyHere() {
		// TODO
		return false;
	}
	
	public void setHookTrajectoireCourbe(HookDemiPlan hookTrajectoireCourbe)
	{
		Executable action = new ThrowsChangeDirection();
		hookTrajectoireCourbe.ajouter_callback(new Callback(action));
//		this.hookTrajectoireCourbe = hookTrajectoireCourbe;
	}
	
	/**
	 * Envoie un ordre à la série. Le protocole est défini dans l'enum ActuatorOrder
	 * @param order l'ordre à envoyer
	 * @throws SerialConnexionException en cas de problème de communication avec la carte actionneurs
	 * @throws FinMatchException 
	 */
	public void useActuator(ActuatorOrder order)
	{
		if(symetrie)
			order = order.getSymmetry();
		stm.utiliseActionneurs(order);
/*		try {
			synchronized(requete)
			{
				if(symetrie)
					order = order.getSymmetry();
				stm.utiliseActionneurs(order);
				do {
					requete.wait();
					// TODO gérer le cas du problème d'actionneurs
				} while(requete.type != RequeteType.ACTIONNEURS_FINI);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
	}

	@Override
	public Vec2<ReadOnly> getPosition() {
		return position.getReadOnly();
	}

	@Override
	public double getOrientation() {
		return orientation;
	}

}
