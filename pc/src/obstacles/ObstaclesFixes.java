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

package obstacles;

import obstacles.types.Obstacle;
import obstacles.types.ObstacleCircular;
import obstacles.types.ObstacleRectangular;
import utils.Vec2RO;

/**
 * Enumération des obstacles fixes.
 * Afin que les obstacles fixes soient facilement modifiables d'une coupe à l'autre.
 * @author pf
 *
 */

public enum ObstaclesFixes {

    TEST(new ObstacleRectangular(new Vec2RO(0,1000),50,50), true),

	
	// bords
    BORD_BAS(new ObstacleRectangular(new Vec2RO(0,0),3000,5), false),
    BORD_GAUCHE(new ObstacleRectangular(new Vec2RO(-1500,1000),5,2000), false),
    BORD_DROITE(new ObstacleRectangular(new Vec2RO(1500,1000),5,2000), false),
    BORD_HAUT(new ObstacleRectangular(new Vec2RO(0,2000),3000,5), false);

    private final Obstacle obstacle;
    private boolean visible;
    public static final ObstaclesFixes[] obstaclesFixesVisibles;
    
    static
    {
    	int nbVisibles = 0;
    	for(ObstaclesFixes o : values())
    		if(o.visible)
    			nbVisibles++;
    	obstaclesFixesVisibles = new ObstaclesFixes[nbVisibles];
    	int i = 0;
    	for(ObstaclesFixes o : values())
    		if(o.visible)
    			obstaclesFixesVisibles[i++] = o;
    }

    private ObstaclesFixes(ObstacleRectangular obstacle, boolean visible)
    {
    	this.obstacle = obstacle;
    	this.visible = visible;
    }

    private ObstaclesFixes(ObstacleCircular obstacle, boolean visible)
    {
    	this.obstacle = obstacle;
    	this.visible = visible;
    }

    public Obstacle getObstacle()
    {
    	return obstacle;
    }

}
