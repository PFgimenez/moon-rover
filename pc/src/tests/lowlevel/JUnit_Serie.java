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

package tests.lowlevel;

import org.junit.Before;
import org.junit.Test;

import serie.BufferOutgoingOrder;
import serie.Ticket;
import tests.JUnit_Test;

/**
 * Tests unitaires de la série.
 * @author pf
 *
 */

public class JUnit_Serie extends JUnit_Test {

	private BufferOutgoingOrder data;
	@Override
	@Before
    public void setUp() throws Exception {
        super.setUp();
        data = container.getService(BufferOutgoingOrder.class);
	}
	
	/**
	 * Un test pour vérifie la connexion
	 * Le programme s'arrête automatiquement au bout de 3s
	 * @throws Exception
	 */
	@Test
	public void test_ping() throws Exception
	{
		Thread.sleep(3000);
	}

	/**
	 * Un test d'ordre long
	 * @throws Exception
	 */
	@Test
	public void test_stream() throws Exception
	{
		data.startStream();
		Thread.sleep(10000);
	}

	/**
	 * Un test d'ordre court.
	 * On redemande la couleur jusqu'à avoir un autre code que "couleur inconnue" 
	 * @throws Exception
	 */
	@Test
	public void test_ask_color() throws Exception
	{
		Ticket.State etat;
		do {
			Ticket t = data.demandeCouleur();
			synchronized(t)
			{
				if(t.isEmpty())
					t.wait();
			}
			etat = t.getAndClear();
		} while(etat != Ticket.State.OK);
	}
	
}