/**
 *  Copyright 2022-2024 bthrock
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

@SuppressWarnings('unused')
public static String version() { return "1.3.6" }

metadata {
    definition(name: "Replica Temp-Humidity-Pressure Sensor", namespace: "replica", author: "bthrock", importUrl: "https://raw.githubusercontent.com/TheMegamind/Replica-Drivers/main/replicaTempHumidityPressureSensor.groovy") {
        capability "Battery"
        capability "Configuration"
        capability "Relative Humidity Measurement"
        capability "Sensor"
        capability "Temperature Measurement"
        capability "Refresh"

        attribute "pressure", "number"
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {
        input(name: "inputTempUnit", type: "enum", title: "SmartThings Device Temperature Unit", options: ["fahrenheit": "Fahrenheit", "celsius": "Celsius"], defaultValue: "fahrenheit", required: false)
        input(name: "outputTempUnit", type: "enum", title: "Display Unit - Temperature", options: ["fahrenheit": "Fahrenheit", "celsius": "Celsius"], defaultValue: "fahrenheit", required: false)
        input("tempOffset", "number", title: "Temperature Offset/Adjustment -10.0 to +10.0 in Degrees?", range: "-10..10", description: "Use this offset to adjust for any inaccuracies in your sensor.", defaultValue: 0, required: false)
        input(name: "inputPressureUnit", type: "enum", title: "SmartThings Device Pressure Unit", options: ["bar": "bar", "cmHg": "cmHg", "inHg": "inHg", "kPa": "kPa", "mbar": "mbar", "mmHg": "mmHg", "Pa": "Pa"], defaultValue: "inHg", required: false)
        input(name: "outputPressureUnit", type: "enum", title: "Display Unit - Pressure", options: ["bar": "bar", "cmHg": "cmHg", "inHg": "inHg", "kPa": "kPa", "mbar": "mbar", "mmHg": "mmHg", "Pa": "Pa"], defaultValue: "inHg", required: false)
        input(name: "deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
    }
}

def installed() {
    initialize()
}

def updated() {
    initialize()
}

def initialize() {
    updateDataValue("triggers", groovy.json.JsonOutput.toJson(getReplicaTriggers()))
    updateDataValue("commands", groovy.json.JsonOutput.toJson(getReplicaCommands()))
    
    unschedule()
    if (settings?.deviceRefreshPollInterval && settings.deviceRefreshPollInterval != "manual") {
        "${settings.deviceRefreshPollInterval}"(refresh)
    }
}

def configure() {
    logInfo "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature.
static Map getReplicaCommands() {
    return [
        "setPressureValue": [[name: "pressure*", type: "STRING"]],
        "setBatteryValue": [[name: "battery*", type: "NUMBER"]],
        "setHumidityValue": [[name: "humidity*", type: "NUMBER"]],
        "setTemperatureValue": [[name: "temperature*", type: "NUMBER"]],
        "setHealthStatusValue": [[name: "healthStatus*", type: "ENUM"]]
    ]
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setHumidityValue(value) {
    String unit = "%rh"
    String descriptionText = "${device.displayName} humidity is $value $unit"
    sendEvent(name: "humidity", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setTemperatureValue(value) {
    // Define conversion functions
    def fahrenheitToCelsius = { f -> (f - 32) * 5 / 9 }
    def celsiusToFahrenheit = { c -> (c * 9 / 5) + 32 }

    // Parse the input value based on inputTempUnit
    double temp
    switch (inputTempUnit) {
        case "fahrenheit":
            temp = value.toDouble()
            break
        case "celsius":
            temp = celsiusToFahrenheit(value.toDouble())
            break
        default:
            throw new IllegalArgumentException("Unsupported input scale: $inputTempUnit")
    }

    // Convert temp to the desired output scale (outputTempUnit)
    double convertedTemp
    String unit
    switch (outputTempUnit) {
        case "fahrenheit":
            convertedTemp = temp.round(1)  // Round to 1 decimal point
            unit = "°F"
            break
        case "celsius":
            convertedTemp = fahrenheitToCelsius(temp).round(1)  // Round to 1 decimal point
            unit = "°C"
            break
        default:
            throw new IllegalArgumentException("Unsupported output scale: $outputTempUnit")
    }

    // Apply temperature offset to the converted temperature
    double offset = tempOffset ? tempOffset.toDouble() : 0
    convertedTemp += offset

    // Round the final temperature output to 1 decimal point
    convertedTemp = convertedTemp.round(1)

    // Prepare description text
    String descriptionText = "${device.displayName} temperature is $convertedTemp $unit"
    
    // Send Event & Log Information
    sendEvent(name: "temperature", value: convertedTemp, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setPressureValue(BigDecimal value) {
    // Conversion factors from kPa
    def kPaToInHg = 0.2953
    def kPaToMbar = 10.0
    def kPaToBar = 0.01
    def kPaToCmHg = 0.750062
    def kPaToMmHg = 7.50062
    def kPaToPa = 1000.0

    // Define conversion factors to kPa
    def inHgToKPa = 1 / kPaToInHg
    def mbarToKPa = 1 / kPaToMbar
    def barToKPa = 1 / kPaToBar
    def cmHgToKPa = 1 / kPaToCmHg
    def mmHgToKPa = 1 / kPaToMmHg
    def paToKPa = 1 / kPaToPa

    // Ensure input value is in kPa
    BigDecimal kPa = value

    // Convert kPa to the desired output scale (outputPressureUnit)
    BigDecimal pressure
    String unit
    switch (outputPressureUnit) {
        case "inHg":
            pressure = kPa.multiply(BigDecimal.valueOf(kPaToInHg)).setScale(2, BigDecimal.ROUND_HALF_UP)
            unit = "inHg"
            break
        case "mbar":
            pressure = kPa.multiply(BigDecimal.valueOf(kPaToMbar)).setScale(2, BigDecimal.ROUND_HALF_UP)
            unit = "mbar"
            break
        case "bar":
            pressure = kPa.multiply(BigDecimal.valueOf(kPaToBar)).setScale(2, BigDecimal.ROUND_HALF_UP)
            unit = "bar"
            break
        case "cmHg":
            pressure = kPa.multiply(BigDecimal.valueOf(kPaToCmHg)).setScale(2, BigDecimal.ROUND_HALF_UP)
            unit = "cmHg"
            break
        case "mmHg":
            pressure = kPa.multiply(BigDecimal.valueOf(kPaToMmHg)).setScale(2, BigDecimal.ROUND_HALF_UP)
            unit = "mmHg"
            break
        case "Pa":
            pressure = kPa.multiply(BigDecimal.valueOf(kPaToPa)).setScale(2, BigDecimal.ROUND_HALF_UP)
            unit = "Pa"
            break
        case "kPa":
            pressure = kPa.setScale(2, BigDecimal.ROUND_HALF_UP)
            unit = "kPa"
            break
        default:
            throw new IllegalArgumentException("Unsupported output unit: $outputPressureUnit")
    }

    // Prepare description text
    String descriptionText = "${device.displayName} atmospheric pressure is $pressure $unit"
    
    // Send Event & Log Information
    sendEvent(name: "pressure", value: pressure.toString(), unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return [
        "refresh": []
    ]
}

private def sendCommand(String name, def value = null, String unit = null, data = [:]) {
    parent?.deviceTriggerHandler(device, [name: name, value: value, unit: unit, data: data, now: now])
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"command":{"label":"command: setTemperatureValue(temperature*)","name":"setTemperatureValue","parameters":[{"name":"temperature*","type":"NUMBER"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"temperature","capability":"temperatureMeasurement","label":"attribute: temperature.*","properties":{"unit":{"enum":["F","C"],"type":"string"},"value":{"maximum":10000,"minimum":-460,"title":"TemperatureValue","type":"number"}},"required":["value","unit"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setHumidityValue(humidity*)","name":"setHumidityValue","parameters":[{"name":"humidity*","type":"NUMBER"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"humidity","capability":"relativeHumidityMeasurement","label":"attribute: humidity.*","properties":{"unit":{"default":"%","enum":["%"],"type":"string"},"value":{"maximum":100,"minimum":0,"type":"number"}},"required":["value"],"title":"Percent","type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setBatteryValue(battery*)","name":"setBatteryValue","parameters":[{"name":"battery*","type":"NUMBER"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"battery","capability":"battery","label":"attribute: battery.*","properties":{"unit":{"default":"%","enum":["%"],"type":"string"},"value":{"maximum":100,"minimum":0,"type":"integer"}},"required":["value"],"title":"IntegerPercent","type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setHealthStatusValue(healthStatus*)","name":"setHealthStatusValue","parameters":[{"name":"healthStatus*","type":"ENUM"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"healthStatus","capability":"healthCheck","label":"attribute: healthStatus.*","properties":{"value":{"title":"HealthState","type":"string"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"command":{"label":"command: setPressureValue(pressure*)","name":"setPressureValue","parameters":[{"name":"pressure*","type":"STRING"}],"type":"command"},"trigger":{"additionalProperties":false,"attribute":"atmosphericPressure","capability":"atmosphericPressureMeasurement","label":"attribute: atmosphericPressure.*","properties":{"unit":{"default":"kPa","enum":["kPa","hPa","bar","mbar","mmHg","inHg","atm","psi"],"type":"string"},"value":{"minimum":0,"type":"number"}},"required":["value"],"type":"attribute"},"type":"smartTrigger"},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"capability":"refresh","label":"command: refresh()","name":"refresh","type":"command"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if (settings?.deviceInfoDisable != true) { log.info "${msg}" } }
private logDebug(msg) { if (settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if (settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn "${msg}" }
private logError(msg) { log.error "${msg}" }
