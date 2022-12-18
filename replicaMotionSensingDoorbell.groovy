/**
*  Copyright 2022 bthrock
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
public static String version() {return "1.2.0"}

metadata 
{
    definition(name: "Replica Motion-Sensing Doorbell", namespace: "replica", author: "bthrock", importUrl:"https://raw.githubusercontent.com/TheMegamind/Replica-Drivers/main/replicaMotionSensingDoorbell.groovy")
    {
        capability "Actuator"
        capability "Configuration"
        capability "MotionSensor"
        capability "Switch"
        capability "Momentary"
        capability "Refresh"

        attribute "healthStatus", "enum", ["offline", "online"]
    }
    preferences {   
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
    log.info "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
Map getReplicaCommands() {
    return ([ "push":[],          
              "setMotionValue":[[name:"motion*",type:"ENUM"]], "setMotionActive":[], "setMotionInactive":[], 
              "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
            ])
}

def push() {
    String descriptionText = "${device.displayName} was pushed"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "momentary", value: "pushed", isStateChange: true)
    runIn(3,toggleMom)
    log.info descriptionText
}

def toggleMom(value){
    sendEvent(name: "switch", value: "off")
}

def on() {
    push()
}

def off() {
    push()
}

def setMotionValue(value) {
    String descriptionText = "${device.displayName} motion is $value"
    sendEvent(name: "motion", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setMotionActive() {
    setMotionValue("active")
}

def setMotionInactive() {
    setMotionValue("inactive")    
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([ "refresh":[] ])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now])
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[

        {"trigger":{"type":"attribute","properties":{"value":{"title":"ActivityState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"motionSensor","attribute":"motion","label":"attribute: motion.*"},"command":{"name":"setMotionValue","label":"command: setMotionValue(motion*)","type":"command","parameters":[{"name":"motion*","type":"ENUM"}]},"type":"smartTrigger"},

        {"trigger":{"type":"attribute","properties":{"value":{"title":"ButtonState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"button","attribute":"button","label":"attribute: button.pushed","value":"pushed","dataType":"ENUM"},"command":{"name":"push","label":"command: push","type":"command","parameters":[{"name":"button","type":"ENUM"}]},"type":"smartTrigger","disableStatus":true},

        {"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger","mute":true}

    ]}"""
}
