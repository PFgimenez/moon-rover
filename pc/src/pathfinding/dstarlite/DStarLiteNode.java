/*
Copyright (C) 2016 Pierre-François Gimenez

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

package pathfinding.dstarlite;

import pathfinding.dstarlite.gridspace.PointGridSpace;

/**
 * Un nœud du D* Lite.
 * @author pf
 *
 */

class DStarLiteNode {

	public final PointGridSpace gridpoint;
	public final Cle cle = new Cle();
	public int g = Integer.MAX_VALUE, rhs = Integer.MAX_VALUE;
	
	/**
	 * "done" correspond à l'appartenance à U dans l'algo du DStarLite
	 */
	public boolean inOpenSet = false;
	public long nbPF = 0;
	
	public DStarLiteNode(PointGridSpace gridpoint)
	{
		this.gridpoint = gridpoint;
	}
	
	@Override
	public final int hashCode()
	{
		return gridpoint.hashcode;
	}
	
	@Override
	public final boolean equals(Object o)
	{
		return gridpoint.hashcode == o.hashCode();
	}
	
	@Override
	public String toString()
	{
		int x = gridpoint.x;
		int y = gridpoint.y;
		return x+" "+y+" ("+cle+")";
	}

	/**
	 * Initialisation du nœud s'il n'a pas encore été utilisé pour ce pathfinding
	 * @param nbPF
	 */
	public void update(long nbPF)
	{
		if(this.nbPF != nbPF)
		{
			g = Integer.MAX_VALUE;
			rhs = Integer.MAX_VALUE;
			inOpenSet = false;
			this.nbPF = nbPF;
		}
	}
	
}