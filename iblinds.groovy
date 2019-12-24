/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 * Change Log 
 * V1.02  -  Added Reverse Option             - Eric B  09-02-2019
 * V1.03  -  Added Open % Value user option   - Eric B  11-25-2019
 * V1.04  -  Updated to work with new ST app and query device for states instead of assuming communications succeeded  - Jimmy H 12-22-2019
 *
 */
 import groovy.json.JsonOutput
 
metadata {
	definition (name: "iblinds", namespace: "iblinds", author: "HAB", ocfDeviceType: "oic.d.blind") {
		capability "Window Shade"
		capability "Window Shade Preset"
		capability "Switch Level"
		capability "Switch"
		capability "Actuator"
		capability "Refresh"
		capability "Battery"
		capability "Configuration"
		capability "Health Check"
	

		//fingerprint inClusters: "0x26"
		fingerprint type: "1106", cc: "5E,85,59,86,72,5A,73,26,25,80"
		fingerprint mfr: "0287", prod: "0003", model: "000D", deviceJoinName: "iBlinds"
	
	}

	simulator {
		status "on":  "command: 2003, payload: FF"
		status "off": "command: 2003, payload: 00"
		status "09%": "command: 2003, payload: 09"
		status "10%": "command: 2003, payload: 0A"
		status "33%": "command: 2003, payload: 21"
		status "66%": "command: 2003, payload: 42"
		status "99%": "command: 2003, payload: 63"

		// reply messages
		reply "2001FF,delay 5000,2602": "command: 2603, payload: FF"
		reply "200100,delay 5000,2602": "command: 2603, payload: 00"
		reply "200119,delay 5000,2602": "command: 2603, payload: 19"
		reply "200132,delay 5000,2602": "command: 2603, payload: 32"
		reply "20014B,delay 5000,2602": "command: 2603, payload: 4B"
		reply "200163,delay 5000,2602": "command: 2603, payload: 63"
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"blind", type: "lighting", width: 6, height: 4, canChangeIcon: true, canChangeBackground: true){
			tileAttribute ("device.windowShade", key: "PRIMARY_CONTROL") {
				attributeState "open", label:'${name}', action:"switch.off", icon:"https://raw.githubusercontent.com/habhomegit/Smartthings_Z-Wave/master/blind.png", backgroundColor:"#00B200", nextState:"closing"
				attributeState "closed", label:'${name}', action:"switch.on", icon:"https://raw.githubusercontent.com/habhomegit/Smartthings_Z-Wave/master/blind.png", backgroundColor:"#ffffff", nextState:"opening"
				attributeState "opening", label:'${name}', action:"switch.off", icon:"https://raw.githubusercontent.com/habhomegit/Smartthings_Z-Wave/master/blind.png", backgroundColor:"#00B200", nextState:"closing"
				attributeState "closing", label:'${name}', action:"switch.on", icon:"https://raw.githubusercontent.com/habhomegit/Smartthings_Z-Wave/master/blind.png", backgroundColor:"#ffffff", nextState:"opening" 
			}
			
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
			attributeState "level", action:"switch level.setLevel"
		}
			tileAttribute("device.battery", key: "SECONDARY_CONTROL") {
			attributeState "battery", label:'Battery Level: ${currentValue}%', unit:"%"    
			}
	
		}

		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {	state "battery", label:'${currentValue}% Battery Level', unit:""
		}
		
		valueTile("levelval", "device.level", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {	state "Level", label:'${currentValue}% Tilt Angle', unit:""
		}
		
		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:"Refresh", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		standardTile("config", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:'Calibrate', action:"configuration.configure" , icon:"st.custom.buttons.add-icon"
		}

		main(["blind"])
	   	details(["blind", "levelval", "battery", "levelSliderControl", "refresh"])

	}
	
	  preferences {
		//input( "time", "time", title: "Check battery level every day at: ", description: "Enter time", defaultValue: now(), required: true)
		input("reverse", "bool", title: "Reverse", description: "Reverse Blind Direction?",defaultValue: "false", required: true)
		input("openLevel", "number", title: "Open Level", description: "Open % Level", defaultValue: 50, range: "1..100", required: true)
	 }
	
}

