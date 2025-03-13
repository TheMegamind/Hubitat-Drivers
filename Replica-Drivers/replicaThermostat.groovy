/**
*  Copyright 2024 bthrock
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
public static String version() {return "1.3.5"}

metadata 
{
    definition(name: "Replica Thermostat", namespace: "replica", author: "bthrock", importUrl:"https://raw.githubusercontent.com/TheMegamind/Replica-Drivers/main/replicaThermostat.groovy")
    {
        capability "Actuator"
        capability "Battery"
        capability "Configuration"
        capability "Refresh"
        capability "Sensor"
        capability "Thermostat"
        capability "TemperatureMeasurement"
        
        command "setThermostatOperatingState", [[name: "Thermostat Operating State*", type: "ENUM", description: "Override the SmartThings Operating State or remove it", constraints: [ "idle", "vent economizer", "pending cool", "cooling", "heating", "pending heat", "fan only", "[remove attribute]" ]]]
        
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {
        input(name:"deviceRefreshPollInterval", type: "enum", title: "Refresh Poll Interval", options: [ "manual":"Manual Poll Only", "runEvery1Minute":"1 Minute", "runEvery5Minutes":"5 Minutes", "runEvery10Minutes":"10 Minutes", "runEvery30Minutes":"30 Minutes", "runEvery1Hour":"1 Hour", "runEvery3Hours":"3 Hours"], defaultValue: "manual", required: false)
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
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
    if(settings?.deviceRefreshPollInterval && settings.deviceRefreshPollInterval!="manual") {
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
    return ([ 
      "setBatteryValue":[[name:"battery*",type:"NUMBER"]],
      "setCoolingSetpointValue":[[name:"temperature*",type:"NUMBER"]], 
      "setHeatingSetpointValue":[[name:"temperature*",type:"NUMBER"]], 
      "setSupportedThermostatFanModesValue":[[name:"supportedThermostatFanModes*",type:"ENUM"]],
      "setSupportedThermostatModesValue":[[name:"supportedThermostatModes*",type:"ENUM"]],
      "setTemperatureValue":[[name:"temperature*",type:"NUMBER"]], 
      "setThermostatFanModeValue":[[name:"mode*",type:"ENUM"]], 
      "setThermostatFanModeAuto":[], "setThermostatFanModeCirculate":[], "setThermostatFanModeOn":[],"setThermostatFanModeFollowSchedule":[],
      "setThermostatModeValue":[[name:"mode*",type:"ENUM"]],
      "setThermostatModeAuto":[], "setThermostatModeCool":[], "setThermostatModeEmergencyHeat":[], "setThermostatModeHeat":[], "setThermostatModeOff":[],
      "setThermostatOperatingStateValue":[[name:"thermostatOperatingState*",type:"ENUM"]], 
      "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
    ])
}

def setBatteryValue(value) {
    String descriptionText = "${device.displayName} battery level is $value %"
    sendEvent(name: "battery", value: value, unit: "%", descriptionText: descriptionText)
    logInfo descriptionText
}

def setCoolingSetpointValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} coolingSetPoint is $value $unit"
    sendEvent(name: "coolingSetpoint", value: value?.toFloat(), unit: unit, descriptionText: descriptionText)
    if((device.currentValue("thermostatMode",true)=="cool" || device.currentValue("thermostatMode",true)=="auto")) {
        sendEvent(name: "thermostatSetpoint", value: value?.toFloat(), unit: unit)
        if(device.currentValue("heatingSetpoint",true)?.toFloat()<value?.toFloat()-2.0)
            sendEvent(name: "heatingSetpoint", value: value?.toFloat()-2.0, unit: unit)        
    }
    logInfo descriptionText
}

def setHeatingSetpointValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} heatingSetpoint is $value $unit"
    sendEvent(name: "heatingSetpoint", value: value?.toFloat(), unit: unit, descriptionText: descriptionText)    
    if((device.currentValue("thermostatMode",true)=="heat" || device.currentValue("thermostatMode",true)=="auto")) {
        sendEvent(name: "thermostatSetpoint", value: value?.toFloat(), unit: unit)
        if(device.currentValue("coolingSetpoint",true)?.toFloat()<value?.toFloat()+2.0)
            sendEvent(name: "coolingSetpoint", value: value?.toFloat()+2.0, unit: unit)        
    }
    logInfo descriptionText
}

def setSupportedThermostatFanModesValue(value) {
    String descriptionText = "${device.displayName} supported thermostat fan modes are $value"
    sendEvent(name: "supportedThermostatFanModes", value: groovy.json.JsonOutput.toJson(value), descriptionText: descriptionText)
    logInfo descriptionText
}


def setSupportedThermostatModesValue(value) {
    String descriptionText = "${device.displayName} supported thermostat modes are $value"
    sendEvent(name: "supportedThermostatModes", value: groovy.json.JsonOutput.toJson(value), descriptionText: descriptionText)
    logInfo descriptionText
}

def setTemperatureValue(value) {
    String unit = "°${getTemperatureScale()}"
    String descriptionText = "${device.displayName} temperature is $value $unit"
    sendEvent(name: "temperature", value: value?.toFloat(), unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

def setThermostatFanModeValue(value) {
	String descriptionText = "${device.displayName} thermostatFanMode is $value"
    sendEvent(name: "thermostatFanMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setThermostatFanModeAuto() {
    setThermostatFanModeValue ("auto")
}

def setThermostatFanModeCirculate() {
    setThermostatFanModeValue ("circulate")
}

def setThermostatFanModeOn() {
    setThermostatFanModeValue ("on")
}

def setThermostatFanModeFollowSchedule() {
    setThermostatFanModeValue ("followSchedule")
}

def setThermostatModeValue(value) {
    String descriptionText = "${device.displayName} thermostatMode is $value"
    sendEvent(name: "thermostatMode", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setThermostatModeAuto() {
    setThermostatModeValue("auto")
}

def setThermostatModeCool() {
    setThermostatModeValue("cool")
}

def setThermostatModeEmergencyHeat() {
    setThermostatModeValue("emergencyHeat")
}

def setThermostatModeHeat() {
    setThermostatModeValue("heat")
}

def setThermostatModeOff() {
    setThermostatModeValue("off")
}

def setThermostatOperatingStateValue(value) {
    String descriptionText = "${device.displayName} thermostatOperatingState is $value"
    sendEvent(name: "thermostatOperatingState", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([
      "setCoolingSetpoint":[[name:"temperature*",type:"NUMBER"]], 
      "setHeatingSetpoint":[[name:"temperature*",type:"NUMBER"]],   
      "setTemperature":[[name:"temperature*",type:"NUMBER"]], 
      "setThermostatFanMode":[[name:"thermostatFanMode*",type:"ENUM"]], 
      "fanAuto":[], "fanCirculate":[], "fanOn":[],"followSchedule":[],
      "setThermostatMode":[[name:"thermostatMode*",type:"ENUM"]],
      "auto":[], "cool":[], "emergencyHeat":[], "heat":[], "off":[],
      "setThermostatOperatingState":[[name:"thermostatOperatingState*",type:"ENUM"]], 
      "refresh":[]
    ])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def setCoolingSetpoint(value) {
    String unit = "°${getTemperatureScale()}"
    sendCommand("setCoolingSetpoint", value,unit)
}

def setHeatingSetpoint(value) {
    String unit = "°${getTemperatureScale()}"
    sendCommand("setHeatingSetpoint",value,unit)
}

def setTemperature(value) {
    String unit = "°${getTemperatureScale()}"
    sendCommand("setTemperatureValue",value,unit)
}

def setThermostatFanMode(value) {
    if(!device.currentValue("setThermostatFanMode",true) || (new groovy.json.JsonSlurper().parseText(device.currentValue("setThermostatFanMode",true))?.find{ it==value })) {
        sendCommand("setThermostatFanMode",value)
    } else if(!!device.currentValue("setThermostatFanMode",true)) {
        logWarn "${device.displayName} setThermostatFanMode '$value' not found in ${device.currentValue("setThermostatFanMode",true)}"
    } 
}

def fanAuto() {
    setThermostatFanMode("auto")
}

def fanCirculate() {
    setThermostatFanMode("circulate")
}

def fanOn() {
    setThermostatFanMode("on")
}

def followSchedule() {
    setThermostatFanMode("followSchedule")
}

def setThermostatMode(value) {
    if(!device.currentValue("supportedThermostatModes",true) || (new groovy.json.JsonSlurper().parseText(device.currentValue("supportedThermostatModes",true))?.find{ it==value })) {
        sendCommand("setThermostatMode",value)
    } else if(!!device.currentValue("supportedThermostatModes",true)) {
        logWarn "${device.displayName} setThermostatMode '$value' not found in ${device.currentValue("supportedThermostatModes",true)}"
    }  
}

def auto() {
    setThermostatMode("auto")
}

def cool() {
    setThermostatMode("cool")
}

def emergencyHeat() {
    setThermostatMode("emergencyHeat")
}

def heat() {
    setThermostatMode("heat")
}

def off() {
    setThermostatMode("off")
}

// SmartThings does not allow setting 'thermostatOperatingState' and appears this isn't always supported (Honeywell T6 for example)
// Workaround to set value by other 'things' since SharpTools will active/inactive on the value. 
def setThermostatOperatingState(value) {
    if(value=="[remove attribute]") {
        setThermostatOperatingStateValue(" ")
        pauseExecution(500)
        device.deleteCurrentState("thermostatOperatingState")
        logWarn "${device.displayName} removed 'thermostatOperatingState'. Please refresh browser and/or update SharpTools account to complete."
    }
    else {
        setThermostatOperatingStateValue(value)
    }
}

void refresh() {
    sendCommand("refresh")
}


static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"auto","label":"command: auto()","type":"command"},"command":{"name":"auto","type":"command","capability":"thermostatMode","label":"command: auto()"},"type":"hubitatTrigger"},{"trigger":{"name":"cool","label":"command: cool()","type":"command"},"command":{"name":"cool","type":"command","capability":"thermostatMode","label":"command: cool()"},"type":"hubitatTrigger"},{"trigger":{"name":"emergencyHeat","label":"command: emergencyHeat()","type":"command"},"command":{"name":"emergencyHeat","type":"command","capability":"thermostatMode","label":"command: emergencyHeat()"},"type":"hubitatTrigger"},{"trigger":{"name":"fanAuto","label":"command: fanAuto()","type":"command"},"command":{"name":"fanAuto","type":"command","capability":"thermostatFanMode","label":"command: fanAuto()"},"type":"hubitatTrigger"},{"trigger":{"name":"fanCirculate","label":"command: fanCirculate()","type":"command"},"command":{"name":"fanCirculate","type":"command","capability":"thermostatFanMode","label":"command: fanCirculate()"},"type":"hubitatTrigger"},{"trigger":{"name":"fanOn","label":"command: fanOn()","type":"command"},"command":{"name":"fanOn","type":"command","capability":"thermostatFanMode","label":"command: fanOn()"},"type":"hubitatTrigger"},{"trigger":{"name":"heat","label":"command: heat()","type":"command"},"command":{"name":"heat","type":"command","capability":"thermostatMode","label":"command: heat()"},"type":"hubitatTrigger"},{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"thermostatMode","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"name":"setCoolingSetpoint","label":"command: setCoolingSetpoint(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"command":{"name":"setCoolingSetpoint","arguments":[{"name":"setpoint","optional":false,"schema":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000}}],"type":"command","capability":"thermostatCoolingSetpoint","label":"command: setCoolingSetpoint(setpoint*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setHeatingSetpoint","label":"command: setHeatingSetpoint(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"command":{"name":"setHeatingSetpoint","arguments":[{"name":"setpoint","optional":false,"schema":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000}}],"type":"command","capability":"thermostatHeatingSetpoint","label":"command: setHeatingSetpoint(setpoint*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setThermostatFanMode","label":"command: setThermostatFanMode(thermostatFanMode*)","type":"command","parameters":[{"name":"thermostatFanMode*","type":"ENUM"}]},"command":{"name":"setThermostatFanMode","arguments":[{"name":"mode","optional":false,"schema":{"title":"ThermostatFanMode","type":"string","enum":["auto","circulate","followschedule","on"]}}],"type":"command","capability":"thermostatFanMode","label":"command: setThermostatFanMode(mode*)"},"type":"hubitatTrigger"},{"trigger":{"name":"setThermostatMode","label":"command: setThermostatMode(thermostatMode*)","type":"command","parameters":[{"name":"thermostatMode*","type":"ENUM"}]},"command":{"name":"setThermostatMode","arguments":[{"name":"mode","optional":false,"schema":{"title":"ThermostatMode","type":"string","enum":["asleep","auto","autowitheco","autowithreset","autochangeover","autochangeoveractive","autocool","autoheat","auxheatonly","auxiliaryemergencyheat","away","cool","custom","dayoff","dryair","eco","emergency heat","emergencyheat","emergencyheatactive","energysavecool","energysaveheat","fanonly","frostguard","furnace","heat","heatingoff","home","in","manual","moistair","off","out","resume","rush hour","rushhour","schedule","southernaway"]}}],"type":"command","capability":"thermostatMode","label":"command: setThermostatMode(mode*)"},"type":"hubitatTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"battery","attribute":"battery","label":"attribute: battery.*"},"command":{"name":"setBatteryValue","label":"command: setBatteryValue(battery*)","type":"command","parameters":[{"name":"battery*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"title":"Temperature","type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"thermostatCoolingSetpoint","attribute":"coolingSetpoint","label":"attribute: coolingSetpoint.*"},"command":{"name":"setCoolingSetpointValue","label":"command: setCoolingSetpointValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"title":"Temperature","type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"thermostatHeatingSetpoint","attribute":"heatingSetpoint","label":"attribute: heatingSetpoint.*"},"command":{"name":"setHeatingSetpointValue","label":"command: setHeatingSetpointValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"title":"ThermostatFanMode","type":"string","enum":["auto","circulate","followschedule","on"]}}},"additionalProperties":false,"required":[],"capability":"thermostatFanMode","attribute":"supportedThermostatFanModes","label":"attribute: supportedThermostatFanModes.*"},"command":{"name":"setSupportedThermostatFanModesValue","label":"command: setSupportedThermostatFanModesValue(supportedThermostatFanModes*)","type":"command","parameters":[{"name":"supportedThermostatFanModes*","type":"JSON_OBJECT"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"title":"ThermostatMode","type":"string","enum":["asleep","auto","autowitheco","autowithreset","autochangeover","autochangeoveractive","autocool","autoheat","auxheatonly","auxiliaryemergencyheat","away","cool","custom","dayoff","dryair","eco","emergency heat","emergencyheat","emergencyheatactive","energysavecool","energysaveheat","fanonly","frostguard","furnace","heat","heatingoff","home","in","manual","moistair","off","out","resume","rush hour","rushhour","schedule","southernaway"]}}},"additionalProperties":false,"required":[],"capability":"thermostatMode","attribute":"supportedThermostatModes","label":"attribute: supportedThermostatModes.*"},"command":{"name":"setSupportedThermostatModesValue","label":"command: setSupportedThermostatModesValue(supportedThermostatModes*)","type":"command","parameters":[{"name":"supportedThermostatModes*","type":"JSON_OBJECT"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"title":"TemperatureValue","type":"number","minimum":-460,"maximum":10000},"unit":{"type":"string","enum":["F","C"]}},"additionalProperties":false,"required":["value","unit"],"capability":"temperatureMeasurement","attribute":"temperature","label":"attribute: temperature.*"},"command":{"name":"setTemperatureValue","label":"command: setTemperatureValue(temperature*)","type":"command","parameters":[{"name":"temperature*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ThermostatFanMode","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"supportedThermostatFanModes":{"type":"array","items":{"title":"ThermostatFanMode","type":"string","enum":["auto","circulate","followschedule","on"]}}}}},"additionalProperties":false,"required":["value"],"capability":"thermostatFanMode","attribute":"thermostatFanMode","label":"attribute: thermostatFanMode.*"},"command":{"name":"setThermostatFanModeValue","label":"command: setThermostatFanModeValue(mode*)","type":"command","parameters":[{"name":"thermostatFanMode*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ThermostatMode","type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"supportedThermostatModes":{"type":"array","items":{"title":"ThermostatMode","type":"string","enum":["asleep","auto","autowitheco","autowithreset","autochangeover","autochangeoveractive","autocool","autoheat","auxheatonly","auxiliaryemergencyheat","away","cool","custom","dayoff","dryair","eco","emergency heat","emergencyheat","emergencyheatactive","energysavecool","energysaveheat","fanonly","frostguard","furnace","heat","heatingoff","home","in","manual","moistair","off","out","resume","rush hour","rushhour","schedule","southernaway"]}}}}},"additionalProperties":false,"required":["value"],"capability":"thermostatMode","attribute":"thermostatMode","label":"attribute: thermostatMode.*"},"command":{"name":"setThermostatModeValue","label":"command: setThermostatModeValue(mode*)","type":"command","parameters":[{"name":"thermostatMode*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"ThermostatOperatingState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"thermostatOperatingState","attribute":"thermostatOperatingState","label":"attribute: thermostatOperatingState.*"},"command":{"name":"setThermostatOperatingStateValue","label":"command: setThermostatOperatingStateValue(thermostatOperatingState*)","type":"command","parameters":[{"name":"thermostatOperatingState*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"},"data":{"type":"object","additionalProperties":false,"required":[],"properties":{"deviceScheme":{"type":"string","enum":["MIXED","TRACKED","UNTRACKED"]},"badProperty":{"type":"string"},"reason":{"type":"string","enum":["INVALID-ACCESS-TOKEN","INTEGRATION-OFFLINE","DEVICE-OFFLINE","DEVICE-DELETED","DEVICE-UNAVAILABLE","DEVICE-UNDISCOVERED"]}}}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"DeviceWatch-DeviceStatus","label":"attribute: DeviceWatch-DeviceStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
