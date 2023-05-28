/**
*  Copyright 2022 bthrock
*
*  The driver is for use with Todd Austin's PurpleAir AQI driver for SmartThings, which is effectively identical to 
*  the "PurpleAir AQI Virtual Sensor" for Hubitat with one notable exception. The SmartThings driver allows the user 
*  to adjust the reporting interval when air quality conditions change rapidly. This ensures that any HVAC or other 
*  automations that rely on air quality data are using the most current data available. 
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
public static String version() {return "1.3.0"}

metadata 
{
    definition(name: "replica PurpleAirAQI", namespace: "replica", author: "bbthrock", importUrl:"https://raw.githubusercontent.com/TheMegamind/Replica-Drivers/main/replicaPurpleAirAQI.groovy")
    {
        capability "Actuator"
        capability "Sensor"
        capability "Configuration"
        capability "Refresh"
        
        attribute "aqi", "number"   		// Current AQI
        attribute "category", "string"		// Description of Current Air Quality
        attribute "sites", "string"		// List of Sensor Sites used 
        attribute "interval", "string"    	// Interval Between Updates 
        
	// "setInterval" command allows the user to override the interval selection in the SmartThings app driver presentation
	command "setInterval", [[name: "interval*", type: "ENUM", description: "Update Interval", constraints: ["1min", "5min", "10min", "15min"," 30min","60min", "180min"]]] 
        
        
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
    refresh()
}

def configure() {
    log.info "${device.displayName} configured default rules"
    initialize()
    updateDataValue("rules", getReplicaRules())
    sendCommand("configure")
}

// Methods documented here will show up in the Replica Command Configuration. These should be mostly setter in nature. 
static Map getReplicaCommands() {
    return ([ 
    	"setAqiValue":[[name:"aqi*",type:"NUMBER"]], 
	"setCategoryValue":[[name:"category*",type:"STRING"]], 
	"setSitesValue":[[name:"sites*",type:"STRING"]],
	"setIntervalValue":[[name:"interval*",type:"STRING"]],
	    
	"setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]
   ]])
}

def setAqiValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "aqi", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

def setCategoryValue(value) {
    String descriptionText = "${device.displayName} AQI Category is $value"
    sendEvent(name: "category", value: value, descriptionText: descriptionText)
    log.info descriptionText
}


def setSitesValue(value) {    
    // ST driver supplies an HTML Table; Convert Table to JSON
    def rows = value.findAll(/<tr>(.*?)<\/tr>/)
    def tableData = []
    rows.each { row ->
       def cell = row.replaceFirst(/<tr><td>(.*?)<\/td><\/tr>/, '$1')
       tableData.add(cell)
    }
    value = new groovy.json.JsonBuilder(tableData).toPrettyString()  // Convert table data to JSON
    String descriptionText = "${device.displayName} Sensor Sites are $value"
    sendEvent(name: "sites", value: value, descriptionText: descriptionText)
    log.info descriptionText
}

// The setInterval command in the ST Purple AQI driver overrides the preference setting in the 
// driver presentation in the SmartThings app. However, if the preference setting is modified
// after a setInterval command, the preference setting will take precedence. 
// 
// When the setInterval command is used, the "interval" attribute in the Hubitat driver presentation
// will report the chosen interval. If the interval is controlled by the the ST driver's preference 
// setting, that interval attribute notes that. The value of that setting is not available here.
def setIntervalValue(value) {    
    if (value != " ") {
        log.info value
        String descriptionText = "${device.displayName} Interval is $value"
        sendEvent(name: "interval", value: value, descriptionText: descriptionText)
        log.info descriptionText
    } else {
        String descriptionText = "${device.displayName} interval is managed by ST driver reference Setting"
        sendEvent(name: "interval", value: "Managed by ST Preference Setting", descriptionText: descriptionText)
        log.info descriptionText
    }  
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
static Map getReplicaTriggers() {
    return ([ 
        "setInterval":[[name:"interval*",type:"STRING"]],  
	"refresh":[]])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def setInterval(interval) {
    sendCommand("setInterval", interval)    
}

void refresh() {
    sendCommand("refresh")
}

static String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"name":"setInterval","label":"command: setInterval(interval*)","type":"command","parameters":[{"name":"interval*","type":"STRING"}]},"command":{"name":"setInterval","arguments":[{"name":"interval","optional":false,"schema":{"type":"string","maxLength":20}}],"type":"command","capability":"partyvoice23922.aqisetinterval","label":"command: setInterval(interval*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.aqisites","attribute":"sites","label":"attribute: sites.*"},"command":{"name":"setSitesValue","label":"command: setSitesValue(sites*)","type":"command","parameters":[{"name":"sites*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":1000},"unit":{"type":"string","enum":["AQI"],"default":"AQI"}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.purpleaqi","attribute":"aqi","label":"attribute: aqi.*"},"command":{"name":"setAqiValue","label":"command: setAqiValue(aqi*)","type":"command","parameters":[{"name":"aqi*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.aqicategory","attribute":"category","label":"attribute: category.*"},"command":{"name":"setCategoryValue","label":"command: setCategoryValue(category*)","type":"command","parameters":[{"name":"category*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string","maxLength":20}},"additionalProperties":false,"required":["value"],"capability":"partyvoice23922.aqisetinterval","attribute":"interval","label":"attribute: interval.*"},"command":{"name":"setIntervalValue","label":"command: setIntervalValue(interval*)","type":"command","parameters":[{"name":"interval*","type":"NUMBER"}]},"type":"smartTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
