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
import pathfinding.SensFinal;
import robot.Cinematique;
import robot.Speed;
import serie.SerialProtocol.InOrder;
import serie.SerialProtocol.State;
import serie.Ticket;
import utils.Vec2RW;

/**
 * Le script qui dépose le minerai dans le panier
 * 
 * @author pf
 *
 */

public class ScriptDeposeMinerai extends Script
{
	private Vec2RW centreDebut = new Vec2RW(610, 2000-180);
	private Vec2RW centreBout = new Vec2RW(1600, 2000-160);
	private Vec2RW centreMilieu = new Vec2RW(1600, 2000-180);
	private double rayonDebut = 180;
	private double rayonBout = 400;
	private double rayonMilieu = 600;
	private boolean gauche;

	public ScriptDeposeMinerai(boolean gauche)
	{
		this.gauche = gauche;
		if(gauche)
		{
			centreDebut.setX(-centreDebut.getX());
			centreMilieu.setX(-centreBout.getX());
			centreBout.setX(-centreBout.getX());
		}
	}
	
	@Override
	public Cinematique getPointEntree()
	{
		return null;
//		return pos.clone();
	}
	
	@Override
	public void setUpCercleArrivee()
	{
		if(gauche)
			cercle.set(centreDebut, Math.PI, rayonDebut, SensFinal.MARCHE_AVANT, new Double[] {-Math.PI / 5, Math.PI / 5}, 70, -70, 10, -10);
		else
			cercle.set(centreDebut, Math.PI, rayonDebut, SensFinal.MARCHE_AVANT, new Double[] {-Math.PI, -4 * Math.PI / 5, 4 * Math.PI / 5, Math.PI}, 70, -70, 10, -10);
	}

	@Override
	protected void run(RealGameState state) throws InterruptedException, MemoryManagerException
	{
		try
		{
			Ticket t = state.robot.traverseBascule();
			Thread.sleep(500);
			try {
				cercle.set(centreMilieu, 0, rayonMilieu, SensFinal.MARCHE_ARRIERE, null, 10, -10, 3, -3);
				state.robot.avanceToCircle(Speed.BASCULE);
				cercle.set(centreBout, 0, rayonBout, SensFinal.MARCHE_ARRIERE, null, 10, -10, 3, -3);
				state.robot.avanceToCircle(Speed.BASCULE);
			}
			catch(UnableToMoveException e)
			{
				log.warning(e);
				double xRobot = state.robot.getCinematique().getPosition().getX();
				double yRobot = state.robot.getCinematique().getPosition().getY();
				if(yRobot < 1650 || (gauche && (xRobot < -1350 || xRobot > -1140)) || (!gauche && (xRobot < 1140 || xRobot > 1350)))
				{
					log.warning("On est trop loin ! Position : "+state.robot.getCinematique().getPosition());
					throw e;
				}
				log.debug("On a eu un problème en reculant, mais on est bien positionné, alors on continue !");
			}
	
			InOrder o = t.attendStatus();
			if(o.etat == State.KO)
			{
				try {
					state.robot.leveFilet();
				}
				catch(ActionneurException e)
				{
					log.warning(e);
				}
			}

			state.robot.ouvreFilet();
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
		catch(ActionneurException | UnableToMoveException e)
		{
			log.warning(e);
		}
		finally
		{
			state.robot.fermeFilet();
			cercle.set(centreDebut, 0, rayonDebut, SensFinal.MARCHE_ARRIERE, null, 10, -10, 3, -3);
			try {
				state.robot.avanceToCircle(Speed.BASCULE);
			} catch (UnableToMoveException e) {
				// On retente !
				try {
					state.robot.avanceToCircle(Speed.BASCULE);
				} catch (UnableToMoveException e1) {
					e1.printStackTrace();
				}
				e.printStackTrace();
			}
		}
	}

	@Override
	public boolean equals(Object other)
	{
		return other instanceof ScriptDeposeMinerai && ((ScriptDeposeMinerai) other).gauche == gauche;
	}

	@Override
	public int hashCode()
	{
		return 64+(gauche ? 1 : 0);
	}
}
