#include "MotionControlSystem.h"


MotionControlSystem::MotionControlSystem() :
leftMotorEncoder(PIN_A_LEFT_MOTOR_ENCODER, PIN_B_LEFT_MOTOR_ENCODER),
rightMotorEncoder(PIN_A_RIGHT_MOTOR_ENCODER, PIN_B_RIGHT_MOTOR_ENCODER),
leftFreeEncoder(PIN_A_LEFT_BACK_ENCODER, PIN_B_LEFT_BACK_ENCODER),
rightFreeEncoder(PIN_A_RIGHT_BACK_ENCODER, PIN_B_RIGHT_BACK_ENCODER),
direction(DirectionController::Instance()),
rightSpeedPID(&currentRightSpeed, &rightPWM, &rightSpeedSetpoint),
rightMotorBlockingMgr(rightSpeedSetpoint, currentRightSpeed),
leftSpeedPID(&currentLeftSpeed, &leftPWM, &leftSpeedSetpoint),
leftMotorBlockingMgr(leftSpeedSetpoint, currentLeftSpeed),
translationPID(&currentTranslation, &movingSpeedSetpoint, &translationSetpoint),
endOfMoveMgr(currentMovingSpeed)
{
	currentTranslation = 0;
	currentLeftSpeed = 0;
	currentRightSpeed = 0;

	maxMovingSpeed = 0;

	movingState = STOPPED;
	trajectoryFullyCompleted = true;
	trajectoryIndex = 0;
	updateNextStopPoint();
	updateSideDistanceFactors();

	leftSpeedPID.setOutputLimits(-1023, 1023);
	rightSpeedPID.setOutputLimits(-1023, 1023);

	loadParameters();

	resetPosition();
	stop();

	lastInterruptDuration = 0;
	maxInterruptDuration = 0;
}

void MotionControlSystem::enablePositionControl(bool enabled)
{
	positionControlled = enabled;
}

void MotionControlSystem::enableLeftSpeedControl(bool enable)
{
	leftSpeedControlled = enable;
}

void MotionControlSystem::enableRightSpeedControl(bool enable)
{
	rightSpeedControlled = enable;
}

void MotionControlSystem::enablePwmControl(bool enable)
{
	pwmControlled = enable;
}

void MotionControlSystem::getEnableStates(bool &cp, bool &cvg, bool &cvd, bool &cpwm)
{
	cp = positionControlled;
	cvg = leftSpeedControlled;
	cvd = rightSpeedControlled;
	cpwm = pwmControlled;
}



/*
	Boucle d'asservissement
*/