def parse(String description) {
	def result = null
	if (description != "updated") {
		log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description, [0x20: 1, 0x26: 1, 0x26: 3, 0x70: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	if (result?.name == 'hail' && hubFirmwareLessThan("000.011.00602")) {
		result = [result, response(zwave.basicV1.basicGet())]
		log.debug "Was hailed: requesting state update"
	} else {
		log.debug "Parse returned ${result?.descriptionText}"
	}
	log.debug "Parsed '$description' to ${result.inspect()}"
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelReport cmd) {
	dimmerEvents(cmd)
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv3.SwitchMultilevelSet cmd) {
	dimmerEvents(cmd)
}

private dimmerEvents(physicalgraph.zwave.Command cmd) {
	def descriptionText = null
	def shadeValue = null
	def switchValue = null

	def level = cmd.value as Integer
	level = reverse ? 99-level : level
	if (level >= 99) {
		level = 100
		shadeValue = "closed"
		switchValue = "off"
	} else if (level > (openLevel - 10) && level < (openLevel + 10)) {
		shadeValue = "open"
		switchValue = "on"
	} else if (level <= 0) {
		level = 0  // unlike dimmer switches, the level isn't saved when closed
		shadeValue = "closed"
		switchValue = "off"
	} else {
		shadeValue = "partially open"
		switchValue = "on"
		descriptionText = "${device.displayName} shade is ${level}% open"
	}
	def levelEvent = createEvent(name: "level", value: level, unit: "%", displayed: false)
	def stateEvent = createEvent(name: "windowShade", value: shadeValue, descriptionText: descriptionText, isStateChange: levelEvent.isStateChange)
	def switchEvent = createEvent(name: "switch", value: switchValue)

	def result = [stateEvent, levelEvent, switchEvent]
	if (!state.lastbatt || now() - state.lastbatt > 24 * 60 * 60 * 1000) {
		log.debug "requesting battery"
		state.lastbatt = (now() - 23 * 60 * 60 * 1000) // don't queue up multiple battery reqs in a row
		result << response(["delay 15000", zwave.batteryV1.batteryGet().format()])
	}
	result
}


def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	log.debug "ConfigurationReport $cmd"
	def value = "when off"
	if (cmd.configurationValue[0] == 1) {value = "when on"}
	if (cmd.configurationValue[0] == 2) {value = "never"}
	createEvent([name: "indicatorStatus", value: value])
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	createEvent([name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false])
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	log.debug "manufacturerId:   ${cmd.manufacturerId}"
	log.debug "manufacturerName: ${cmd.manufacturerName}"
	log.debug "productId:        ${cmd.productId}"
	log.debug "productTypeId:    ${cmd.productTypeId}"
	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	updateDataValue("MSR", msr)
	if (cmd.manufacturerName) {
		updateDataValue("manufacturer", cmd.manufacturerName)
	}
	createEvent([descriptionText: "$device.displayName MSR: $msr", isStateChange: false])
}

def zwaveEvent(physicalgraph.zwave.commands.switchmultilevelv1.SwitchMultilevelStopLevelChange cmd) {
	[createEvent(name:"switch", value:"on"), response(zwave.switchMultilevelV1.switchMultilevelGet().format())]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	// Handles all Z-Wave commands we aren't interested in
	log.debug "unhandled $cmd"
	return [:]
}

def on() {
	open()
}

def off() {
	close()		
}

def open() {
	log.debug "Opening $device.displayName"
	setLevel(openLevel)
}

def close() {
	log.debug "Closing $device.displayName"
	setLevel(0)
	/*
	if(reverse) {
		sendEvent(name: "switch", value: "off")
		sendEvent(name: "windowShade", value: "closed")
		sendEvent(name: "level", value: 99, unit: "%")
		zwave.switchMultilevelV2.switchMultilevelSet(value: 0x63).format()
		} else {
		sendEvent(name: "switch", value: "off")
		sendEvent(name: "windowShade", value: "closed")
		sendEvent(name: "level", value: 0, unit: "%")
		zwave.switchMultilevelV2.switchMultilevelSet(value: 0x00).format()
		}
		*/
}

def setLevel(value, duration = null) {
	log.debug "setLevel >> value: (${value.inspect()})"
	Integer level = value as Integer
	level = reverse ? 99-level : level
	if (level < 0) level = 0
	if (level > 99) level = 99
	
	//zwave.basicV1.basicSet(value: level).format()
	
	delayBetween([
		zwave.switchMultilevelV2.switchMultilevelSet(value: level).format(),
		zwave.switchMultilevelV2.switchMultilevelGet().format()
		], 3000)
}

/*def setLevel(value, duration = null) {
	log.debug "setLevel >> value: $value, duration: $duration"
	def valueaux = value as Integer
	def level = Math.max(Math.min(valueaux, 99), 0)
	 if(reverse)
	 {
	   level = 99 - level
	 }
	
	def dimmingDuration = duration < 128 ? duration : 128 + Math.round(duration / 60)
	def getStatusDelay = duration < 128 ? (duration*1000)+2000 : (Math.round(duration / 60)*60*1000)+2000
	zwave.switchMultilevelV2.switchMultilevelSet(value: level, dimmingDuration: dimmingDuration).format()
				
}*/

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	state.lastbatt = now()
	createEvent(map)
}

