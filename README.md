# Overview
This project monitors the run time of a pump driven by 120/240V AC power and then disables power to the pump if it determines there is a likely leak. As it is expected there is a pressure tank involved, it is not as simple as just measuring run time length. We instead look for certain conditions:
* Major Leak: In this scenario, the main pipe from the pump or one large enough to discharge water at a rate that prevents the pressure tank from filling has failed. 
* Branch/Appliance Leak: In this scenario there is a leak in a household supply line, appliance, or more likely something connected to a hose bib (e.g. automatic waterer for livestock). The leak isn't significant enough to prevent the pressure tank from filling and cutting off the pump periodically. 
* Minor/Pinhole Leak: In this scenario there is a small leak that has a negligible effect on consumption rate and therefore is hard to detect. We do not try to handle this scenario at this point.

# Implementation
* Logs all pump on/off events to a TimescaleDB (Postgres) DB (https://docs.tigerdata.com/self-hosted/latest/install/installation-linux/)
* Monitors for a Major Leak by comparing the current run time length to a max threshold.
* Monitors for a Branch/Appliance leak by comparing total run time over a period of time.