void MotionControlSystem::control()
{
	static uint32_t beginTimestamp;
	beginTimestamp = micros();

	updateSpeedAndPosition();
	manageStop();
	manageBlocking();
	checkTrajectory();
	updateTrajectoryIndex();

	if (positionControlled)
	{
		/* Gestion du timeout de MOVE_INIT */
		static uint32_t moveInit_startTime = 0;
		static bool moveInit_started = false;
		if (movingState == MOVE_INIT)
		{
			if (!moveInit_started)
			{
				moveInit_started = true;
				moveInit_startTime = millis();
			}
			if (millis() - moveInit_startTime > TIMEOUT_MOVE_INIT)
			{
				movingState = EXT_BLOCKED;
				clearCurrentTrajectory();
				stop();
				Log::critical(34, "MOVE_INIT TIMEOUT");
			}
		}
		else
		{
			moveInit_started = false;
		}

		/* Asservissement */
		if (movingState == MOVE_INIT)
		{
			TrajectoryPoint currentTrajPoint = currentTrajectory[trajectoryIndex];
			direction.setAimCurvature(currentTrajPoint.getCurvature());

			if (ABS(direction.getRealCurvature() - currentTrajPoint.getCurvature()) < CURVATURE_TOLERANCE)
			{
				movingState = MOVING;
				endOfMoveMgr.moveIsStarting();
				derivativeCurvature.update(currentTrajPoint.getCurvature());
			}
			leftSpeedSetpoint = 0;
			rightSpeedSetpoint = 0;
		}
		else if (movingState == MOVING)
		{
			/* Asservissement sur trajectoire */
			static float posError, prevPosError, deltaPosError;
			static float orientationError, prevOrientationError, deltaOrientationError;
			static Average<float, 5> averageDeltaPosError;
			static Average<float, 5> averageDeltaOrientationError;
			static Average<float, 5> averagePosError;
			static Average<float, 5> averageOrientationError;
			
			uint8_t realIndex = trajectoryIndex + desiredIndexOffset;
			while (!currentTrajectory[realIndex].isUpToDate())
			{
				realIndex--;
			}

			Position posConsigne = currentTrajectory[trajectoryIndex].getPosition();
			float anticipatedCurvature = currentTrajectory[realIndex].getCurvature();
			posError = -(position.x - posConsigne.x) * sinf(posConsigne.orientation) + (position.y - posConsigne.y) * cosf(posConsigne.orientation);
			orientationError = fmodulo(position.orientation - posConsigne.orientation, TWO_PI);

			deltaPosError = (posError - prevPosError) * FREQ_ASSERV;
			//averageDeltaPosError.add(deltaPosError);
			//deltaPosError = averageDeltaPosError.value();

			if (orientationError > PI)
			{
				orientationError -= TWO_PI;
			}

			deltaOrientationError = (orientationError - prevOrientationError) * FREQ_ASSERV;
			//averageDeltaOrientationError.add(deltaOrientationError);
			//deltaOrientationError = averageDeltaOrientationError.value();

			if (maxMovingSpeed > 0)
			{
				curvatureOrder = anticipatedCurvature - curvatureCorrectorK1 * posError - curvatureCorrectorK2 * orientationError;
				curvatureOrder -= curvatureCorrectorKD1 * deltaPosError;
				curvatureOrder -= curvatureCorrectorKD2 * deltaOrientationError;
			}
			else
			{
				curvatureOrder = anticipatedCurvature - curvatureCorrectorK1 * posError + curvatureCorrectorK2 * orientationError;
				curvatureOrder -= curvatureCorrectorKD1 * deltaPosError;
				curvatureOrder += curvatureCorrectorKD2 * deltaOrientationError;
			}

			curvatureOrder += derivativeCurvature.getDerivativeCurvature() * curvatureCorrectorKd;

			direction.setAimCurvature(curvatureOrder);

			prevPosError = posError;
			prevOrientationError = orientationError;
			
			watchTrajErrors.traj_curv = anticipatedCurvature;
			watchTrajErrors.current_curv = direction.getRealCurvature();
			watchTrajErrors.aim_curv = curvatureOrder;
			watchTrajErrors.angle_err = 100*orientationError;
			watchTrajErrors.pos_err = posError;
			watchTrajErrors.curv_deriv = 10*derivativeCurvature.getDerivativeCurvature();
			watchTrajErrors.delta_angle_err = deltaOrientationError;
			watchTrajErrors.delta_pos_err = deltaPosError/10;

			watchTrajIndex.index = realIndex;
			watchTrajIndex.currentPos = position;
			watchTrajIndex.aimPosition = posConsigne;

			translationPID.compute(); // MAJ movingSpeedSetpoint

			// Limitation de l'acc�l�ration (et pas de la d�c�l�ration)
			if (movingSpeedSetpoint - previousMovingSpeedSetpoint > maxAcceleration)
			{
				movingSpeedSetpoint = previousMovingSpeedSetpoint + maxAcceleration;
			}
			//else if (movingSpeedSetpoint - previousMovingSpeedSetpoint < -maxAcceleration)
			//{
			//	movingSpeedSetpoint = previousMovingSpeedSetpoint - maxAcceleration;
			//}

			previousMovingSpeedSetpoint = movingSpeedSetpoint;

			// Limitation de la vitesse
			if (movingSpeedSetpoint > ABS(maxMovingSpeed))
			{
				movingSpeedSetpoint = ABS(maxMovingSpeed);
			}

			// Calcul des vitesses gauche et droite en fonction de la vitesse globale
			leftSpeedSetpoint = movingSpeedSetpoint * leftSideDistanceFactor;
			rightSpeedSetpoint = movingSpeedSetpoint * rightSideDistanceFactor;

			// Gestion du sens de d�placement
			if (maxMovingSpeed < 0)
			{
				leftSpeedSetpoint = -leftSpeedSetpoint;
				rightSpeedSetpoint = -rightSpeedSetpoint;
			}
		}
		else
		{
			leftSpeedSetpoint = 0;
			rightSpeedSetpoint = 0;
		}
	}

	if (leftSpeedControlled)
	{
		if (leftSpeedSetpoint == 0)
		{
			leftSpeedPID.resetIntegralError();
		}
		leftSpeedPID.compute();		// Actualise la valeur de 'leftPWM'
	}
	if (rightSpeedControlled)
	{
		if (rightSpeedSetpoint == 0)
		{
			rightSpeedPID.resetIntegralError();
		}
		rightSpeedPID.compute();	// Actualise la valeur de 'rightPWM'
	}

	if (pwmControlled)
	{
		motor.runLeft(leftPWM);
		motor.runRight(rightPWM);
	}

	lastInterruptDuration = micros() - beginTimestamp;
	if (lastInterruptDuration > maxInterruptDuration)
	{
		maxInterruptDuration = lastInterruptDuration;
	}
}

