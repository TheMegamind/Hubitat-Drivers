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
***** Thanks to @bloodtick_jones, developer of HubiThings Replica, whose work provided the foundation. All mistakes are mine. *****
*/
@SuppressWarnings('unused')
public static String version() {return "1.3.0"}
import groovy.json.JsonBuilder
import groovy.json.JsonOutput
import java.text.SimpleDateFormat

Random rnd = new Random()

metadata 
{
    definition(name: "Replica Sonos", namespace: "replica", author: "bthrock", importUrl:"https://raw.githubusercontent.com/TheMegamind/Replica-Drivers/main/replicaSonos.groovy")
    {
	capability "Actuator"
	capability "AudioNotification"
	capability "AudioVolume"
	capability "Configuration"
	capability "MusicPlayer"			
	capability "Refresh"

	//capability audioTrackData in SmartThings 
	//attribute "audioTrackData", "JSON_OBJECT"         // use native 'trackData' in Hubitat 
	attribute "elapsedTime", "number"    	    	    // Omitted from Rules as elapsedTime is reporting null values
	attribute "totalTime", "number"             	    // Omitted from Rules as totalTime is reporting null values
	attribute "artist", "string"
	attribute "album", "string"
	attribute "title", "string"
	attribute "albumArtUrl", "string"

	//capability mediaGroup in SmartThings
	attribute "groupMute", "enum"	             	
	attribute "groupPrimaryDeviceId", "string"   	
	attribute "groupId", "string"   		    
	attribute "groupVolume", "number"   	     	
	attribute "groupRole", "enum"  
	command "setGroupVolume", [[name:"groupVolume*",type:"NUMBER"]]
	command "groupVolumeUp"
	command "groupVolumeDown"
	command "setGroupMute", [[name: "state*", type: "ENUM", description: "Set Group Mute", constraints: ["muted","unmuted"]]]
	command "muteGroup"
	command "unmuteGroup"
    

	//capability mediaPlayback in SmartThings
	//attribute "playbackStatus", "enum"               	// Use native 'status' in Hubitat
	//attribute "supportedPlaybackCommands","enum"		// Omitted here and from Rules; not needed by Hubitat
	command "playFavorite", [[name: "favoriteId", type: "STRING", description: "Favorite Id Number"],[name: "favoriteName", type: "STRING", description: "Favorite Name"]]

	//capability mediaPreset in SmartThings
	attribute "favorites", "string"                		// json String; "string" is correct here
	attribute "lastFavoriteRequest", "string"		// json String; "string" is correct here
	//attribute "presets", "JSON_OBJECT"                	// "Presets" in ST Driver; "Favorites" in Sonos UI; use the latter instead
	//attribute "supportedTrackControlCommands","enum"	// Omitted here and from Rules; not needed by Hubitat
	
	//workaround for native mediaPlayer 'playTrack' command, which does not have the volumelevel argument
	command "playTrack", [[name: "trackuri*", type: "STRING", description: "Play the selected track"],[name: "volumelevel", type: "NUMBER", description: "Volume (0-100)%"]]

	attribute "healthStatus", "enum", ["offline", "online"]

    }
    preferences {
        input(name:"deviceInfoDisable", type: "bool", title: "Disable Info logging:", defaultValue: false)
        input(name:"favoritesAsAttribute", type: "bool", title: "Include Favorites in Attributes:", defaultValue: false)
        input(name:"partialMatches", type: "bool", title: "Allow Partial Matches for Favorite Name:", defaultValue: false)
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
	    	"setTrackDataValue":[[name:"trackData*",type:"JSON_OBJECT"]],
		"setElapsedTimeValue":[[name:"elapsedTime*",type:"NUMBER"]],
		"setTotalTimeValue":[[name:"totalTime*",type:"NUMBER"]],

		"setGroupMuteValue":[[name:"groupMute*",type:"ENUM"]],
		"setGroupPrimaryDeviceIdValue":[[name:"groupPrimaryDeviceId*",type:"STRING"]],
		"setGroupIdValue":[[name:"groupId*",type:"STRING"]],
		"setGroupVolumeValue":[[name:"groupVolume*",type:"NUMBER"]],
		"setGroupRoleValue":[[name:"groupRole*",type:"ENUM"]],

		"setMuteValue":[[name:"mute*",type:"ENUM"]],
		"setStatusValue":[[name:"Status*",type:"ENUM"]],
		"setFavoritesValue":[[name:"favorites*",type:"JSON_OBJECT"]],
		"setVolumeValue":[[name:"volume*",type:"NUMBER"]],
		"setSupportedPlaybackCommandsValue":[[name:"supportedPlaybackCommands*",type:"ENUM"]],
		"setSupportedTrackControlCommandsValue":[[name:"supportedTrackControlCommands*",type:"ENUM"]],

		"setHealthStatusValue":[[name:"healthStatus*",type:"ENUM"]]
	    ])
}

