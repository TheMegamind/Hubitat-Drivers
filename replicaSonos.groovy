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
public static String version() {return "1.3.0"}

metadata 
{
    definition(name: "Replica Sonos (Beta)", namespace: "replica", author: "bthrock", importUrl:"https://raw.githubusercontent.com/TheMegamind/Replica-Drivers/main/replicaSonos.groovy")
    {
        capability "Actuator"
        capability "Configuration"
	capability "AudioVolume"
        //capability "MusicPlayer"                   //Exposes Unsupported Commands in Device Presentation
        capability "Refresh"
        
	attribute "audioTrackData", "JSON_OBJECT"    //capability audioTrackData in SmartThings 
	attribute "presets", "JSON_OBJECT"           //capability mediaPreset in SmartThings 
	attribute "elapsedTime", "number"    	     //capability audioTrackData in SmartThings - Not Currently Reported
	attribute "status", "enum"    		     //capability mediaPlayback in SmartThings 
	attribute "totalTime", "number"              //capability audioTrackData in SmartThings - Not Currently Reported
		
        attribute "healthStatus", "enum", ["offline", "online"]
	    
	command "playPreset", [[name: "presetId*", type: "STRING", description: "Play the selected preset"]]
	command "nextTrack"			//Supported Sonos Command
	command "previousTrack"			//Supported Sonos Command (Has Limited Utility)
	command "play"                          //Supported Sonos Command
	command "pause"                         //Supported Sonos Command
	command "stop"                          //Supported Sonos Command
	
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
Map getReplicaCommands() {
    return ([ 
		    "setAudioTrackDataValue":[[name:"audioTrackData*",type:"JSON_OBJECT"]],
		   
		    "setMuteValue":[[name:"mute*",type:"ENUM"]],
		    "setPlaybackStatusValue":[[name:"playbackStatus*",type:"ENUM"]],
		    "setPresetsValue":[[name:"presets*",type:"JSON_OBJECT"]],
		    "setVolumeValue":[[name:"volume*",type:"NUMBER"]],
	            "setElapsedTimeValue":[[name:"elapsedTime*",type:"NUMBER"]],
	            "setSupportedPlaybackCommandsValue":[[name:"supportedPlaybackCommands*",type:"ENUM"]], 
		    "setTotalTimeValue":[[name:"totalTime*",type:"NUMBER"]],

		    "setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]

	    ])
}

def setSupportedPlaybackCommandsValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "supportedPlaybackCommands", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setPlaybackStatusValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "playbackStatus", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setMuteValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "mute", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setPresetsValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "presets", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setTotalTimeValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "totalTime", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setAudioTrackDataValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "audioTrackData", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setElapsedTimeValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "elapsedTime", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setVolumeValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "volume", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

def setHealthStatusValue(value) {    
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}

// Methods documented here will show up in the Replica Trigger Configuration. These should be all of the native capability commands
Map getReplicaTriggers() {
    return ([
	    "mute":[], 
	    "unmute":[],
	    "nextTrack":[],
	    "previousTrack":[],
	    "pause":[],
	    "play":[],
	    "stop":[],
	    "volumeDown":[],
	    "volumeUp":[],
	    "setVolume":[[name:"volume*",type:"NUMBER"]],
	    "playPreset":[[name:"presetId*",type:"STRING"]],
	    
	    "refresh":[]
    ])
}

private def sendCommand(String name, def value=null, String unit=null, data=[:]) {
    data.version=version()
    parent?.deviceTriggerHandler(device, [name:name, value:value, unit:unit, data:data, now:now()])
}

def mute() { 
    sendCommand("mute")
}

def unmute() {
    sendCommand("unmute")
}

def nextTrack() {
    sendCommand("nextTrack")
}

def previousTrack() {
    sendCommand("previousTrack")
}

def pause() {
    sendCommand("pause")
}

def play() {
    sendCommand("play")
}

def stop() {
    sendCommand("stop")
}

def volumeUp() {
    sendCommand("volumeUp")
}

def volumeDown() {
    sendCommand("volumeDown")
}

def setVolume(volume) {
    sendCommand("setVolume",volume)
}

def playPreset(presetId) {
    sendCommand("playPreset",presetId)
}

void refresh() {
    sendCommand("refresh")
}


String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"AudioTrackData","type":"object","additionalProperties":false,"properties":{"title":{"title":"String","type":"string","maxLength":255},"artist":{"title":"String","type":"string","maxLength":255},"album":{"title":"String","type":"string","maxLength":255},"albumArtUrl":{"title":"URI","type":"string","format":"uri"},"mediaSource":{"title":"String","type":"string","maxLength":255}},"required":["title"]}},"additionalProperties":false,"required":["value"],"capability":"audioTrackData","attribute":"audioTrackData","label":"attribute: audioTrackData.*"},"command":{"name":"setAudioTrackDataValue","label":"command: setAudioTrackDataValue(audioTrackData*)","type":"command","parameters":[{"name":"audioTrackData*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"MuteState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"audioMute","attribute":"mute","label":"attribute: mute.*"},"command":{"name":"setMuteValue","label":"command: setMuteValue(mute*)","type":"command","parameters":[{"name":"mute*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":[],"capability":"mediaPlayback","attribute":"playbackStatus","label":"attribute: playbackStatus.*"},"command":{"name":"setPlaybackStatusValue","label":"command: setPlaybackStatusValue(playbackStatus*)","type":"command","parameters":[{"name":"playbackStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"title":"MediaPreset","type":"object","additionalProperties":false,"properties":{"id":{"type":"string"},"name":{"type":"string"},"mediaSource":{"type":"string"},"imageUrl":{"type":"string"}},"required":["id","name"]}}},"additionalProperties":false,"required":[],"capability":"mediaPresets","attribute":"presets","label":"attribute: presets.*"},"command":{"name":"setPresetsValue","label":"command: setPresetsValue(presets*)","type":"command","parameters":[{"name":"presets*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"audioVolume","attribute":"volume","label":"attribute: volume.*"},"command":{"name":"setVolumeValue","label":"command: setVolumeValue(volume*)","type":"command","parameters":[{"name":"totalTime*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"nextTrack","label":"command: nextTrack()","type":"command"},"command":{"name":"nextTrack","type":"command","capability":"mediaTrackControl","label":"command: nextTrack()"},"type":"hubitatTrigger"},{"trigger":{"name":"previousTrack","label":"command: previousTrack()","type":"command"},"command":{"name":"previousTrack","type":"command","capability":"mediaTrackControl","label":"command: previousTrack()"},"type":"hubitatTrigger"},{"trigger":{"name":"playPreset","label":"command: playPreset(presetId*)","type":"command","parameters":[{"name":"presetId*","type":"STRING"}]},"command":{"name":"playPreset","arguments":[{"name":"presetId","optional":false,"schema":{"title":"String","type":"string","maxLength":255}}],"type":"command","capability":"mediaPresets","label":"command: playPreset(presetId*)"},"type":"hubitatTrigger"},{"trigger":{"name":"mute","label":"command: mute()","type":"command"},"command":{"name":"mute","type":"command","capability":"audioMute","label":"command: mute()"},"type":"hubitatTrigger"},{"trigger":{"name":"unmute","label":"command: unmute()","type":"command"},"command":{"name":"unmute","type":"command","capability":"audioMute","label":"command: unmute()"},"type":"hubitatTrigger"},{"trigger":{"name":"stop","label":"command: stop()","type":"command"},"command":{"name":"stop","type":"command","capability":"mediaPlayback","label":"command: stop()"},"type":"hubitatTrigger"},{"trigger":{"name":"play","label":"command: play()","type":"command"},"command":{"name":"play","type":"command","capability":"mediaPlayback","label":"command: play()"},"type":"hubitatTrigger"},{"trigger":{"name":"pause","label":"command: pause()","type":"command"},"command":{"name":"pause","type":"command","capability":"mediaPlayback","label":"command: pause()"},"type":"hubitatTrigger"},{"trigger":{"name":"volumeDown","label":"command: volumeDown()","type":"command"},"command":{"name":"volumeDown","type":"command","capability":"audioVolume","label":"command: volumeDown()"},"type":"hubitatTrigger"},{"trigger":{"name":"volumeUp","label":"command: volumeUp()","type":"command"},"command":{"name":"volumeUp","type":"command","capability":"audioVolume","label":"command: volumeUp()"},"type":"hubitatTrigger"},{"trigger":{"name":"setVolume","label":"command: setVolume(volume*)","type":"command","parameters":[{"name":"volume*","type":"NUMBER"}]},"command":{"name":"setVolume","arguments":[{"name":"volume","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioVolume","label":"command: setVolume(volume*)"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