void MotionControlSystem::updateSpeedAndPosition() 
{
	static int32_t
		leftMotorTicks = 0,
		rightMotorTicks = 0,
		leftTicks = 0,
		rightTicks = 0;
	static int32_t
		previousLeftMotorTicks = 0,
		previousRightMotorTicks = 0,
		previousLeftTicks = 0,
		previousRightTicks = 0;
	static int32_t
		deltaLeftMotorTicks = 0,
		deltaRightMotorTicks = 0,
		deltaLeftTicks = 0,
		deltaRightTicks = 0;
	static float
		deltaTranslation_mm = 0,
		half_deltaRotation_rad = 0,
		currentAngle = 0,
		corrector = 1,
		deltaTranslation = 0;

	// R�cup�ration des donn�es des encodeurs
	leftMotorTicks = leftMotorEncoder.read();
	rightMotorTicks = rightMotorEncoder.read();
	leftTicks = leftFreeEncoder.read();
	rightTicks = rightFreeEncoder.read();

	// Calcul du mouvement de chaque roue depuis le dernier asservissement
	deltaLeftMotorTicks = leftMotorTicks - previousLeftMotorTicks;
	deltaRightMotorTicks = rightMotorTicks - previousRightMotorTicks;
	deltaLeftTicks = leftTicks - previousLeftTicks;
	deltaRightTicks = rightTicks - previousRightTicks;

	previousLeftMotorTicks = leftMotorTicks;
	previousRightMotorTicks = rightMotorTicks;
	previousLeftTicks = leftTicks;
	previousRightTicks = rightTicks;

	// Mise � jour de la vitesse des moteurs
	currentLeftSpeed = deltaLeftMotorTicks * FREQ_ASSERV;
	currentRightSpeed = deltaRightMotorTicks * FREQ_ASSERV;
	averageLeftSpeed.add(currentLeftSpeed);
	averageRightSpeed.add(currentRightSpeed);
	currentLeftSpeed = averageLeftSpeed.value();
	currentRightSpeed = averageRightSpeed.value();

	// Mise � jour de la position et de l'orientattion
	deltaTranslation = ((float)deltaLeftTicks + (float)deltaRightTicks) / 2;
	deltaTranslation_mm = deltaTranslation * TICK_TO_MM;
	half_deltaRotation_rad = (((float)deltaRightTicks - (float)deltaLeftTicks) / 4) * TICK_TO_RADIANS;
	currentAngle = position.orientation + half_deltaRotation_rad;
	position.setOrientation(position.orientation + half_deltaRotation_rad * 2);
	corrector = 1 - square(half_deltaRotation_rad) / 6;
	position.x += corrector * deltaTranslation_mm * cosf(currentAngle);
	position.y += corrector * deltaTranslation_mm * sinf(currentAngle);

	// Mise � jour de currentTranslation
	if (maxMovingSpeed >= 0)
	{
		currentTranslation_float += deltaTranslation;
	}
	else
	{
		currentTranslation_float -= deltaTranslation;
	}
	currentTranslation = (int32_t)(currentTranslation_float + 0.5);


	// Mise � jour de la vitesse de translation
	currentMovingSpeed = deltaTranslation * FREQ_ASSERV;
	averageTranslationSpeed.add(currentMovingSpeed);
	currentMovingSpeed = averageTranslationSpeed.value();

	// Mise � jour des erreurs cumulatives des encodeurs des moteurs
	leftMotorError += deltaLeftMotorTicks * FRONT_TICK_TO_TICK - (int32_t)(deltaTranslation * leftSideDistanceFactor);
	rightMotorError += deltaRightMotorTicks * FRONT_TICK_TO_TICK - (int32_t)(deltaTranslation * rightSideDistanceFactor);

	// En cas d'erreur excessive au niveau des moteurs de propulsion, le robot est consid�r� bloqu�.
	if ((ABS(leftMotorError) > MOTOR_SLIP_TOLERANCE || ABS(rightMotorError) > MOTOR_SLIP_TOLERANCE) && false)
	{
		movingState = EXT_BLOCKED;
		clearCurrentTrajectory();
		stop();
		Log::critical(34, "Derapage d'un moteur de propulsion");
	}
}

void MotionControlSystem::updateTrajectoryIndex()
{
	if (movingState == MOVING && !currentTrajectory[trajectoryIndex].isStopPoint())
	{
		uint8_t nextPoint = trajectoryIndex + 1;
		
		Position trajPoint = currentTrajectory[trajectoryIndex].getPosition();
		if 
			(
			currentTrajectory[nextPoint].isUpToDate() &&
			((position.x - trajPoint.x) * cosf(trajPoint.orientation) + (position.y - trajPoint.y) * sinf(trajPoint.orientation)) * (float)maxMovingSpeed > 0
			)
		{
			currentTrajectory[trajectoryIndex].makeObsolete();
			trajectoryIndex = nextPoint;
			updateTranslationSetpoint();
			updateSideDistanceFactors();
			derivativeCurvature.update(currentTrajectory[trajectoryIndex].getCurvature());
		}


		// Ancien algorithme de changement de point
		/*
		bool nextPointIncermented = false;
		while 
			(
			currentTrajectory[nextPoint].isUpToDate() && 
			position.isCloserToAThanB(currentTrajectory[nextPoint].getPosition(), currentTrajectory[trajectoryIndex].getPosition())
			)
		{
			currentTrajectory[(uint8_t)(nextPoint - 1)].makeObsolete();
			nextPoint++;
			nextPointIncermented = true;
		}
		if (nextPointIncermented)
		{
			trajectoryIndex = (uint8_t)(nextPoint - 1);
			updateTranslationSetpoint();
			updateSideDistanceFactors();
		}
		*/
	}
	else if (movingState == STOPPED && currentTrajectory[trajectoryIndex].isStopPoint())
	{
		if (currentTrajectory[(uint8_t)(trajectoryIndex + 1)].isUpToDate() && !trajectoryFullyCompleted)
		{
			currentTrajectory[trajectoryIndex].makeObsolete();
			trajectoryIndex++;
			updateNextStopPoint();
			updateSideDistanceFactors();
			trajectoryFullyCompleted = true;
		}
	}
}

