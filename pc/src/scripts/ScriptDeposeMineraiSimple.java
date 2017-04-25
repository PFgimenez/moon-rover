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

import exceptions.ActionneurException;
import exceptions.UnableToMoveException;
import pathfinding.GameState;
import pathfinding.SensFinal;
import pathfinding.astar.arcs.CercleArrivee;
import robot.Robot;
import utils.Log;
import utils.Vec2RO;

/**
 * Le script qui dépose le minerai dans la base mais pas dans le filet
 * @author pf
 *
 */

public class ScriptDeposeMineraiSimple extends Script
{
	private CercleArrivee cercle;
	
	public ScriptDeposeMineraiSimple(Log log, CercleArrivee cercle)
	{
		super(log);
		this.cercle = cercle;
	}

	@Override
	public void setUpCercleArrivee()
	{
		cercle.set(new Vec2RO(700, 1800), Math.PI, 200, SensFinal.MARCHE_ARRIERE);
	}

	@Override
	protected void run(GameState<? extends Robot> state) throws InterruptedException, UnableToMoveException
	{
		try {
			try {
				state.robot.baisseFilet();
			} catch (ActionneurException e) {
				e.printStackTrace();
			}
			state.robot.ouvreFilet();
		}
		finally
		{
			try {
				state.robot.leveFilet();
			} catch (ActionneurException e) {
				e.printStackTrace();
			}
			state.robot.fermeFilet();
		}
	}
	
	@Override
	public boolean equals(Object other)
	{
		return other instanceof ScriptDeposeMineraiSimple; // de toute façon, il n'y a qu'un seul script de ce type
	}
	
	@Override
	public int hashCode()
	{
		return 0;
	}

	@Override
	public String toString()
	{
		return "DEPOSE_SIMPLE";
	}
}
