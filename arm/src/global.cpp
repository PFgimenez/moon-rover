#include "global.h"

bool isSymmetry;
bool marcheAvant;
Uart<2> serial_rb;
double x_odo, y_odo; // abscisse et ordonn�e exprim�es en mm
double orientation_odo; // exprim� en radians
double cos_orientation_odo, sin_orientation_odo;
bool asserEnable;
bool debugMode = false;
bool needArrive = false;
SemaphoreHandle_t odo_mutex = xSemaphoreCreateMutex();
SemaphoreHandle_t consigneAsser_mutex = xSemaphoreCreateMutex();
double courbure_odo;
std::vector<Hook*> listeHooks;
Uart<6> serial_ax;
AX<Uart<6>>* ax12;
volatile bool ping = false;
TIM_Encoder_InitTypeDef encoder, encoder2;
TIM_HandleTypeDef timer, timer2, timer3;
volatile bool startOdo = false;
volatile bool matchDemarre = true; // TODO

MODE_ASSER modeAsserActuel = ASSER_OFF;
