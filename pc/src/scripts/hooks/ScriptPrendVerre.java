package scripts.hooks;

import exceptions.FinMatchException;
import exceptions.ScriptException;
import exceptions.SerialConnexionException;
import exceptions.UnableToMoveException;
import hook.HookFactory;
import robot.RobotReal;
import scripts.ScriptHook;
import strategie.GameState;
import table.GameElementNames;
import utils.Config;
import utils.Log;

/**
 * Script hook de prise de verre.
 * C'est le script appelé lorsqu'on a détecté un verre.
 * @author pf
 *
 */

public class ScriptPrendVerre extends ScriptHook
{

	public ScriptPrendVerre(HookFactory hookgenerator, Config config, Log log)
	{
		super(hookgenerator, config, log);
	}

	@Override
	protected void termine(GameState<RobotReal> gamestate) throws ScriptException,
			FinMatchException, SerialConnexionException
	{
		// TODO (avec règlement)
	}

	@Override
	protected void execute(GameElementNames id_version, GameState<RobotReal> state)
			throws UnableToMoveException, SerialConnexionException,
			FinMatchException
	{
		// TODO (avec règlement)
	}

}