//capability audioTrackData in SmartThings 
def setTrackDataValue(event) {   
    trackData = new JsonBuilder(event.value).toPrettyString()
    String descriptionText = "${device.displayName}'s trackData is $trackData"
    sendEvent(name: "trackData", value: trackData, descriptionText: descriptionText)
    logInfo descriptionText
    trackDescription = "${event.value.title} by ${event.value.artist}"
    sendEvent(name: "trackDescription", value: trackDescription)
}

//capability audioTrackData in SmartThings 
//No rules defined as elapsedTime is reporting null values
def setElapsedTimeValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "elapsedTime", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability audioTrackData in SmartThings 
//No rules defined as totalTime is reporting null values
def setTotalTimeValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "totalTime", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability mediaGroup in SmartThings
def setGroupMuteValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "groupMute", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability mediaGroup in SmartThings
def setGroupPrimaryDeviceIdValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "groupPrimaryDeviceId", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability mediaGroup in SmartThings
def setGroupIdValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "groupId", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability mediaGroup in SmartThings
def setGroupVolumeValue(value) {
    String unit = "%"
    String descriptionText = "${device.displayName} is $value $unit"
    sendEvent(name: "groupVolume", value: value, unit: unit, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability mediaGroup in SmartThings
def setGroupRoleValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "groupRole", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability audioMute in SmartThings
def setMuteValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "mute", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability mediaPlayback in SmartThings
def setStatusValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "status", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}


//capability mediaPresets in SmartThings
def setFavoritesValue(event) {
    favorites = event.value
    favorites = favorites.sort { a,b -> a.name <=> b.name}       //Sort by Name for potential dropdown  
    state.favorites = favorites
    if(settings?.favoritesAsAttribute == true) {
        favorites = new JsonBuilder(event.value).toPrettyString()
        sendEvent(name: "favorites", value: favorites)
    } else if (device.currentValue("favorites") != null) {       
        sendEvent(name: "favorites", value: null)
        device.deleteCurrentState("favorites")
    }
    logInfo "Sonos Favorites ($state.favorites.size total) have been updated"
}

//capability audioVolume in SmartThings
//Native HE Integration uses both Volume and Level, So Both are Included Here
def setVolumeValue(value) {
    String unit = "%"
    String descriptionText = "${device.displayName} is $value $unit"
    sendEvent(name: "volume", value: value, unit: unit)
    sendEvent(name: "level", value: value, unit: unit)
    logInfo descriptionText
}

//capability mediaPlayback in SmartThings
//Omitted from Rules; not needed by Hubitat
def setSupportedPlaybackCommandsValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "supportedPlaybackCommands", value: value, descriptionText: descriptionText)
    logInfo descriptionText
}

