/**
 *  Copyright 2025 Megamind
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
 *    Pi-hole® 6 Blocking Control (Parent Device Driver)
 *    →→ Enable/Disable DNS blocking for one or more Pi-hole® 6 instances
 *    →→ Pi-holes may be controlled collectively or individually
 *    →→ Disable Blocking using user-defined Default or command-time option
 *    →→ Alternate On/Off Switch for compatibility with voice assistants
 *    →→ Optional Auto-Refresh to track blocking status changes initiated elsewhere (i.e., web admin)
 *    →→ Tracks SID Expiration and reuses unexpired SIDs or re-authenticates to minimize active sessions 
 * 
 *   Date            Description
 *   -------------   -----------------------------------------------------------------------------
 *   03-13-2025      1.0.0 Initial Release
 *   03-14-2025      1.0.1 Revise importUrl
 *
 * LINE 30 MAX 
 */
public static String version() {return "1.0.1"}

import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
    definition(
        name: "Pi-hole 6 Blocking Control Parent",
        namespace: "Megamind",
        author: "Megamind",
        importUrl: "https://raw.githubusercontent.com/TheMegamind/Hubitat/main/piholeV6BlockingControl/piholeV6BlockingControlParent.groovy"
    ) {
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        command "enable"
        command "disable", [[name: "Timer (seconds)", type: "NUMBER", description: "Disable for how long?"]]
        
        // Aggregated status from all children (as JSON)
        attribute "blockingStatus", "string"
    }
    
    preferences {
        input name: "piHoleConfigurations", type: "text", title: "Pi-hole Configurations (JSON Array)", 
            description: "Enter a JSON array of configurations. Example:\n" +
                         "[{\"name\": \"Pi-hole 1\", \"url\": \"https://192.168.1.15:443\", \"password\": \"pass1\"}, " +
                         "{\"name\": \"Pi-hole 2\", \"url\": \"https://192.168.1.11:443\", \"password\": \"pass2\"}]",
            required: true
        input name: "defaultBlockingTime", type: "number", title: "Default Blocking Time (seconds)", defaultValue: 300, required: false
        input name: "autoRefreshInterval", type: "number", title: "Auto Refresh Interval (minutes)", 
            description: "Set to 0 to disable auto-refresh", defaultValue: 0, required: false
        input name: "ignoreSSLIssues", type: "bool", title: "Ignore SSL Issues for HTTP Requests?", defaultValue: true, required: false
        input(name:"deviceInfoEnable", type:"bool", title: "Enable Info logging:", defaultValue: true, width:4)
	input(name: "deviceDebugEnable", type: "bool", title: "Enable Debug Logging?", description: "If enabled, will Auto Disable after 30 minutes",defaultValue: true,  width:4)
	//input(name:"deviceTraceEnable", type:"bool", title: "Enable Trace logging:", defaultValue: false, width:4)
    }  
}

def logsOff() {
    device.updateSetting("deviceDebugEnable",[value:'false',type:"bool"])
    device.updateSetting("deviceTraceEnable",[value:'false',type:"bool"])
    logInfo "${device.displayName} Debug logs disabled"
}

Boolean autoLogsOff() {
    if ((Boolean)settings.deviceDebugEnable || (Boolean)settings.deviceTraceEnable) {
        runIn(1800, "logsOff") 
    } else {
        unschedule("logsOff")
    }
    return true
}

def installed() {
    logInfo "Parent driver installed"
    initialize()
    autoLogsOff()
}

def updated() {
    logInfo "Parent driver updated"
    initialize()
    autoLogsOff()
    updateChildConfigs()
}

def initialize() {
    createChildDevices()
    // No subscribe() calls are used in driver code.
    refreshAll()
    if (settings.autoRefreshInterval?.toInteger() > 0) {
        scheduleAutoRefresh()
    }
}

// Implement refresh() so the capability is supported.
def refresh() {
    refreshAll()
}

