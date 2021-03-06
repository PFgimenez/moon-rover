#ifndef _BATTERYSENSOR_h
#define _BATTERYSENSOR_h

#include "pin_mapping.h"
#include "Singleton.h"

#if defined(ARDUINO) && ARDUINO >= 100
	#include "Arduino.h"
#else
	#include "WProgram.h"
#endif

class BatterySensor : public Singleton<BatterySensor>
{
public:
	BatterySensor()
	{
		pinMode(PIN_GET_VOLTAGE, INPUT);
		updatePeriod = 1000; //ms
		lastUpdateTime = 0;
	}

	void update()
	{
		if (millis() - lastUpdateTime > updatePeriod)
		{
			float voltage = (float)analogRead(PIN_GET_VOLTAGE) / 73;
			float level = (voltage - 10.7) * 50;

			// 10,7V <-> 0%
			// 11,1V <-> 20%
			// 12,7V <-> 100%
			if (level < 0)
			{
				level = 0;
			}
			else if (level > 100)
			{
				level = 100;
			}
			currentLevel = (uint8_t)level;

			lastUpdateTime = millis();
		}
	}

	uint8_t getLevel()
	{
		return currentLevel;
	}

private:
	uint32_t lastUpdateTime; // ms
	uint8_t currentLevel; // pourcentage de batterie restant (de 0 � 100%)
	uint32_t updatePeriod; // ms
};


#endif

