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

package pathfinding.astar.arcs.vitesses;

import pathfinding.DirectionStrategy;
import pathfinding.astar.arcs.ClothoidesComputer;
import robot.Cinematique;

/**
 * Arc de clothoïde de longueur constante
 * 
 * @author pf
 *
 */

public enum VitesseClotho implements VitesseCourbure
{
	COURBURE_IDENTIQUE(0),
	GAUCHE_0(1),
	DROITE_0(-1),
	GAUCHE_1(4),
	DROITE_1(-4),
	GAUCHE_2(9),
	DROITE_2(-9),

	COURBURE_IDENTIQUE_AFTER_STOP(0),
	COURBURE_IDENTIQUE_G1_AFTER_STOP(1, 0),
	COURBURE_IDENTIQUE_D1_AFTER_STOP(-1, 0),
	COURBURE_IDENTIQUE_G3_AFTER_STOP(3, 0),
	COURBURE_IDENTIQUE_D3_AFTER_STOP(-3, 0),

	COURBURE_IDENTIQUE_REBROUSSE(0, 0),
	COURBURE_IDENTIQUE_G1_REBROUSSE(1, 0),
	COURBURE_IDENTIQUE_D1_REBROUSSE(-1, 0),
	COURBURE_IDENTIQUE_G3_REBROUSSE(3, 0),
	COURBURE_IDENTIQUE_D3_REBROUSSE(-3, 0);

	public final int vitesse; // vitesse en (1/m)/m = 1/m^2
	public final int courbureInitiale; // courbure en m^-1
	public final int squaredRootVitesse; // sqrt(abs(vitesse))
	public final boolean positif; // calculé à la volée pour certaine vitesse
	public final boolean rebrousse;
	public final boolean arret;

	private VitesseClotho(int vitesse)
	{
		this(0, vitesse);
	}

	private VitesseClotho(int courbureInitiale, int vitesse)
	{
		this.courbureInitiale = courbureInitiale;
		rebrousse = toString().endsWith("_REBROUSSE");
		arret = toString().endsWith("_AFTER_STOP");
		this.vitesse = vitesse;
		positif = vitesse >= 0;
		this.squaredRootVitesse = (int) Math.sqrt(Math.abs(vitesse));
	}

	@Override
	public boolean isAcceptable(Cinematique c, DirectionStrategy directionstrategyactuelle, double courbureMax)
	{

		// il y a un problème si :
		// - on veut rebrousser chemin
		// ET
		// - si :
		// - on n'est pas en fast, donc pas d'autorisation
		// ET
		// - on est dans la bonne direction, donc pas d'autorisation
		// exceptionnelle de se retourner

		if(rebrousse && (directionstrategyactuelle != DirectionStrategy.FASTEST && directionstrategyactuelle.isPossible(c.enMarcheAvant)))
		{
			// log.debug(vitesse+" n'est pas acceptable (rebroussement
			// interdit");
			return false;
		}

		// Si on ne rebrousse pas chemin alors que c'est nécessaire
		if(!rebrousse && !directionstrategyactuelle.isPossible(c.enMarcheAvant))
		{
			// log.debug(vitesse+" n'est pas acceptable (rebroussement
			// nécessaire");
			return false;
		}

		double courbureFuture = c.courbureGeometrique + vitesse * ClothoidesComputer.DISTANCE_ARC_COURBE_M;
		if(!(courbureFuture >= -courbureMax && courbureFuture <= courbureMax))
		{
			// log.debug(vitesse+" n'est acceptable (courbure trop grande");
			return false;
		}

		return true;
	}

	@Override
	public int getNbArrets()
	{
		if(arret || rebrousse)
			return 1;
		return 0;
	}
}
