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
 *
 *  Left It Open
 *
 *  Author: SmartThings
 *  Date: 2013-05-09
 */
definition(
    name: "Left It Open",
    namespace: "smartthings",
    author: "SmartThings",
    description: "Notifies you when you have left a door or window open longer that a specified amount of time.",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/bon-voyage.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/ModeMagic/bon-voyage%402x.png"
)

preferences {

  section("Monitor this door or window") {
    input "contact", "capability.contactSensor", multiple: true
  }

  section("And notify me if it's open for more than this many minutes (default 10)") {
    input "openThreshold", "number", description: "Number of minutes", required: false
  }

  section("Delay between notifications (default 10 minutes") {
    input "frequency", "number", title: "Number of minutes", description: "", required: false
  }

  section("Via text message at this number (or via push notification if not specified") {
    input("recipients", "contact", title: "Send notifications to") {
      input "phone", "phone", title: "Phone number (optional)", required: false
    }
  }
}

def installed() {
  log.trace "installed()"
  subscribe()
}

def updated() {
  log.trace "updated()"
  unsubscribe()
  subscribe()
}

def subscribe() {
  subscribe(contact, "contact.open", doorOpen)
  runIn(0, doorOpenTooLong, [overwrite: true])
}

def doorOpen(evt) {
  log.trace "doorOpen($evt.name: $evt.value)"
  def delay = (openThreshold != null && openThreshold != "") ? openThreshold * 60 : 600
  runIn(delay, doorOpenTooLong, [overwrite: true])
}

def doorOpenTooLong() {
  def freq = (frequency != null && frequency != "") ? frequency * 60 : 600
  def open = false
  contact.each {
    open = checkContact(it) || open
  }
  
  if (open) {
    log.debug "Contacts are still open:  calling doorOpenTooLong() in $freq ms"
    runIn(freq, doorOpenTooLong, [overwrite: false])
  } else {
    log.debug "All contacts are closed:  exiting doorOpenTooLong()"
  }
}

boolean checkContact(contact) {
  def contactState = contact.currentState("contact")

  if (contactState.value == "open") {
    def elapsed = now() - contactState.rawDateCreated.time
    def threshold = ((openThreshold != null && openThreshold != "") ? openThreshold * 60000 : 60000) - 1000
    if (elapsed >= threshold) {
      log.debug "Contact has stayed open long enough since last check ($elapsed ms):  calling sendMessage()"
      sendMessage(contact)
    } else {
      log.debug "Contact has not stayed open long enough since last check ($elapsed ms < $threshold ms):  doing nothing"
    }
    return true
  } else {
    log.warn "checkContact($contact) called but contact is closed:  doing nothing"
  }
  return false
}

void sendMessage(contact) {
  def minutes = (openThreshold != null && openThreshold != "") ? openThreshold : 10
  def msg = "${contact.displayName} has been left open for ${minutes} minute(s)."
  log.info msg
  if (location.contactBookEnabled) {
    sendNotificationToContacts(msg, recipients)
  } else {
    if (phone) {
      sendSms phone, msg
    } else {
      sendPush msg
    }
  }
}