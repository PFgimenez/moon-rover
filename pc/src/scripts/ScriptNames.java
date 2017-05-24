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

import table.GameElementNames;

/**
 * La liste des scripts
 * 
 * @author pf
 *
 */

public enum ScriptNames
{
	SCRIPT_DEPOSE_MINERAI_GAUCHE(new ScriptDeposeMinerai(true)),
	SCRIPT_DEPOSE_MINERAI_DROITE(new ScriptDeposeMinerai(false)),

	SCRIPT_HOMOLO_GAUCHE(new ScriptHomolo(GameElementNames.MINERAI_CRATERE_HAUT_GAUCHE)),
	SCRIPT_HOMOLO_DROITE(new ScriptHomolo(GameElementNames.MINERAI_CRATERE_HAUT_DROITE)),

	SCRIPT_CRATERE_HAUT_GAUCHE(new ScriptPetitCratere(GameElementNames.MINERAI_CRATERE_HAUT_GAUCHE)),
	SCRIPT_CRATERE_HAUT_DROITE(new ScriptPetitCratere(GameElementNames.MINERAI_CRATERE_HAUT_DROITE)),
	SCRIPT_CRATERE_BAS_GAUCHE(new ScriptPetitCratere(GameElementNames.MINERAI_CRATERE_BAS_GAUCHE)),
	SCRIPT_CRATERE_BAS_DROITE(new ScriptPetitCratere(GameElementNames.MINERAI_CRATERE_BAS_DROITE));

	public final Script s;

	private ScriptNames(Script s)
	{
		this.s = s;
	}

}
