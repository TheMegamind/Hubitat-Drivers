/**
*  Copyright 2023 bthrock
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
public static String version() {return "1.3.1"}

metadata 
{
    definition(name: "Replica Energy Monitoring Plug", namespace: "replica", author: "bthrock", importUrl:"https://raw.githubusercontent.com/TheMegamind/Hubitat/main/Replica-Drivers/replicaEnergyMonitoringPlug.groovy")
    {
	capability "Actuator"
	capability "Configuration"
	capability "EnergyMeter"
	capability "PowerMeter"
	capability "Switch"
	capability "Refresh"
        
        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {
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
	    "setSwitchValue":[[name:"switch*",type:"ENUM"]], 
	    	"setSwitchOff":[], "setSwitchOn":[], 
	    "setEnergyValue":[[name:"energy*",type:"NUMBER"]],
	    "setPowerValue":[[name:"power*",type:"NUMBER"]],
	    "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]])
}

def setSwitchValue(value) {
    String descriptionText = "${device.displayName} was turned $value"
    sendEvent(name: "switch", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setSwitchOff() {
    setSwitchValue("off")
}

def setSwitchOn() {
    setSwitchValue("on")    
}

def setEnergyValue(value) {
    String descriptionText = "${device.displayName} energy is $value kWh"
    sendEvent(name: "energy", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setPowerValue(value) {
    String descriptionText = "${device.displayName} power is $value W"
    sendEvent(name: "power", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ "off":[] , "on":[], "refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def off() { 
    sendCommand("off")
}

def on() {
    sendCommand("on")
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"off","label":"command: off()","type":"command"},"command":{"name":"off","type":"command","capability":"switch","label":"command: off()"},"type":"hubitatTrigger"},{"trigger":{"name":"on","label":"command: on()","type":"command"},"command":{"name":"on","type":"command","capability":"switch","label":"command: on()"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"SwitchState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"switch","attribute":"switch","label":"attribute: switch.*"},"command":{"name":"setSwitchValue","label":"command: setSwitchValue(switch*)","type":"command","parameters":[{"name":"switch*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"number"},"unit":{"type":"string","enum":["W"],"default":"W"}},"additionalProperties":false,"required":["value"],"capability":"powerMeter","attribute":"power","label":"attribute: power.*"},"command":{"name":"setPowerValue","label":"command: setPowerValue(power*)","type":"command","parameters":[{"name":"power*","type":"NUMBER"}]},"type":"smartTrigger","disableStatus":true},{"trigger":{"type":"attribute","properties":{"value":{"type":"number"},"unit":{"type":"string","enum":["Wh","kWh","mWh","kVAh"],"default":"kWh"}},"additionalProperties":false,"required":["value"],"capability":"energyMeter","attribute":"energy","label":"attribute: energy.*"},"command":{"name":"setEnergyValue","label":"command: setEnergyValue(energy*)","type":"command","parameters":[{"name":"energy*","type":"NUMBER"}]},"type":"smartTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
