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

package scripts;

import exceptions.ActionneurException;
import exceptions.MemoryManagerException;
import exceptions.UnableToMoveException;
import pathfinding.RealGameState;
import robot.Cinematique;
import robot.Speed;
import table.EtatElement;
import table.GameElementNames;
import utils.Log.Verbose;

/**
 * Le script qui récupère les balles d'un petit cratère
 * 
 * @author pf
 *
 */

public class ScriptHomolo extends Script
{
	private GameElementNames element;

	public ScriptHomolo(GameElementNames element)
	{
		this.element = element;
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof ScriptHomolo && other.hashCode() == hashCode();
	}

	@Override
	public int hashCode()
	{
		return element.hashCode();
	}

	@Override
	public void setUpCercleArrivee()
	{
		cercle.set(element, 250, 60, -60, 10, -10);
	}

	@Override
	protected void run(RealGameState state) throws InterruptedException, UnableToMoveException, MemoryManagerException
	{
		int nbEssai = 3;
		cercle.set(element, 203, 5, -5, 3, -1); // nouveau rayon : 200
		state.robot.avanceToCircle(Speed.TEST);
		while(!state.robot.isArrivedAsser() && nbEssai > 0)
		{
			log.warning("Le robot n'est pas bien arrivé sur le cratère : on retente.", Verbose.SCRIPTS.masque);
			state.robot.avance(40, Speed.TEST);
			state.robot.avanceToCircle(Speed.TEST);
			nbEssai--;
		}

		if(!state.robot.isArrivedAsser())
			log.warning("On lance le script même en étant mal positionné !", Verbose.SCRIPTS.masque);
		
		try
		{
			try
			{
				state.robot.rearme();
			}
			catch(ActionneurException e)
			{
				log.warning(e);
			}

			try
			{
				state.robot.rearmeAutreCote();
			}
			catch(ActionneurException e)
			{
				log.warning(e);
			}

			state.robot.ouvreFilet();

			try
			{
				state.robot.baisseFilet();
			}
			catch(ActionneurException e)
			{
				log.warning(e);
				try
				{
					state.robot.leveFilet();
				}
				catch(ActionneurException e1)
				{
					log.warning(e1);
					state.robot.fermeFilet();
					throw e1;
				}

				try
				{
					state.robot.baisseFilet();
				}
				catch(ActionneurException e1)
				{
					log.warning(e1);
					try
					{
						state.robot.leveFilet();
					}
					catch(ActionneurException e2)
					{
						log.warning(e2);
					}
					state.robot.fermeFilet();
					throw e1;
				}
			}

			state.robot.fermeFilet();
			state.robot.ouvreFilet();
			state.robot.fermeFiletForce();

			try
			{
				state.robot.leveFilet();
			}
			catch(ActionneurException e)
			{
				log.warning(e);
			}

			// le minerai est considéré comme pris
			state.table.setDone(element, EtatElement.PRIS_PAR_NOUS);
			state.robot.setFiletPlein(true);

			state.robot.ouvreFilet();
			try
			{
				state.robot.ejecteBalles();
				Thread.sleep(500);
				try
				{
					state.robot.ejecteBallesAutreCote();
					try
					{
						state.robot.rearmeAutreCote();
					}
					catch(ActionneurException e)
					{
						log.warning(e);
						try
						{
							state.robot.ejecteBallesAutreCote();
						}
						catch(ActionneurException e1)
						{
							log.warning(e1);
						}
						try
						{
							state.robot.rearmeAutreCote();
						}
						catch(ActionneurException e1)
						{
							log.warning(e1);
						}
					}
				}
				catch(ActionneurException e)
				{
					log.warning(e);
				}
				finally
				{
					try
					{
						state.robot.rearme();
					}
					catch(ActionneurException e)
					{
						log.warning(e);
						try
						{
							state.robot.ejecteBalles();
						}
						catch(ActionneurException e1)
						{
							log.warning(e1);
						}
						try
						{
							state.robot.rearme();
						}
						catch(ActionneurException e1)
						{
							log.warning(e1);
						}
					}
				}
			}
			catch(ActionneurException e)
			{
				log.warning(e);
			}
			finally
			{
				state.robot.fermeFilet();
			}

		}
		catch(ActionneurException e)
		{
			state.robot.setFiletPlein(false);
			log.warning(e);
		}
	}
	
	@Override
	public Cinematique getPointEntree()
	{
		return null;
	}

}
