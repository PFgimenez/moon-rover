package scripts.anticipables;

import java.util.ArrayList;

import astar.arc.PathfindingNodes;
import obstacles.gameElement.GameElementNames;
import enums.Side;
import enums.Tribool;
import exceptions.FinMatchException;
import exceptions.ScriptHookException;
import exceptions.SerialConnexionException;
import exceptions.UnableToMoveException;
import hook.HookFactory;
import robot.cardsWrappers.enums.HauteurBrasClap;
import robot.RobotChrono;
import scripts.Script;
import strategie.GameState;
import utils.Config;
import utils.Log;

/**
 * Script des claps.
 * @author pf
 *
 */

public class ScriptClap extends Script {

	public ScriptClap(HookFactory hookgenerator, Config config, Log log)
	{
		super(hookgenerator, config, log);
	}

	// Deux métaversions, contenant chacune une version.
	// id 0: droite, les deux clap
	// id 1: droite, un seul clap (le 2e, celui de gauche)
	// id 2: gauche
	@Override
	public ArrayList<Integer> getVersions(GameState<RobotChrono> state) {
		ArrayList<Integer> out = new ArrayList<Integer>();
		// on tente même si c'est peut-être fait par l'ennemi
		if(state.gridspace.isDone(GameElementNames.CLAP_1) != Tribool.TRUE && state.gridspace.isDone(GameElementNames.CLAP_3) != Tribool.TRUE)
			out.add(0);
		// on autorise à choisir la version 1 même si la version 0 est possible
		if(state.gridspace.isDone(GameElementNames.CLAP_1) != Tribool.TRUE)
			out.add(1);
		if(state.gridspace.isDone(GameElementNames.CLAP_2) != Tribool.TRUE)
			out.add(2);
		return out;
	}

	@Override
	public PathfindingNodes point_entree(int id) {
		if(id == 0)
			return PathfindingNodes.CLAP_DROIT;
		else if(id == 1)
			return PathfindingNodes.CLAP_DROIT_SECOND;
		else
			return PathfindingNodes.CLAP_GAUCHE;
	}

	@Override
	public void execute(int id_version, GameState<?> state)
			throws UnableToMoveException, SerialConnexionException,
			FinMatchException, ScriptHookException
	{
//		ArrayList<Hook> hooks_entre_scripts = hookfactory.getHooksEntreScripts(state);
		// côté droit
		if(id_version == 0)
		{
			state.robot.tourner(Math.PI);
			// TODO: probablement pas possible s'il y a des plots dans ce coin
			state.robot.avancer_dans_mur(-100);
			state.robot.bougeBrasClap(Side.LEFT, HauteurBrasClap.FRAPPE_CLAP);
			state.robot.clapTombe();
			state.gridspace.setDone(GameElementNames.CLAP_3, Tribool.TRUE);
			state.robot.avancer(150);
			state.robot.bougeBrasClap(Side.LEFT, HauteurBrasClap.TOUT_EN_HAUT);
			state.robot.avancer(300);
			state.robot.bougeBrasClap(Side.LEFT, HauteurBrasClap.FRAPPE_CLAP);
			state.robot.clapTombe();
			state.gridspace.setDone(GameElementNames.CLAP_1, Tribool.TRUE);
			state.robot.avancer(150);
		}
		else if(id_version == 1)
		{
			Side s;
			double angle = state.robot.getOrientation();
			double cos = Math.cos(angle);
			double sin = Math.sin(angle);
			if(cos+sin > 0)
				s = Side.RIGHT;
			else
				s = Side.LEFT;
			if(cos-sin < 0)
			{
				if(s == Side.RIGHT)
					state.robot.tourner(Math.PI/4);
				else
					state.robot.tourner(-3*Math.PI/4);
			}
			state.robot.bougeBrasClap(s, HauteurBrasClap.FRAPPE_CLAP);
			if(s == Side.RIGHT)
				state.robot.tourner(-Math.PI/4);
			else
				state.robot.tourner(3*Math.PI/4);
			state.gridspace.setDone(GameElementNames.CLAP_1, Tribool.TRUE);
		}
		else if(id_version == 2)// côté gauche
		{
			state.robot.tourner(0);
			state.robot.bougeBrasClap(Side.RIGHT, HauteurBrasClap.FRAPPE_CLAP);
			state.robot.clapTombe();
			state.gridspace.setDone(GameElementNames.CLAP_2, Tribool.TRUE);
			state.robot.avancer(300);
		}
	}

	@Override
	protected void termine(GameState<?> state) throws SerialConnexionException,
			FinMatchException, ScriptHookException {
		state.robot.bougeBrasClap(Side.LEFT, HauteurBrasClap.RENTRE);
		state.robot.bougeBrasClap(Side.RIGHT, HauteurBrasClap.RENTRE);
	}

	@Override
	public PathfindingNodes point_sortie(int id) {
		if(id == 0)
			return PathfindingNodes.SORTIE_CLAP_DROIT;
		else if(id == 1)
			return PathfindingNodes.SORTIE_CLAP_DROIT_SECOND;
		else
			return PathfindingNodes.SORTIE_CLAP_GAUCHE;
	}

}