void MotionControlSystem::updateNextStopPoint()
{
	if (currentTrajectory[trajectoryIndex].isUpToDate() && currentTrajectory[trajectoryIndex].isStopPoint())
	{
		nextStopPoint = trajectoryIndex;
	}
	else
	{
		uint16_t infiniteLoopCheck = 0;
		bool found = false;
		nextStopPoint = trajectoryIndex + 1;
		while (currentTrajectory[nextStopPoint].isUpToDate() && infiniteLoopCheck < UINT8_MAX + 1)
		{
			if (currentTrajectory[nextStopPoint].isStopPoint())
			{
				found = true;
				break;
			}
			nextStopPoint++;
			infiniteLoopCheck++;
		}
		if (!found)
		{
			nextStopPoint = UINT16_MAX;
		}
	}
	updateTranslationSetpoint();
}

void MotionControlSystem::checkTrajectory()
{
	if (movingState == MOVE_INIT || movingState == MOVING)
	{
		bool valid = true;
		if (!currentTrajectory[trajectoryIndex].isUpToDate())
		{
			valid = false;
		}
		else if (movingState == MOVING && !currentTrajectory[trajectoryIndex].isStopPoint())
		{
			if (!currentTrajectory[(uint8_t)(trajectoryIndex + 1)].isUpToDate())
			{
				valid = false;
			}
		}
		if (!valid)
		{
			movingState = EMPTY_TRAJ;
			clearCurrentTrajectory();
			stop();
			Log::critical(32, "Empty trajectory");
		}
	}
}

void MotionControlSystem::updateTranslationSetpoint()
{
	if (nextStopPoint == UINT16_MAX)
	{
		translationSetpoint = currentTranslation + UINT8_MAX * TRAJECTORY_STEP;
	}
	else
	{
		uint8_t nbPointsToTravel = nextStopPoint - trajectoryIndex;
		translationSetpoint = currentTranslation + nbPointsToTravel * TRAJECTORY_STEP;
		if (nbPointsToTravel > 10)
		{
			translationSetpoint += TRAJECTORY_STEP / 2;
		}
		else
		{
			Position posConsigne = currentTrajectory[trajectoryIndex].getPosition();
			translationSetpoint += 
				ABS(
					(
						(position.x - posConsigne.x) * cosf(posConsigne.orientation) + 
						(position.y - posConsigne.y) * sinf(posConsigne.orientation)
					) / TICK_TO_MM
				);
		}
	}
}


void MotionControlSystem::updateSideDistanceFactors()
{
	static float squared_length = square(FRONT_BACK_WHEELS_DISTANCE);

	if (curvatureOrder == 0 || !currentTrajectory[trajectoryIndex].isUpToDate())
	{
		leftSideDistanceFactor = 1;
		rightSideDistanceFactor = 1;
	}
	else
	{
		float r = 1000 / curvatureOrder;
		if (r > 0)
		{
			leftSideDistanceFactor = (sqrtf(square(r - DIRECTION_ROTATION_POINT_Y) + squared_length) - DIRECTION_WHEEL_DIST_FROM_ROT_PT) / r;
			rightSideDistanceFactor = (sqrtf(square(r + DIRECTION_ROTATION_POINT_Y) + squared_length) + DIRECTION_WHEEL_DIST_FROM_ROT_PT) / r;
		}
		else
		{
			leftSideDistanceFactor = -(sqrtf(square(r - DIRECTION_ROTATION_POINT_Y) + squared_length) + DIRECTION_WHEEL_DIST_FROM_ROT_PT) / r;
			rightSideDistanceFactor = -(sqrtf(square(r + DIRECTION_ROTATION_POINT_Y) + squared_length) - DIRECTION_WHEEL_DIST_FROM_ROT_PT) / r;
		}
	}
}


void MotionControlSystem::manageStop()
{
	endOfMoveMgr.compute();
	if (endOfMoveMgr.isStopped() && movingState == MOVING)
	{
		if (currentTrajectory[trajectoryIndex].isStopPoint())
		{
			movingState = STOPPED;
		}
		else
		{
			movingState = EXT_BLOCKED;
			clearCurrentTrajectory();
			Log::critical(33, "Erreur d'asservissement en translation");
		}
		stop();
	}
}