def createChildDevices() {
    def configs = []
    try {
        configs = new groovy.json.JsonSlurper().parseText(settings.piHoleConfigurations)
    } catch(Exception e) {
        logError "Invalid JSON in configurations: ${e.message}"
        return
    }
    
    // 1. Build a list (or set) of Pi-hole names from the JSON
    def configNames = configs.collect { it.name }
    
    // 2. Remove any child devices whose label is NOT in configNames
    getChildDevices().each { child ->
        if (!configNames.contains(child.displayName)) {
            logInfo "Removing child device '${child.displayName}' because it is no longer in the JSON config"
            try {
                deleteChildDevice(child.deviceNetworkId)
                logInfo "Removed child device '${child.displayName}'"
            } catch (Exception ex) {
                // Because the environment doesn't recognize com.hubitat.app.exception.InUseByAppException,
                // we check the exception message for a clue that the device is still in use by an app.
                def msg = ex.message?.toLowerCase()
                if (msg?.contains("in use") || msg?.contains("app")) {
                    logWarn "Cannot remove child device '${child.displayName}': device is in use by an app. " +
                            "Remove it from any apps before deleting."
                } else {
                    logError "Error removing child device '${child.displayName}': ${ex.message}"
                }
            }
        }
    }
    
    // 3. Create (or log that we have) child devices for each config in the JSON
    configs.eachWithIndex { config, index ->
        def name = config.name ?: "Pi-hole ${index+1}"
        def dni = "pihole-" + name.toLowerCase().replaceAll("\\s+", "-")
        def label = name
        if (!getChildDevice(dni)) {
            try {
                def dataMap = [
                    piholename    : config.name.toString(),
                    piholeurl     : config.url.toString(),
                    piholepassword: config.password ? config.password.toString() : ""
                ]
                def child = addChildDevice("Megamind", "Pi-hole 6 Blocking Control Child", dni, [label: label, data: dataMap])
                logInfo "Created child device: '${label}' (DNI: ${dni})"
            } catch(Exception e) {
                logError "Error creating child device '${label}': ${e.message}"
            }
        } else {
            logInfo "Child device '${label}' already exists"
        }
    }
}

def scheduleAutoRefresh() {
    def interval = settings.autoRefreshInterval.toInteger() * 60
    runIn(interval, "refreshAll")
}

def refreshAll() {
    logInfo "Refreshing all child devices"
    // Request each child to refresh its status.
    getChildDevices().each { child ->
        child.refresh()
    }
    // Wait 5 seconds before aggregating statuses.
    runIn(5, "aggregateChildStatus")
}

def aggregateChildStatus() {
    def aggregatedStatus = [:]
    def childStatuses = []

    getChildDevices().each { child ->
        def status = child.currentValue("blockingStatus") ?: "Unknown"
        aggregatedStatus[child.displayName] = status
        childStatuses << status.toLowerCase()
    }
    // Store a JSON representation of all child statuses
    sendEvent(name: "blockingStatus", value: groovy.json.JsonOutput.toJson(aggregatedStatus))

    // 1) If any child is "Auth Failed", "Error", or "Unknown", set parent switch to "error"
    // 2) Else if all children are "enabled", set parent switch to "on"
    // 3) Otherwise, set parent switch to "off"

    def anyError = childStatuses.any { it in ["auth failed", "error", "unknown"] }
    def allEnabled = childStatuses.every { it == "enabled" }

    def parentSwitch
    if (anyError) {
        parentSwitch = "error"
    } else if (allEnabled) {
        parentSwitch = "on"
    } else {
        parentSwitch = "off"
    }

    sendEvent(name: "switch", value: parentSwitch)
    logInfo "Parent: Aggregated child statuses = ${aggregatedStatus}, switch set to '${parentSwitch}'"
}

// updateChildConfigs() updates existing children with new configuration settings.
def updateChildConfigs() {
    def configs = []
    try {
        configs = new JsonSlurper().parseText(settings.piHoleConfigurations)
    } catch(Exception e) {
        logError "Invalid JSON in configurations: ${e.message}"
        return
    }
    getChildDevices().each { child ->
        def childName = child.displayName
        def config = configs.find { it.name == childName }
        if (config) {
            def newConfig = [
                piholename: config.name.toString(),
                piholeurl: config.url.toString(),
                piholepassword: config.password ? config.password.toString() : ""
            ]
            child.updateConfig(newConfig)
            logInfo "Parent: Updated config for child device ${childName}"
        }
    }
    runIn(5, "aggregateChildStatus")
}

def on() {
    logInfo "Parent: on() command received – enabling all"
    getChildDevices().each { child -> child.on() }
    sendEvent(name: "switch", value: "on")
    runIn(5, "aggregateChildStatus")
}

def off() {
    logInfo "Parent: off() command received – disabling all using default timer"
    getChildDevices().each { child -> child.off() }
    sendEvent(name: "switch", value: "off")
    runIn(5, "aggregateChildStatus")
}

def enable() {
    logInfo "Parent: enable() command received – enabling all"
    getChildDevices().each { child -> child.enable() }
    sendEvent(name: "switch", value: "on")
    runIn(5, "aggregateChildStatus")
}

def disable(timer = null) {
    def t = timer ? timer.toInteger() : (settings.defaultBlockingTime ? settings.defaultBlockingTime.toInteger() : 300)
    logInfo "Parent: disable() command received – disabling all for ${t} seconds"
    getChildDevices().each { child -> child.disable(t) }
    sendEvent(name: "switch", value: "off")
    runIn(5, "aggregateChildStatus")
}

// Utility Methods
private logInfo(msg)  { if(settings?.deviceInfoEnable != false) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