//capability mediaTrackControl in SmartThings
//Omitted from Rules; not needed by Hubitat
def setSupportedTrackControlCommandsValue(value) {
    String descriptionText = "${device.displayName} is $value"
    sendEvent(name: "supportedTrackControlCommands", value: value, descriptionText: descriptionText)
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
		"setLevel":[[name:"volume*",type:"NUMBER"]],
		"playFavorite": [[name:"id*",type:"STRING"]],
		"groupVolumeUp":[],
		"groupVolumeDown":[],
		"muteGroup":[],
		"unmuteGroup":[],
		"setGroupMute":[[name: "state*", type: "ENUM"]],
		"setGroupVolume":[[name:"groupVolume*",type:"NUMBER"]],
		"playTrack":[[name:"trackuri*",type:"STRING"],[name:"volumelevel*",type:"NUMBER",data:"volumelevel"]],
		"playTrackAndResume":[[name:"trackuri*",type:"STRING"],[name:"volumelevel*",type:"NUMBER",data:"volumelevel"]],
		"playTrackAndRestore":[[name:"trackuri*",type:"STRING"],[name:"volumelevel*",type:"NUMBER",data:"volumelevel"]],

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

def setLevel(volume) {
    sendCommand("setVolume",volume)
}

def playFavorite(favoriteId=null,favoriteName=null) {   
    if(favoriteId != null) {
        selectedFavorite = state.favorites.find {it.id==favoriteId}
        method = "id"
        if(selectedFavorite == null) { 
            log.info  "No match for Favorite Id requested. Selecting Random Favorite." 
        } 
    } else if((favoriteName != null) && (settings?.partialMatches == false)) {   
        selectedFavorite = state.favorites.find {it.name.toLowerCase()==favoriteName.toLowerCase()}
        method = "name"
        if(selectedFavorite == null) { 
            log.info  "No match for Favorite Name requested. Selecting Random Favorite." 
        } 
    } else if((favoriteName != null) && (settings?.partialMatches == true) ) {     
        selectedFavorite = state.favorites.find {it.name.toLowerCase().contains(favoriteName.toLowerCase())}
        method = "name (partial match)"
        if(selectedFavorite == null) { 
            logInfo "No match for partial Favorite Name requested. Selecting Random Favorite."
        }
    }
    if (selectedFavorite == null) {
        Random rnd = new Random()
        selectedFavorite = state.favorites[(rnd.nextInt(state.favorites.size))]
        method = "random"
    }
    id = selectedFavorite.id
    name = selectedFavorite.name
    mediaSource = selectedFavorite.mediaSource
    imageUrl = selectedFavorite.imageUrl
    sendCommand("playFavorite",id)
    logInfo "Requesting Favorite ${name} (ID ${id}) by ${method} on ${device.displayName}"
    date = new Date()
    sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    requestDate = sdf.format(date)   
    lastFavoriteRequest = JsonOutput.toJson(
        [
            id: id, 
            name: name, 
            mediaSource: mediaSource,
            imageUrl: imageUrl,
            method: method, 
            time: requestDate 
        ]
    )   
    sendEvent(name: "lastFavoriteRequest", value: lastFavoriteRequest)                         
}  

def groupVolumeUp() {
    sendCommand("groupVolumeUp")
}
		
def groupVolumeDown() {
    sendCommand("groupVolumeDown")
}

def setGroupVolume(groupVolume) {
    sendCommand("setGroupVolume",groupVolume)
}
		
def muteGroup() {
    sendCommand("muteGroup")
}

def unmuteGroup() {
    sendCommand("unmuteGroup")
}

def setGroupMute(state) {
    sendCommand("setGroupMute",state)
}

def playTrack(trackuri, volumelevel=null) {
    sendCommand("playTrack",trackuri, null, [volumelevel:volumelevel])
}

def playTrackAndResume(trackuri, volumelevel=null) {
    sendCommand("playTrackAndResume", trackuri, null, [volumelevel:volumelevel])
}

def playTrackAndRestore(trackuri, volumelevel=null) {
    sendCommand("playTrackAndRestore", trackuri, null, [volumelevel:volumelevel])
}

void refresh() {
    sendCommand("refresh")
}

String getReplicaRules() {
    return """{"version":1,"components":[{"trigger":{"type":"attribute","properties":{"value":{"title":"HealthState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"healthCheck","attribute":"healthStatus","label":"attribute: healthStatus.*"},"command":{"name":"setHealthStatusValue","label":"command: setHealthStatusValue(healthStatus*)","type":"command","parameters":[{"name":"healthStatus*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"MuteState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"audioMute","attribute":"mute","label":"attribute: mute.*"},"command":{"name":"setMuteValue","label":"command: setMuteValue(mute*)","type":"command","parameters":[{"name":"mute*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"audioVolume","attribute":"volume","label":"attribute: volume.*"},"command":{"name":"setVolumeValue","label":"command: setVolumeValue(volume*)","type":"command","parameters":[{"name":"totalTime*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"nextTrack","label":"command: nextTrack()","type":"command"},"command":{"name":"nextTrack","type":"command","capability":"mediaTrackControl","label":"command: nextTrack()"},"type":"hubitatTrigger"},{"trigger":{"name":"previousTrack","label":"command: previousTrack()","type":"command"},"command":{"name":"previousTrack","type":"command","capability":"mediaTrackControl","label":"command: previousTrack()"},"type":"hubitatTrigger"},{"trigger":{"name":"mute","label":"command: mute()","type":"command"},"command":{"name":"mute","type":"command","capability":"audioMute","label":"command: mute()"},"type":"hubitatTrigger"},{"trigger":{"name":"unmute","label":"command: unmute()","type":"command"},"command":{"name":"unmute","type":"command","capability":"audioMute","label":"command: unmute()"},"type":"hubitatTrigger"},{"trigger":{"name":"stop","label":"command: stop()","type":"command"},"command":{"name":"stop","type":"command","capability":"mediaPlayback","label":"command: stop()"},"type":"hubitatTrigger"},{"trigger":{"name":"play","label":"command: play()","type":"command"},"command":{"name":"play","type":"command","capability":"mediaPlayback","label":"command: play()"},"type":"hubitatTrigger"},{"trigger":{"name":"pause","label":"command: pause()","type":"command"},"command":{"name":"pause","type":"command","capability":"mediaPlayback","label":"command: pause()"},"type":"hubitatTrigger"},{"trigger":{"name":"volumeDown","label":"command: volumeDown()","type":"command"},"command":{"name":"volumeDown","type":"command","capability":"audioVolume","label":"command: volumeDown()"},"type":"hubitatTrigger"},{"trigger":{"name":"volumeUp","label":"command: volumeUp()","type":"command"},"command":{"name":"volumeUp","type":"command","capability":"audioVolume","label":"command: volumeUp()"},"type":"hubitatTrigger"},{"trigger":{"name":"setVolume","label":"command: setVolume(volume*)","type":"command","parameters":[{"name":"volume*","type":"NUMBER"}]},"command":{"name":"setVolume","arguments":[{"name":"volume","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioVolume","label":"command: setVolume(volume*)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"String","type":"string","maxLength":255}},"additionalProperties":false,"required":["value"],"capability":"mediaGroup","attribute":"groupId","label":"attribute: groupId.*"},"command":{"name":"setGroupIdValue","label":"command: setGroupIdValue(groupId*)","type":"command","parameters":[{"name":"groupId*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"MuteState","type":"string"}},"additionalProperties":false,"required":["value"],"capability":"mediaGroup","attribute":"groupMute","label":"attribute: groupMute.*"},"command":{"name":"setGroupMuteValue","label":"command: setGroupMuteValue(groupMute*)","type":"command","parameters":[{"name":"groupMute*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"String","type":"string","maxLength":255}},"additionalProperties":false,"required":["value"],"capability":"mediaGroup","attribute":"groupPrimaryDeviceId","label":"attribute: groupPrimaryDeviceId.*"},"command":{"name":"setGroupPrimaryDeviceIdValue","label":"command: setGroupPrimaryDeviceIdValue(groupPrimaryDeviceId*)","type":"command","parameters":[{"name":"groupPrimaryDeviceId*","type":"STRING"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":["value"],"capability":"mediaGroup","attribute":"groupRole","label":"attribute: groupRole.*"},"command":{"name":"setGroupRoleValue","label":"command: setGroupRoleValue(groupRole*)","type":"command","parameters":[{"name":"groupRole*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"title":"IntegerPercent","type":"attribute","properties":{"value":{"type":"integer","minimum":0,"maximum":100},"unit":{"type":"string","enum":["%"],"default":"%"}},"additionalProperties":false,"required":["value"],"capability":"mediaGroup","attribute":"groupVolume","label":"attribute: groupVolume.*"},"command":{"name":"setGroupVolumeValue","label":"command: setGroupVolumeValue(groupVolume*)","type":"command","parameters":[{"name":"groupVolume*","type":"NUMBER"}]},"type":"smartTrigger"},{"trigger":{"name":"muteGroup","label":"command: muteGroup()","type":"command"},"command":{"name":"muteGroup","type":"command","capability":"mediaGroup","label":"command: muteGroup()"},"type":"hubitatTrigger"},{"trigger":{"name":"setGroupVolume","label":"command: setGroupVolume(groupVolume*)","type":"command","parameters":[{"name":"groupVolume*","type":"NUMBER"}]},"command":{"name":"setGroupVolume","arguments":[{"name":"groupVolume","optional":false,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"mediaGroup","label":"command: setGroupVolume(groupVolume*)"},"type":"hubitatTrigger"},{"trigger":{"name":"unmuteGroup","label":"command: unmuteGroup()","type":"command"},"command":{"name":"unmuteGroup","type":"command","capability":"mediaGroup","label":"command: unmuteGroup()"},"type":"hubitatTrigger"},{"trigger":{"name":"groupVolumeDown","label":"command: groupVolumeDown()","type":"command"},"command":{"name":"groupVolumeDown","type":"command","capability":"mediaGroup","label":"command: groupVolumeDown()"},"type":"hubitatTrigger"},{"trigger":{"name":"groupVolumeUp","label":"command: groupVolumeUp()","type":"command"},"command":{"name":"groupVolumeUp","type":"command","capability":"mediaGroup","label":"command: groupVolumeUp()"},"type":"hubitatTrigger"},{"trigger":{"name":"playTrackAndRestore","label":"command: playTrackAndRestore(trackuri*, volumelevel*)","type":"command","parameters":[{"name":"trackuri*","type":"STRING"},{"name":"volumelevel*","type":"NUMBER","data":"volumelevel"}]},"command":{"name":"playTrackAndRestore","arguments":[{"name":"uri","optional":false,"schema":{"title":"URI","type":"string","format":"uri"}},{"name":"level","optional":true,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioNotification","label":"command: playTrackAndRestore(uri*, level)"},"type":"hubitatTrigger"},{"trigger":{"name":"playTrackAndResume","label":"command: playTrackAndResume(trackuri*, volumelevel*)","type":"command","parameters":[{"name":"trackuri*","type":"STRING"},{"name":"volumelevel*","type":"NUMBER","data":"volumelevel"}]},"command":{"name":"playTrackAndResume","arguments":[{"name":"uri","optional":false,"schema":{"title":"URI","type":"string","format":"uri"}},{"name":"level","optional":true,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioNotification","label":"command: playTrackAndResume(uri*, level)"},"type":"hubitatTrigger"},{"trigger":{"name":"playTrack","label":"command: playTrack(trackuri*, volumelevel*)","type":"command","parameters":[{"name":"trackuri*","type":"STRING"},{"name":"volumelevel*","type":"NUMBER","data":"volumelevel"}]},"command":{"name":"playTrack","arguments":[{"name":"uri","optional":false,"schema":{"title":"URI","type":"string","format":"uri"}},{"name":"level","optional":true,"schema":{"type":"integer","minimum":0,"maximum":100}}],"type":"command","capability":"audioNotification","label":"command: playTrack(uri*, level)"},"type":"hubitatTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"title":"AudioTrackData","type":"object","additionalProperties":false,"properties":{"title":{"title":"String","type":"string","maxLength":255},"artist":{"title":"String","type":"string","maxLength":255},"album":{"title":"String","type":"string","maxLength":255},"albumArtUrl":{"title":"URI","type":"string","format":"uri"},"mediaSource":{"title":"String","type":"string","maxLength":255}},"required":["title"]}},"additionalProperties":false,"required":["value"],"capability":"audioTrackData","attribute":"audioTrackData","label":"attribute: audioTrackData.*"},"command":{"name":"setTrackDataValue","label":"command: setTrackDataValue(trackData*)","type":"command","parameters":[{"name":"trackData*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"string"}},"additionalProperties":false,"required":[],"capability":"mediaPlayback","attribute":"playbackStatus","label":"attribute: playbackStatus.*"},"command":{"name":"setStatusValue","label":"command: setStatusValue(Status*)","type":"command","parameters":[{"name":"Status*","type":"ENUM"}]},"type":"smartTrigger"},{"trigger":{"type":"attribute","properties":{"value":{"type":"array","items":{"title":"MediaPreset","type":"object","additionalProperties":false,"properties":{"id":{"type":"string"},"name":{"type":"string"},"mediaSource":{"type":"string"},"imageUrl":{"type":"string"}},"required":["id","name"]}}},"additionalProperties":false,"required":[],"capability":"mediaPresets","attribute":"presets","label":"attribute: presets.*"},"command":{"name":"setFavoritesValue","label":"command: setFavoritesValue(favorites*)","type":"command","parameters":[{"name":"favorites*","type":"JSON_OBJECT"}]},"type":"smartTrigger"},{"trigger":{"name":"playFavorite","label":"command: playFavorite(id*)","type":"command","parameters":[{"name":"id*","type":"STRING"}]},"command":{"name":"playPreset","arguments":[{"name":"presetId","optional":false,"schema":{"title":"String","type":"string","maxLength":255}}],"type":"command","capability":"mediaPresets","label":"command: playPreset(presetId*)"},"type":"hubitatTrigger"},{"trigger":{"name":"refresh","label":"command: refresh()","type":"command"},"command":{"name":"refresh","type":"command","capability":"refresh","label":"command: refresh()"},"type":"hubitatTrigger","disableStatus":true},{"trigger":{"name":"setGroupMute","label":"command: setGroupMute(state*)","type":"command","parameters":[{"name":"state*","type":"ENUM"}]},"command":{"name":"setGroupMute","arguments":[{"name":"state","optional":false,"schema":{"title":"MuteState","type":"string","enum":["muted","unmuted"]}}],"type":"command","capability":"mediaGroup","label":"command: setGroupMute(state*)"},"type":"hubitatTrigger"}]}"""
}

private logInfo(msg)  { if(settings?.deviceInfoDisable != true) { log.info  "${msg}" } }
private logDebug(msg) { if(settings?.deviceDebugEnable == true) { log.debug "${msg}" } }
private logTrace(msg) { if(settings?.deviceTraceEnable == true) { log.trace "${msg}" } }
private logWarn(msg)  { log.warn   "${msg}" }
private logError(msg) { log.error  "${msg}" }