void MotionControlSystem::manageBlocking()
{
	leftMotorBlockingMgr.compute();
	rightMotorBlockingMgr.compute();
	if (leftMotorBlockingMgr.isBlocked() || rightMotorBlockingMgr.isBlocked())
	{
		movingState = INT_BLOCKED;
		clearCurrentTrajectory();
		stop();
		Log::critical(31, "Blocage physique d'un moteur");
	}
}

void MotionControlSystem::clearCurrentTrajectory()
{
	trajectoryFullyCompleted = true;
	TrajectoryPoint voidPoint;
	for (uint16_t i = 0; i < UINT8_MAX + 1; i++)
	{
		currentTrajectory[i] = voidPoint;
	}
}



/*
	Gestion des d�placements
*/

void MotionControlSystem::addTrajectoryPoint(const TrajectoryPoint & trajPoint, uint8_t index)
{
	bool updateIsNeeded = false;
	if (trajPoint.isStopPoint() || (currentTrajectory[index].isUpToDate() && currentTrajectory[index].isStopPoint()))
	{
		updateIsNeeded = true;
	}
	currentTrajectory[index] = trajPoint;
	if (updateIsNeeded)
	{
		noInterrupts();
		updateNextStopPoint();
		interrupts();
	}

	Log::data(Log::TRAJECTORY, trajPoint);
}

MotionControlSystem::MovingState MotionControlSystem::getMovingState() const
{
	noInterrupts();
	MovingState movingStateCpy = movingState;
	interrupts();
	return movingStateCpy;
}

void MotionControlSystem::gotoNextStopPoint()
{
	noInterrupts();
	if (movingState == MOVING || movingState == MOVE_INIT)
	{
		Log::warning("Nested call of MotionControlSystem::gotoNextStopPoint()");
	}
	if (!trajectoryFullyCompleted)
	{
		updateTrajectoryIndex();
	}

	movingState = MOVE_INIT;
	trajectoryFullyCompleted = false;
	interrupts();
}

void MotionControlSystem::stop() 
{
	noInterrupts();
	currentTranslation = 0;
	currentTranslation_float = 0;
	translationSetpoint = 0;
	leftSpeedSetpoint = 0;
	rightSpeedSetpoint = 0;
	leftPWM = 0;
	rightPWM = 0;
	movingSpeedSetpoint = 0;
	previousMovingSpeedSetpoint = 0;
	motor.runLeft(0);
	motor.runRight(0);
	translationPID.resetIntegralError();
	translationPID.resetDerivativeError();
	leftSpeedPID.resetIntegralError();
	leftSpeedPID.resetDerivativeError();
	rightSpeedPID.resetIntegralError();
	rightSpeedPID.resetDerivativeError();
	leftMotorError = 0;
	rightMotorError = 0;
	derivativeCurvature.reset();
	interrupts();
}

void MotionControlSystem::highLevelStop()
{
	movingState = STOPPED;
	clearCurrentTrajectory();
	stop();
}

bool MotionControlSystem::isStopped()
{
	return endOfMoveMgr.isStopped();
}

void MotionControlSystem::setMaxMovingSpeed(int32_t maxMovingSpeed_mm_sec)
{
	//desiredIndexOffset = ABS(maxMovingSpeed_mm_sec) / DESIRED_OFFSET_TO_SPEED;
	desiredIndexOffset = 0;
	Serial.print("offset= ");
	Serial.println(desiredIndexOffset);
	int32_t speed_ticks_sec = (maxMovingSpeed_mm_sec / TICK_TO_MM ) / FRONT_TICK_TO_TICK;
	noInterrupts();
	maxMovingSpeed = speed_ticks_sec;
	interrupts();
}

int32_t MotionControlSystem::getMaxMovingSpeed() const
{
	return maxMovingSpeed * TICK_TO_MM * FRONT_TICK_TO_TICK;
}

void MotionControlSystem::setMaxAcceleration(int32_t newMaxAcceleration)
{
	noInterrupts();
	maxAcceleration = newMaxAcceleration;
	interrupts();
}

int32_t MotionControlSystem::getMaxAcceleration() const
{
	return maxAcceleration;
}



/**
* Getters/Setters des constantes d'asservissement en translation/rotation/vitesse
*/

