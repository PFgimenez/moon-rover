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

package scripts;

import exceptions.UnableToMoveException;
import pathfinding.GameState;
import robot.Robot;
import utils.Log;

/**
 * Script abstrait
 * @author pf
 *
 */

public abstract class Script
{
	protected Log log;
	
	public Script(Log log)
	{
		this.log = log;
	}
	
	public abstract void setUpCercleArrivee();
	
	protected abstract void run(GameState<? extends Robot> state) throws InterruptedException, UnableToMoveException;
	
	protected abstract void termine(GameState<? extends Robot> state) throws InterruptedException, UnableToMoveException;
	
	public void execute(GameState<? extends Robot> state) throws InterruptedException
	{
		try {
			run(state);
		}
		catch(UnableToMoveException e)
		{
			log.critical("Erreur lors de l'exécution du script "+getClass().getSimpleName());
		}
		finally
		{
			try {
				termine(state);
			}			
			catch(UnableToMoveException e)
			{
				log.critical("La terminaison de "+getClass().getSimpleName()+" a rencontré un problème. Nouvelle tentative.");
				try {
					termine(state);
				}
				catch(UnableToMoveException e1)
				{
					log.critical("La terminaison de "+getClass().getSimpleName()+" a encore échoué !");
				}
			}
		}

	}

}
