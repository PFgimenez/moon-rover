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

package threads;

import obstacles.Capteurs;
import obstacles.SensorsData;
import obstacles.SensorsDataBuffer;
import utils.Config;
import utils.Log;

/**
 * Thread qui gère les entrées des capteurs
 * @author pf
 *
 */

public class ThreadCapteurs extends ThreadService
{
	private SensorsDataBuffer buffer;
	private Capteurs capteurs;
	
	protected Log log;
	
	public ThreadCapteurs(Log log, SensorsDataBuffer buffer, Capteurs capteurs)
	{
		this.log = log;
		this.buffer = buffer;
		this.capteurs = capteurs;
	}
	
	@Override
	public void run()
	{
		Thread.currentThread().setName("ThreadRobotCapteurs");
		log.debug("Démarrage de "+Thread.currentThread().getName());
		try {
			while(true)
			{
				SensorsData e = null;
				synchronized(buffer)
				{
					if(buffer.isEmpty())
						buffer.wait();
					e = buffer.poll();
				}
				capteurs.updateObstaclesMobiles(e);
				
			}
		} catch (InterruptedException e2) {
			log.debug("Arrêt de "+Thread.currentThread().getName());
		}
	}
	
	@Override
	public void updateConfig(Config config)
	{}

	@Override
	public void useConfig(Config config)
	{}

}