void MotionControlSystem::setCurrentPIDTunings(float kp, float ki, float kd)
{
	switch (pidToSet)
	{
	case MotionControlSystem::LEFT_SPEED:
		setLeftSpeedTunings(kp, ki, kd);
		break;
	case MotionControlSystem::RIGHT_SPEED:
		setRightSpeedTunings(kp, ki, kd);
		break;
	case MotionControlSystem::SPEED:
		setLeftSpeedTunings(kp, ki, kd);
		setRightSpeedTunings(kp, ki, kd);
		break;
	case MotionControlSystem::TRANSLATION:
		setTranslationTunings(kp, ki, kd);
		break;
	default:
		break;
	}
}
void MotionControlSystem::getCurrentPIDTunings(float &kp, float &ki, float &kd) const
{
	switch (pidToSet)
	{
	case MotionControlSystem::LEFT_SPEED:
		getLeftSpeedTunings(kp, ki, kd);
		break;
	case MotionControlSystem::RIGHT_SPEED:
		getRightSpeedTunings(kp, ki, kd);
		break;
	case MotionControlSystem::SPEED:
		float kpl, kil, kdl, kpr, kir, kdr;
		getLeftSpeedTunings(kpl, kil, kdl);
		getRightSpeedTunings(kpr, kir, kdr);
		if ((kpl != kpr || kil != kir) || kdl != kdr)
		{
			Log::warning("Left/Right speed PID tunings are different, left tunings are returned");
		}
		kp = kpl;
		ki = kil;
		kd = kdl;
		break;
	case MotionControlSystem::TRANSLATION:
		getTranslationTunings(kp, ki, kd);
		break;
	default:
		break;
	}
}
void MotionControlSystem::getTranslationTunings(float &kp, float &ki, float &kd) const {
	kp = translationPID.getKp();
	ki = translationPID.getKi();
	kd = translationPID.getKd();
}
void MotionControlSystem::getLeftSpeedTunings(float &kp, float &ki, float &kd) const {
	kp = leftSpeedPID.getKp();
	ki = leftSpeedPID.getKi();
	kd = leftSpeedPID.getKd();
}
void MotionControlSystem::getRightSpeedTunings(float &kp, float &ki, float &kd) const {
	kp = rightSpeedPID.getKp();
	ki = rightSpeedPID.getKi();
	kd = rightSpeedPID.getKd();
}
void MotionControlSystem::getTrajectoryTunings(float &k1, float &kd1, float &k2, float &kd2) const {
	k1 = curvatureCorrectorK1;
	k2 = curvatureCorrectorK2;
	kd1 = curvatureCorrectorKD1;
	kd2 = curvatureCorrectorKD2;
}
void MotionControlSystem::setTranslationTunings(float kp, float ki, float kd) {
	translationPID.setTunings(kp, ki, kd);
}
void MotionControlSystem::setLeftSpeedTunings(float kp, float ki, float kd) {
	leftSpeedPID.setTunings(kp, ki, kd);
}
void MotionControlSystem::setRightSpeedTunings(float kp, float ki, float kd) {
	rightSpeedPID.setTunings(kp, ki, kd);
}
void MotionControlSystem::setTrajectoryTunings(float k1, float kd1, float k2, float kd2) {
	curvatureCorrectorK1 = k1;
	curvatureCorrectorK2 = k2;
	curvatureCorrectorKD1 = kd1;
	curvatureCorrectorKD2 = kd2;
}
void MotionControlSystem::setPIDtoSet(PIDtoSet newPIDtoSet)
{
	pidToSet = newPIDtoSet;
}
MotionControlSystem::PIDtoSet MotionControlSystem::getPIDtoSet() const
{
	return pidToSet;
}
void MotionControlSystem::getPIDtoSet_str(char * str, size_t size) const
{
	char leftSpeedStr[] = "LEFT_SPEED";
	char rightSpeedStr[] = "RIGHT_SPEED";
	char speedStr[] = "SPEED";
	char translationStr[] = "TRANSLATION";

	if (size == 0)
	{
		return;
	}
	else if (size < 12)
	{
		str[0] = '\0';
	}
	else
	{
		switch (pidToSet)
		{
		case MotionControlSystem::LEFT_SPEED:
			strcpy(str, leftSpeedStr);
			break;
		case MotionControlSystem::RIGHT_SPEED:
			strcpy(str, rightSpeedStr);
			break;
		case MotionControlSystem::SPEED:
			strcpy(str, speedStr);
			break;
		case MotionControlSystem::TRANSLATION:
			strcpy(str, translationStr);
			break;
		default:
			str[0] = '\0';
			break;
		}
	}
}

void MotionControlSystem::setCurvatureCorrectorKd(float kd)
{
	curvatureCorrectorKd = kd;
}

float MotionControlSystem::getCurvatureCorrectorKd()
{
	return curvatureCorrectorKd;
}


/*
* Getters/Setters des variables de position haut niveau
*/
void MotionControlSystem::setPosition(const Position & newPosition)
{
	noInterrupts();
	position = newPosition;
	interrupts();
}

void MotionControlSystem::getPosition(Position & returnPos) const
{
	noInterrupts();
	returnPos = position;
	interrupts();
}

uint8_t MotionControlSystem::getTrajectoryIndex() const
{
	noInterrupts();
	uint8_t indexCpy = trajectoryIndex;
	interrupts();
	return indexCpy;
}

void MotionControlSystem::resetPosition()
{
	noInterrupts();
	position.x = 0;
	position.y = 0;
	position.orientation = 0;
	interrupts();
	stop();
}


