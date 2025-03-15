# Pi-hole® 6 Blocking Control for Hubitat

This repository provides two Hubitat drivers—**Parent** and **Child**—that allow users to enable or disable ad-blocking on one or more Pi-hole® 6 instances—*either collectively or individually*—from their Hubitat environment. 

The parent driver dynamically creates and removes child devices based on a JSON configuration, aggregates each Pi-hole’s status, and provides group on/off/enable/disable commands. Each child device handles communication with an individual Pi-hole instance (including authentication, blocking status, and timed disables).

---
  
<h6 align="center">Note: This project is independently-maintained. The maintainer is not affiliated with the Official Pi-hole® Project at https://github.com/pi-hole in any way. Pi-hole® and the Pi-hole logo are registered trademarks of Pi-hole LLC. </h6>

---

## Features

- **Parent/Child Architecture**  
  - The **Parent** driver manages multiple Pi-holes via the child devices.  
  - The **Child** driver manages the individual Pi-hole instances, handling HTTP requests (enable/disable/refresh) and authentication.

- **Dynamic Device Creation**  
  - The parent driver reads a JSON array of Pi-hole configurations (name, URL, password) and automatically creates, updates, or removes child devices accordingly.

- **Group Control**  
  - The parent driver can enable/disable *all* Pi-holes at once using voice-friendly on/off commands or custom enable/disable commands with an optional timer.

- **Aggregated Status**  
  - The parent driver aggregates each child’s blocking status into a JSON string and sets its own “switch” attribute based on whether all Pi-holes are “enabled,” any are “error,” etc.

- **Automatic Removal of Unused Child Devices**  
  - If a Pi-hole is removed from the JSON (or its name changes), the parent can remove the old child device so Hubitat stays in sync.

- **Auto-Refresh**  
  - Optional periodic refresh of each Pi-hole’s status. The child driver re-authenticates if needed and updates the parent aggregator.

- **Configurable Logging**  
  - Both parent and child drivers have toggles for info logging and debug logging. Debug logging can automatically turn off after 30 minutes.

---

## Requirements

- **Hubitat Elevation** (tested on 2.2.x or later, though older versions may work).  
- **Pi-hole 6** instance(s) accessible from Hubitat hub’s network.

---

## Installation & Configuration

Installation via [**Hubitat Package Manager (HPM)**](https://hubitatpackagemanager.hubitatcommunity.com/installing.html) is recommended to ensure the drivers remain up-to-date with feature updates, bugfixes, or other changes. In HPM, search for the keyword "Pi-hole" and choose the "Pi-hole v6 Multi-Instance Blocking Control Drivers."

If installed via *HPM*, **skip to Step 3** below.    

---

<h4>Manual (Non-HPM) Installation</h4>

1. **Add Parent Driver Code**  *(Manual Installation Only)*
   - In Hubitat’s “Drivers Code” section, click “New Driver” and paste the raw contents of the **Parent driver file** ([*piholeV6BlockingControlParent.groovy*](https://raw.githubusercontent.com/TheMegamind/Hubitat/main/piholeV6BlockingControl/piholeV6BlockingControlParent.groovy)), then save.

2. **Add Child Driver Code**  *(Manual Installation Only)*
   - Repeat the above step for the **Child driver file**,  ([*piholeV6BlockingControlChild.groovy*](https://raw.githubusercontent.com/TheMegamind/Hubitat/main//piholeV6BlockingControl/piholeV6BlockingControlChild.groovy)).

---

<h4>Configuration (All Installations)</h4>

3. **Create a Virtual Device**  
   - Go to “Devices” → “Add Virtual Device.”  
   - Select **“Pi-hole 6 Blocking Control Parent”** from the “Type” dropdown.  
   - Name the device (e.g., “Pi-hole Control”).

4. **Configure the Parent Device**  
   - Open the device page for the new parent device.  
   - In “Preferences,” set the JSON array of Pi-hole configurations, e.g.:
     ```json
     [
       {"name": "Pi-hole 1", "url": "https://192.168.1.15:443", "password": "pass1"},
       {"name": "Pi-hole 2", "url": "https://192.168.1.11:443", "password": "pass2"}
     ]
     ```
     - For Pi‑hole installations without an admin password, the "password" field in the JSON should be left empty (i.e., `"password": ""`) or the field omitted altogether, (i.e.,`{"name": "Pi-hole 1", "url": "https://192.168.1.15:443"}`
     - **Important**: While `url` and `password` definitions can be modified later to accommodate changes in Pi-holes, changes to the `name` will **remove the existing devices and create new ones**. This will impact any any rules, pistons, or apps that use the child devices. 
   - Adjust other preferences (default blocking time, auto-refresh interval, logging options) as needed.  
   - Click “Save Preferences.” The parent driver will create or update child devices accordingly.

---

## Usage

### Group Commands (Parent)

- **Enable / Disable**  
  - These commands on the parent device enable and disable *all* Pi-holes. `Disable`, unlike `off`, accepts a timer in seconds (defaults to `defaultBlockingTime`)
- **On / Off**
  - Similar to enable/disable (but without the `disable` commands 'custom' timer option), are included for use with voice-assistants (e.g., Alexa, Google Home), which have difficulty recognizing custom commands like `enable` or `disable`. 
- **Refresh**  
  - Refreshes the current blocking status of all child devices, which then aggregates to the parent device.

### Individual Commands (Child)

- Each child device has commands for:
  - **Enable / Disable** (with optional timer).  
  - **On / Off** (voice-friendly enable/disable).  
  - **Refresh** (re-authenticates if needed, then checks blocking status).  
- Each child device also auto-updates if the parent calls `refreshAll()`.

### Logging

- **Debug Logging** can be toggled in each driver’s preferences and auto-disables after 30 minutes if enabled.  
- **Info Logging** can be toggled as well, so the user only sees essential logs if desired.

---

## Advanced Notes

1. **JSON Configuration**  
   - Each Pi-hole is defined by a JSON object with `name`, `url`, and an optional `password`.  
   - If a Pi-hole is removed from or renamed in the JSON, the parent automatically removes the old child device and creates a new one. Any existing automations referencing the old device need to be updated or removed.
     - *Note: Hubitat will sometimes refuse to remove a child device if it is “in use” by an app. If that occurs, an error message will appear in the logs.*

2. **Authentication & Timed Disables**  
   - `disable(timer)` calls the Pi-hole API to disable blocking for `timer` seconds, then re-checks status afterward.
   - Child drivers re-authenticate if a Pi-hole session ID expires.  
     - <sub>**Note**: The session timeout can be modified from Pi-hole's web admin page. Go to **Settings** > **All settings** (if you don't see the menu button, open any settings page and select **Expert** mode), then click on the **Webserver and API** tab. Modify the timeount under `webserver.session.timeout`.</sub>


3. **Error Handling**  
   - If authentication fails or the Pi-hole URL is invalid, the child sets `blockingStatus` to “Auth Failed” or “Invalid URL.”  
   - The parent’s aggregator can interpret these states and set its switch to “error.”

4. **Renaming**  
   - *Warning**: If a Pi-hole’s `name` is modified in the configuration JSON, the parent will see it as a new device and remove the old child.

---

## License

This project is licensed under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).