def getCheckInterval() {
	// Device Health will ping() the device at this interval (in seconds) if there hasn't heard anything
	// 6 hours
	6 * 60 * 60
}

def installed () {
	// When device is installed get battery level and set daily schedule for battery refresh
	// Don't need this with device health since it will ping the device every CheckInterval if there isn't any events
	/*log.debug "Installed, Set Get Battery Schedule"
	runIn(15,getBattery) 
	schedule("$time",getBattery)*/ 
	sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	sendEvent(name: "supportedWindowShadeCommands", value: JsonOutput.toJson(["open", "close", "pause"]), displayed: false)
	response(refresh())
	
}

def updated () {
	// When device is updated get battery level and set daily schedule for battery refresh
	/*log.debug "Updated , Set Get Battery Schedule"
	runIn(15,getBattery) 
	schedule("$time",getBattery)*/
	if (device.latestValue("checkInterval") != checkInterval) {
		sendEvent(name: "checkInterval", value: checkInterval, displayed: false)
	}
	def cmds = []
	if (!device.latestState("battery")) {
		cmds << zwave.batteryV1.batteryGet().format()
	}

	if (!device.getDataValue("MSR")) {
		cmds << zwave.manufacturerSpecificV1.manufacturerSpecificGet().format()
	}

	cmds << zwave.switchMultilevelV1.switchMultilevelGet().format()
	response(cmds)
}

def presetPosition() {
	log.debug "Moving $device.displayName to the preset position"
	setLevel(openLevel ?: state.openLevel ?: 50)
}

def pause() {
	stop()
}

def stop() {
	log.debug "Stopping $device.displayName"
	zwave.switchMultilevelV3.switchMultilevelStopLevelChange().format()
}

def ping() {
	refresh()     
}

def refresh() {
	log.debug "Refreshing $device.displayName"
	delayBetween([
		zwave.switchMultilevelV1.switchMultilevelGet().format(),
		zwave.batteryV1.batteryGet().format(),
	], 3000)
}

/*
def getBattery() {
	log.debug  "get battery level"
	// Use sendHubCommand to get battery level 
	def cmd = []
	cmd << new physicalgraph.device.HubAction(zwave.batteryV1.batteryGet().format())
	sendHubCommand(cmd)
	
}
*/


/* The configure method is an advanced feature to launch calibration from the SmartThings App.
**** USE AT YOUR OWN RISK ****
Changing the configurationValue will allow you to change the inital calibration torque 
Reducing the torque helps improve calibration for small blinds 
Increasing the torque helps imporve calibration for large blinds

Note: You must add "config" to the details above to expose the config button in the App

-- details(["blind", "levelval", "battery", "levelSliderControl",  "refresh", "config"]) --

Update configurationValue[N] as follows: 

1 - Calibrate at default torque values
2 - Reduce calibration torque by 1 factor
3 - Reduce calibration torque by 2 factor
4 - Increase calibration torque by .5 factor
5. - Increase calibration torque by 1 factor 

--  zwave.configurationV2.configurationSet(parameterNumber: 1, size: 1, configurationValue: [N]).format()  

*/


/*
def configure() {

	 // Run Calibration 
	   log.debug "Configuration tile pushed"
	   zwave.configurationV2.configurationSet(parameterNumber: 1, size: 1, configurationValue: [1]).format()      
}
*/