/*
*	R�glage des blockingMgr et stoppingMgr
*/

void MotionControlSystem::setLeftMotorBmgrTunings(float sensibility, uint32_t responseTime)
{
	noInterrupts();
	leftMotorBlockingMgr.setTunings(sensibility, responseTime);
	interrupts();
}

void MotionControlSystem::setRightMotorBmgrTunings(float sensibility, uint32_t responseTime)
{
	noInterrupts();
	rightMotorBlockingMgr.setTunings(sensibility, responseTime);
	interrupts();
}

void MotionControlSystem::setEndOfMoveMgrTunings(uint32_t epsilon, uint32_t responseTime)
{
	noInterrupts();
	endOfMoveMgr.setTunings(epsilon, responseTime);
	interrupts();
}

void MotionControlSystem::getLeftMotorBmgrTunings(float & sensibility, uint32_t & responseTime) const
{
	leftMotorBlockingMgr.getTunings(sensibility, responseTime);
}

void MotionControlSystem::getRightMotorBmgrTunings(float & sensibility, uint32_t & responseTime) const
{
	rightMotorBlockingMgr.getTunings(sensibility, responseTime);
}

void MotionControlSystem::getEndOfMoveMgrTunings(uint32_t & epsilon, uint32_t & responseTime) const
{
	endOfMoveMgr.getTunings(epsilon, responseTime);
}


/*
*	Getters/Setters de d�bug
*/

void MotionControlSystem::getTicks(int32_t & leftFront, int32_t & rightFront, int32_t & leftBack, int32_t & rightBack)
{
	leftFront = leftMotorEncoder.read();
	rightFront = rightMotorEncoder.read();
	leftBack = leftFreeEncoder.read();
	rightBack = rightFreeEncoder.read();
}

void MotionControlSystem::saveParameters()
{
	int a = 0; // Adresse m�moire dans l'EEPROM
	float kp, ki, kd, s;
	uint32_t e, t;

	EEPROM.put(a, positionControlled);
	a += sizeof(positionControlled);
	EEPROM.put(a, leftSpeedControlled);
	a += sizeof(leftSpeedControlled);
	EEPROM.put(a, rightSpeedControlled);
	a += sizeof(rightSpeedControlled);
	EEPROM.put(a, pwmControlled);
	a += sizeof(pwmControlled);

	leftMotorBlockingMgr.getTunings(s, t);
	EEPROM.put(a, s);
	a += sizeof(s);
	EEPROM.put(a, t);
	a += sizeof(t);

	rightMotorBlockingMgr.getTunings(s, t);
	EEPROM.put(a, s);
	a += sizeof(s);
	EEPROM.put(a, t);
	a += sizeof(t);

	endOfMoveMgr.getTunings(e, t);
	EEPROM.put(a, e);
	a += sizeof(e);
	EEPROM.put(a, t);
	a += sizeof(t);

	EEPROM.put(a, maxAcceleration);
	a += sizeof(maxAcceleration);

	kp = translationPID.getKp();
	EEPROM.put(a, kp);
	a += sizeof(kp);
	ki = translationPID.getKi();
	EEPROM.put(a, ki);
	a += sizeof(ki);
	kd = translationPID.getKd();
	EEPROM.put(a, kd);
	a += sizeof(kd);

	kp = leftSpeedPID.getKp();
	EEPROM.put(a, kp);
	a += sizeof(kp);
	ki = leftSpeedPID.getKi();
	EEPROM.put(a, ki);
	a += sizeof(ki);
	kd = leftSpeedPID.getKd();
	EEPROM.put(a, kd);
	a += sizeof(kd);

	kp = rightSpeedPID.getKp();
	EEPROM.put(a, kp);
	a += sizeof(kp);
	ki = rightSpeedPID.getKi();
	EEPROM.put(a, ki);
	a += sizeof(ki);
	kd = rightSpeedPID.getKd();
	EEPROM.put(a, kd);
	a += sizeof(kd);

	EEPROM.put(a, curvatureCorrectorK1);
	a += sizeof(curvatureCorrectorK1);
	EEPROM.put(a, curvatureCorrectorK2);
	a += sizeof(curvatureCorrectorK2);

	EEPROM.put(a, pidToSet);
	a += sizeof(pidToSet);

	EEPROM.put(a, curvatureCorrectorKd);
	a += sizeof(curvatureCorrectorKd);

	EEPROM.put(a, curvatureCorrectorKD1);
	a += sizeof(curvatureCorrectorKD1);

	EEPROM.put(a, curvatureCorrectorKD2);
	a += sizeof(curvatureCorrectorKD2);
}

