# Overview
This project does two things:
- Automatic Trough Filler: monitors the float switch in each of two water troughs and turns on an irrigation valve to fill up a trough when the water gets low
- Leak Prevention: monitors the run time of a well pump driven by 120/240V AC power and disables power to the pump if it determines there is a likely leak. 

## Water Troughs
We send a 24V AC signal to the two trough float switches. We use 24V AC to avoid interference as the wire runs to the sensors are long and reside in the same ditch as 120V AC wires. We then use opto couplers connected to two Arduino input ports to monitor the signals coming back in. Based on the these signals, we send 24V AC to the corresponding irrigation valve in order to fill up the troughs. We use standard relays controlled by Arduino output ports to send the 24V to the irrigation valves. 


## Leak Prevention
As it is expected there is a pressure tank involved, leak detection is not as simple
as just measuring run time length. We instead look for certain conditions:
* Major Leak: In this scenario, the main pipe from the pump or one large enough to discharge water at a rate that prevents the pressure tank from filling has failed. 
* Branch/Appliance Leak: In this scenario there is a leak in a household supply line, appliance, or more likely something connected to a hose bib. The leak isn't significant enough to prevent the pressure tank from filling and cutting off the pump periodically. 
* Minor/Pinhole Leak: In this scenario there is a small leak that has a negligible effect on consumption rate and therefore is hard to detect. We do not try to handle this scenario at this point.

# Implementation
* Raspberry Pi 3 running Ubuntu 25.04, pinout: https://vilros.com/pages/raspberry-pi-3-pinout?srsltid=AfmBOoqhupbA2xsf37N1qwRffkfjB3x1ktasjpo0Kr82Q_Mipk-3zkY-
* Logs all pump on/off events to a local Postgres DB. Initial intent was to leverage TimescaleDB (sitting on top of Postgres) but compiling it to run on Arm is a lot of overhead. More importantly, reading/writing a lot of data to a micro SD card will cause the card to fail eventually. Decision is to instead just record key events (e.g. pump on/off timestamps) in the DB and maintain state in memory. State is recreated from the DB on initial load (e.g. reads last 24 hours of info) and is available for querying/reporting.
* Monitors for a Major Leak by comparing the current run time length to a max threshold.
* Monitors for a Branch/Appliance leak by comparing total run time over a period of time.

# HW
* See KiCad wiring schematic in this directory
* See [article](https://pdcastro.github.io/mains-io/) for using mains to switch low voltage signals for detecting pump on/off, using an optocoupler instead of reverse relay
* Uses Optocoupler to detect 110VAC and output 3.3V (amazon history)
* 24v AC coil 40A 2-pole contactor for disabling well pump, normally closed so pump still works if control circuit dies, hard to find nc, have to use lighting contactor (see amazon history: 32Amp 4 Pole NC Normally Closed, Lighting Contactor 24VAC Coil 934024 40A)
* 3.3v relay for driving 24v AC to irrigation valves and pump contactor (amazon history)
* 24v AC relay powering 3.3v load to indicate when float sensors engage/disengage (amazon history)

# Build
> mvn package

FTP the resulting target/pumpcontroller*.jar to raspberry pi (192.168.0.212) /opt/pumpcontroller/target

Postgres password needs to be set for OS env var: PG_PWD.
postgersdb: port 5432, sudo -u postgres psql pumpdb

# Runtime
* Created as a systemd service: pumpcontroller
* sudo systemctl [start/stop/restart/status] pumpcontroller.service
* sudo journalctl -u pumpcontroller.service -f


