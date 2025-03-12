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
 *    Pi-hole® 6 Blocking Control (Child Device Driver)
 * 
 *   Date            Description
 *   -------------   ---------------------------------------------------------
 *   03-12-2025      1.0.0 Initial Release
 *
 * LINE 30 MAX 
 */
public static String version() {return "1.0.0"}

import groovy.json.JsonBuilder

final String AUTH_ENDPOINT = "/api/auth"
final String DNS_BLOCKING_ENDPOINT = "/api/dns/blocking"
final String STATUS_ENABLED = "enabled"
final String STATUS_DISABLED = "disabled"
final String STATUS_ERROR = "Error"
final String STATUS_INVALID_URL = "Invalid URL"

metadata {
    definition(
        name: "Pi-hole 6 Blocking Control Child",
        namespace: "Megamind",
        author: "Megamind",
        importUrl: "https://your.repository.url/ChildDriver.groovy"
    ) {
        capability "Actuator"
        capability "Switch"
        capability "Refresh"
        command "enable"
        command "disable", [[name: "Timer (seconds)", type: "NUMBER", description: "Disable for how long?"]]
        attribute "blockingStatus", "string"
    }
    preferences {
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

// Utility methods
def ensureHttps(String url) {
    return url.toLowerCase().startsWith("https://") ? url : "https://${url}"
}

def isValidUrl(String url) {
    def urlPattern = ~/^https:\/\/([A-Za-z0-9\-\.]+)(:\d+)?$/
    return urlPattern.matcher(url).matches()
}

def installed() {
    logInfo "Child device installed"
    initialize()
    autoLogsOff()
}

def updated() {
    logInfo "Child device updated"
    initialize()
    autoLogsOff()
}

// Revised initialize() now uses state.piConfig if available; otherwise, falls back to stored data.
def initialize() {
    // If state.piConfig already exists (from updateConfig), use it.
    def piName = state.piConfig?.name ?: (device.getDataValue("piholename") ?: device.label)
    def piUrl = state.piConfig?.url ?: device.getDataValue("piholeurl")
    def piPassword = state.piConfig?.password ?: device.getDataValue("piholepassword")
    logInfo "Child device configuration: piholename=${piName}, piholeurl=${piUrl}, piholepassword=${piPassword}"
    
    if (!piUrl) {
        logWarn "URL not provided in configuration for ${piName}"
        sendEvent(name: "blockingStatus", value: STATUS_INVALID_URL)
        return
    }
    // Update state.piConfig with the values (this ensures new values from updateConfig() persist)
    state.piConfig = [ name: piName, url: piUrl, password: piPassword ]
    state.piConfig.url = ensureHttps(state.piConfig.url)
    if (!isValidUrl(state.piConfig.url)) {
        logError "Invalid URL for ${piName}: ${state.piConfig.url}"
        sendEvent(name: "blockingStatus", value: STATUS_INVALID_URL)
        return
    }
    
    // Store new configuration in device data so it persists.
    updateDataValue("piholename", piName)
    updateDataValue("piholeurl", state.piConfig.url)
    updateDataValue("piholepassword", (piPassword ? piPassword : "****"))
    
    // Reset runtime state
    state.sid = null
    state.sidExpiration = 0
    
    if (settings.autoRefreshInterval?.toInteger() > 0) {
        scheduleAutoRefresh()
    }
    refresh() // Initial refresh (attempt to authenticate and fetch blocking status)
}

def scheduleAutoRefresh() {
    def interval = settings.autoRefreshInterval.toInteger() * 60
    runIn(interval, "refresh")
}

def on() {
    logInfo "Child ${state.piConfig.name}: on() received – enabling blocking"
    handleBlockingCommand(true, null)
    sendEvent(name: "switch", value: "on")
}

def enable() {
    logInfo "Child ${state.piConfig.name}: enable() received"
    on()
}

def off() {
    logInfo "Child ${state.piConfig.name}: off() received – disabling blocking with default timer"
    def t = settings.defaultBlockingTime ? settings.defaultBlockingTime.toInteger() : 300
    disable(t)
    sendEvent(name: "switch", value: "off")
}

def disable(timer = null) {
    def t = timer ? timer.toInteger() : (settings.defaultBlockingTime ? settings.defaultBlockingTime.toInteger() : 300)
    logInfo "Child ${state.piConfig.name}: disable() received – disabling for ${t} seconds"
    handleBlockingCommand(false, t)
    sendEvent(name: "switch", value: "off")
}

def refresh() {
    logInfo "Child ${state.piConfig.name}: refreshing blocking status"
    if (!state.piConfig.url) {
        logWarn "URL not configured for ${state.piConfig.name}"
        sendEvent(name: "blockingStatus", value: STATUS_INVALID_URL)
        return
    }
    def sid = getSessionId()
    if (sid) {
        fetchBlockingStatus(state.piConfig.url, sid, state.piConfig.name)
    } else {
        logWarn "SID not available for ${state.piConfig.name}"
        sendEvent(name: "blockingStatus", value: "Auth Failed")
    }
    if (settings.autoRefreshInterval?.toInteger() > 0) {
        def interval = settings.autoRefreshInterval.toInteger() * 60
        runIn(interval, "refresh")
    }
}

def handleBlockingCommand(boolean blockingStatus, Integer timer) {
    if (!state.piConfig.url) {
        logWarn "URL not configured for ${state.piConfig.name}"
        sendEvent(name: "blockingStatus", value: STATUS_INVALID_URL)
        return
    }
    def sid = getSessionId()
    if (!sid) {
        logError "No valid SID for ${state.piConfig.name}"
        sendEvent(name: "blockingStatus", value: "Auth Failed")
        return
    }
    setBlockingStatus(state.piConfig.url, sid, blockingStatus, timer, state.piConfig.name)
    if (!blockingStatus && timer) runIn(timer, "refresh")
}

def setBlockingStatus(String url, String sid, boolean blockingStatus, Integer timer, String piName) {
    try {
        url = ensureHttps(url)
        def payload = blockingStatus ? [blocking: blockingStatus] : [blocking: blockingStatus, timer: timer]
        def fullUrl = "${url}/api/dns/blocking"
        def postParams = [
            uri: fullUrl,
            contentType: "application/json",
            ignoreSSLIssues: settings.ignoreSSLIssues,
            headers: ['sid': sid],
            body: new JsonBuilder(payload).toString()
        ]
        logDebug "Child ${piName}: Sending setBlockingStatus POST to ${fullUrl}"
        httpPost(postParams) { resp ->
            logDebug "Child ${piName}: Response: ${resp.data}"
            if (resp.status == 200) {
                def blockingResponse = resp.data?.blocking?.toString()?.trim()?.toLowerCase()
                def status
                if (blockingResponse == "enabled") {
                    status = "enabled"
                } else if (blockingResponse == "disabled") {
                    status = "disabled"
                } else {
                    status = "unknown"
                }

                // Update blockingStatus attribute
                sendEvent(name: "blockingStatus", value: status.capitalize())

                // Also sync the standard Switch attribute
                if (status == "enabled") {
                    sendEvent(name: "switch", value: "on")
                } else if (status == "disabled") {
                    sendEvent(name: "switch", value: "off")
                }

                logInfo "Child ${piName}: Blocking status set to ${status.capitalize()}"

                // Notify parent to re-aggregate status
                logDebug "Child ${piName}: Notifying parent aggregator now..."
                if (parent) {
                    parent.runIn(1, "aggregateChildStatus")
                }
            } else {
                logError "Child ${piName}: Failed to set blocking status. HTTP ${resp.status}"
            }
        }
    } catch(Exception e) {
        logError "Child ${piName}: Error setting blocking status: ${e.message}"
        if (!settings.ignoreSSLIssues) {
            logInfo "Child ${piName}: SSL certificate validation is enforced."
        }
    }
}

def fetchBlockingStatus(String url, String sid, String piName) {
    try {
        url = ensureHttps(url)
        def fullUrl = "${url}/api/dns/blocking"
        logDebug "Child ${piName}: Sending fetchBlockingStatus GET to ${fullUrl}"
        def getParams = [
            uri: fullUrl,
            contentType: "application/json",
            ignoreSSLIssues: settings.ignoreSSLIssues,
            headers: ['sid': sid]
        ]
        httpGet(getParams) { resp ->
            if (resp.status == 200) {
                def blockingResponse = resp.data?.blocking
                logDebug "Child ${piName}: blockingResponse = ${blockingResponse}"
                def responseStr = blockingResponse?.toString()?.trim()?.toLowerCase()
                def status
                if (responseStr == "enabled") {
                    status = "enabled"
                } else if (responseStr == "disabled") {
                    status = "disabled"
                } else {
                    status = "unknown"
                }

                // Update blockingStatus attribute
                sendEvent(name: "blockingStatus", value: status.capitalize())

                // Also sync the standard Switch attribute
                if (status == "enabled") {
                    sendEvent(name: "switch", value: "on")
                } else if (status == "disabled") {
                    sendEvent(name: "switch", value: "off")
                }

                logInfo "Child ${piName}: Fetched blocking status: ${status.capitalize()}"

                // Notify parent to re-aggregate status
                if (parent && parent.hasProperty("aggregateChildStatus")) {
                    parent.runIn(1, "aggregateChildStatus")
                }
            } else {
                logError "Child ${piName}: Failed to fetch blocking status. HTTP ${resp.status}"
            }
        }
    } catch(Exception e) {
        logError "Child ${piName}: Error fetching blocking status: ${e.message}"
        if (!settings.ignoreSSLIssues) {
            logInfo "Child ${piName}: SSL certificate validation is enforced."
        }
    }
}

def getSessionId() {
    def currentTime = now()
    if (state.sid && state.sidExpiration > currentTime) {
        logDebug "Child ${state.piConfig.name}: Using existing SID: ${state.sid}"
        return state.sid
    }
    logInfo "Child ${state.piConfig.name}: SID expired or not found. Reauthenticating..."
    def password = (state.piConfig.password && state.piConfig.password != "****") ? state.piConfig.password : ""
    def authResult = authenticate(state.piConfig.url, password)
    if (!authResult) {
        logDebug "Child ${state.piConfig.name}: Setting blockingStatus to Auth Failed"
        logError "Child ${state.piConfig.name}: Authentication failed."
        sendEvent(name: "blockingStatus", value: "Auth Failed")
        return null
    }
    def sid = authResult[0]
    def validity = authResult[1]?.toInteger() ?: 1800
    if (sid) {
        state.sid = sid
        state.sidExpiration = currentTime + (validity * 1000)
        logDebug "Child ${state.piConfig.name}: New SID: ${sid}, valid for ${validity} seconds"
    }
    return sid
}

def authenticate(String url, String password) {
    try {
        url = ensureHttps(url)
        def payload = password ? new JsonBuilder([password: password]).toString() : '{"password":""}'
        def fullUrl = "${url}/api/auth"
        def postParams = [
            uri: fullUrl,
            contentType: "application/json",
            ignoreSSLIssues: settings.ignoreSSLIssues,
            body: payload
        ]
        logDebug "Child ${state.piConfig.name}: Sending authentication POST to ${fullUrl}"
        def sid = null
        def validity = 1800
        httpPost(postParams) { resp ->
            logDebug "Child ${state.piConfig.name}: Auth response: ${resp.data}"
            if (resp.status == 200) {
                def sessionData = resp.data?.session
                sid = sessionData?.sid
                validity = sessionData?.validity ?: validity
            } else {
                logError "Child ${state.piConfig.name}: Authentication failed. HTTP ${resp.status}"
            }
        }
        return [sid, validity]
    } catch(groovyx.net.http.HttpResponseException e) {
        if (e.statusCode == 401) {
            logError "Child ${state.piConfig.name}: Authentication failed: Unauthorized. Check password."
        } else {
            logError "Child ${state.piConfig.name}: HTTP error during authentication: ${e.statusCode} ${e.message}"
        }
        return null
    } catch(Exception e) {
        logError "Child ${state.piConfig.name}: Error during authentication: ${e.message}"
        return null
    }
}

// Updates the child's configuration without needing to delete the device.
def updateConfig(newConfig) {
    // Clear old session state.
    state.sid = null
    state.sidExpiration = 0
    // Expect newConfig as a map with keys: piholename, piholeurl, piholepassword
    state.piConfig = [
        name: newConfig.piholename ?: device.label,
        url: newConfig.piholeurl ?: (state.piConfig ? state.piConfig.url : ""),
        password: newConfig.piholepassword ?: (state.piConfig ? state.piConfig.password : "")
    ]
    logInfo "Child ${state.piConfig.name}: Configuration updated to: ${state.piConfig}"
    // Update device data values so they persist.
    updateDataValue("piholename", state.piConfig.name)
    updateDataValue("piholeurl", state.piConfig.url)
    updateDataValue("piholepassword", state.piConfig.password ? state.piConfig.password : "****")
    // Reinitialize the driver with the new configuration.
    initialize()
}

// Utility Methods
private logInfo(msg)  { if(settings?.deviceInfoEnable != false) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
