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

package config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;
import container.Service;
import robot.RobotColor;
import utils.Log;

/**
 * S'occupe de fournir un ensemble de valeurs aux autres classes qui
 * l'utilisent.
 * Ces valeurs sont la configuration du robot. Elles sont stockées dans deux
 * endroits :
 * - la configuration initiale, contenue dans ConfigInfo
 * - le fichier config.ini, qui surcharge la configuration initiale
 * L'intérêt du fichier config.ini est de pouvoir modifier facilement la
 * configuration sans recompiler.
 * 
 * @author pf
 *
 */
public class Config implements Service
{
	private String name_config_file = "config.ini";
	private volatile Properties properties = new Properties();
	private Log log;
	private boolean graphicEnable;
	private boolean surcharged = false;
	private boolean configIniCharge = false;

	public Config()
	{
		try
		{
			FileInputStream f = new FileInputStream(name_config_file);
			properties.load(f);
			f.close();
			configIniCharge = true;
		}
		catch(IOException e)
		{
			System.out.println("Chargement de la configuration depuis  : " + System.getProperty("user.dir"));
			System.out.println(e);
			System.out.println("Utilisation des valeurs par défaut.");
		}
		completeConfig();
		graphicEnable = Boolean.parseBoolean(getString(ConfigInfo.GRAPHIC_ENABLE));
	}

	/**
	 * A appeler après l'ouverture du log.
	 */
	public void init(Log log)
	{
		this.log = log;
		if(getBoolean(ConfigInfo.AFFICHE_CONFIG))
			afficheTout();
		else
			afficheChanged();
	}

	/**
	 * Récupère un entier de la config
	 * 
	 * @param nom
	 * @return
	 * @throws NumberFormatException
	 */
	public Integer getInt(ConfigInfo nom) throws NumberFormatException
	{
		String s = getString(nom);
		if(s != null)
			return Integer.parseInt(s);
		return null;
	}

	/**
	 * Récupère un entier de la config
	 * 
	 * @param nom
	 * @return
	 * @throws NumberFormatException
	 */
	public Short getShort(ConfigInfo nom) throws NumberFormatException
	{
		String s = getString(nom);
		if(s != null)
			return Short.parseShort(s);
		return null;
	}

	/**
	 * Récupère un entier de la config
	 * 
	 * @param nom
	 * @return
	 * @throws NumberFormatException
	 */
	public Byte getByte(ConfigInfo nom) throws NumberFormatException
	{
		String s = getString(nom);
		if(s != null)
			return Byte.parseByte(s);
		return null;
	}

	/**
	 * Récupère un entier long de la config
	 * 
	 * @param nom
	 * @return
	 * @throws NumberFormatException
	 */
	public Long getLong(ConfigInfo nom) throws NumberFormatException
	{
		String s = getString(nom);
		if(s != null)
			return Long.parseLong(s);
		return null;
	}

	/**
	 * Récupère un booléen de la config
	 * 
	 * @param nom
	 * @return
	 */
	public Boolean getBoolean(ConfigInfo nom)
	{
		String s = getString(nom);
		if(s == null)
			return null;
		
		// le booléen GRAPHIC_ENABLE peut désactiver tout ce qui est graphique
		if(nom.name().startsWith("GRAPHIC_"))
			return graphicEnable && Boolean.parseBoolean(s);
		return Boolean.parseBoolean(s);
	}

	/**
	 * Récupère un double de la config
	 * 
	 * @param nom
	 * @return
	 * @throws NumberFormatException
	 */
	public Double getDouble(ConfigInfo nom) throws NumberFormatException
	{
		String s = getString(nom);
		if(s != null)
			return Double.parseDouble(s);
		return null;
	}

	/**
	 * Méthode de récupération des paramètres de configuration
	 * 
	 * @param nom
	 * @return
	 */
	public synchronized String getString(ConfigInfo nom)
	{
		if(nom.uptodate)
			return properties.getProperty(nom.toString());
		return null;
	}

	/**
	 * Méthode set privée
	 * 
	 * @param nom
	 * @return
	 */
	private synchronized void set(ConfigInfo nom, String value)
	{
		// On notifie avant d'affecter la valeur.
		// En fait, ça n'a pas d'importance car le mutex sur la config est
		// activé…
		nom.uptodate = true;
		if(value.compareTo(properties.getProperty(nom.toString())) != 0)
			notify();
		log.debug(nom + " = " + value + " (ancienne valeur: " + properties.getProperty(nom.toString()) + ")");
		properties.setProperty(nom.toString(), value);
	}

	/**
	 * Méthode set publique avec protection
	 * 
	 * @param nom
	 * @param value
	 */
	public void set(ConfigInfo nom, Object value)
	{
		if(nom.constant)
			log.critical("Demande d'affectation à une config constante: " + nom);
		else
			set(nom, value.toString());
	}

	/**
	 * Affiche toute la config.
	 * Appelé au début du match.
	 */
	private synchronized void afficheTout()
	{
		log.debug("Configuration initiale");
		for(ConfigInfo info : ConfigInfo.values())
			if(info.constant)
				log.debug(info + " = " + getString(info) + (info.overridden ? " (overridden)" : ""));
	}

	private synchronized void afficheChanged()
	{
		if(surcharged)
		{
			log.debug("Delta config :");
			for(ConfigInfo info : ConfigInfo.values())
				if(info.constant && (info.overridden || !info.getDefaultValue().toString().equals(getString(info))))
					log.debug(info + " = " + getString(info) + (info.overridden ? " (overridden)" : ""));
		}
		else
			log.debug("Pas de surcharge de configuration");
	}

	/**
	 * Complète avec les valeurs par défaut le fichier de configuration
	 */
	private synchronized void completeConfig()
	{
		if(configIniCharge)
		{
			for(ConfigInfo info : ConfigInfo.values())
			{
				if(!properties.containsKey(info.toString()))
					properties.setProperty(info.toString(), info.getDefaultValue().toString());
				else if(!info.constant)
				{
					System.out.println(info + " NE peut PAS être surchargé par config.ini");
					properties.setProperty(info.toString(), info.getDefaultValue().toString());
				}
				else if(info.overridden)
				{
					surcharged = true;
					properties.setProperty(info.toString(), info.getDefaultValue().toString());
				}
				else if(!info.getDefaultValue().equals(properties.getProperty(info.name())))
				{
					// System.out.println(info+" surchargé par config.ini
					// ("+info.getDefaultValue()+" ->
					// "+properties.getProperty(info.name())+")");
					surcharged = true;
				}
			}
			for(String cle : properties.stringPropertyNames())
			{
				if(cle.contains("#"))
				{
					properties.remove(cle);
					continue;
				}
				boolean found = false;
				for(ConfigInfo info : ConfigInfo.values())
					if(info.toString().compareTo(cle) == 0)
					{
						found = true;
						break;
					}
				if(!found)
					System.err.println("Config " + cle + " inconnue !");
			}
		}
		else
		{
			for(ConfigInfo info : ConfigInfo.values())
				properties.setProperty(info.toString(), info.getDefaultValue().toString());
		}
	}

	/**
	 * Récupère la symétrie
	 * 
	 * @return
	 */
	public Boolean getSymmetry()
	{
		String s = getString(ConfigInfo.COULEUR);
		if(s != null)
			return RobotColor.parse(s).symmetry;
		return null;
	}

}