void MotionControlSystem::loadParameters()
{
	noInterrupts();

	int a = 0; // Adresse m�moire dans l'EEPROM
	float kp, ki, kd, s;
	uint32_t e, t;

	EEPROM.get(a, positionControlled);
	a += sizeof(positionControlled);
	EEPROM.get(a, leftSpeedControlled);
	a += sizeof(leftSpeedControlled);
	EEPROM.get(a, rightSpeedControlled);
	a += sizeof(rightSpeedControlled);
	EEPROM.get(a, pwmControlled);
	a += sizeof(pwmControlled);

	EEPROM.get(a, s);
	a += sizeof(s);
	EEPROM.get(a, t);
	a += sizeof(t);
	leftMotorBlockingMgr.setTunings(s, t);

	EEPROM.get(a, s);
	a += sizeof(s);
	EEPROM.get(a, t);
	a += sizeof(t);
	rightMotorBlockingMgr.setTunings(s, t);

	EEPROM.get(a, e);
	a += sizeof(e);
	EEPROM.get(a, t);
	a += sizeof(t);
	endOfMoveMgr.setTunings(e, t);

	EEPROM.get(a, maxAcceleration);
	a += sizeof(maxAcceleration);

	EEPROM.get(a, kp);
	a += sizeof(kp);
	EEPROM.get(a, ki);
	a += sizeof(ki);
	EEPROM.get(a, kd);
	a += sizeof(kd);
	translationPID.setTunings(kp, ki, kd);

	EEPROM.get(a, kp);
	a += sizeof(kp);
	EEPROM.get(a, ki);
	a += sizeof(ki);
	EEPROM.get(a, kd);
	a += sizeof(kd);
	leftSpeedPID.setTunings(kp, ki, kd);

	EEPROM.get(a, kp);
	a += sizeof(kp);
	EEPROM.get(a, ki);
	a += sizeof(ki);
	EEPROM.get(a, kd);
	a += sizeof(kd);
	rightSpeedPID.setTunings(kp, ki, kd);

	EEPROM.get(a, curvatureCorrectorK1);
	a += sizeof(curvatureCorrectorK1);
	EEPROM.get(a, curvatureCorrectorK2);
	a += sizeof(curvatureCorrectorK2);

	EEPROM.get(a, pidToSet);
	a += sizeof(pidToSet);

	EEPROM.get(a, curvatureCorrectorKd);
	a += sizeof(curvatureCorrectorKd);

	EEPROM.get(a, curvatureCorrectorKD1);
	a += sizeof(curvatureCorrectorKD1);

	EEPROM.get(a, curvatureCorrectorKD2);
	a += sizeof(curvatureCorrectorKD2);

	interrupts();
}

void MotionControlSystem::loadDefaultParameters()
{
	noInterrupts();

	positionControlled = true;
	leftSpeedControlled = true;
	rightSpeedControlled = true;
	pwmControlled = true;

	leftMotorBlockingMgr.setTunings(0, 0);
	rightMotorBlockingMgr.setTunings(0, 0);
	endOfMoveMgr.setTunings(100, 100);

	maxAcceleration = 25;

	translationPID.setTunings(2.75, 0, 1);
	leftSpeedPID.setTunings(0.6, 0.01, 20);
	rightSpeedPID.setTunings(0.6, 0.01, 20);
	curvatureCorrectorK1 = 0.1;
	curvatureCorrectorK2 = 12;
	curvatureCorrectorKd = 0;

	pidToSet = SPEED;

	interrupts();
}

void MotionControlSystem::logAllData()
{
	static Position nonVolatilePos;
	static uint32_t lastLogTime = 0;
	if (micros() - lastLogTime > 10000)
	{
		if (micros() - lastLogTime > 13000)
		{
			//Serial.printf("LATENCE (%d)\n", micros() - lastLogTime);
		}
		lastLogTime = micros();
		noInterrupts();
		nonVolatilePos = position;
		Log::data(Log::POSITION, nonVolatilePos);
		Log::data(Log::PID_V_G, leftSpeedPID);
		Log::data(Log::PID_V_D, rightSpeedPID);
		Log::data(Log::PID_TRANS, translationPID);
		Log::data(Log::BLOCKING_M_G, leftMotorBlockingMgr);
		Log::data(Log::BLOCKING_M_D, rightMotorBlockingMgr);
		Log::data(Log::STOPPING_MGR, endOfMoveMgr);
		Log::data(Log::TRAJ_ERR, watchTrajErrors);
		interrupts();
	}
}

uint32_t MotionControlSystem::getLastInterruptDuration()
{
	noInterrupts();
	uint32_t cpy = lastInterruptDuration;
	interrupts();
	return cpy;
}

uint32_t MotionControlSystem::getMaxInterruptDuration()
{
	noInterrupts();
	uint32_t cpy = maxInterruptDuration;
	interrupts();
	return cpy;
}

void MotionControlSystem::setPWM(int32_t pwm)
{
	leftPWM = pwm;
	rightPWM = pwm;
}

void MotionControlSystem::setSpeed(int32_t speed)
{
	leftSpeedSetpoint = speed;
	rightSpeedSetpoint = speed;
}

void MotionControlSystem::setTranslation(int32_t distance)
{
	translationSetpoint += distance;
}

