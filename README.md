# Airways Project
Economy simulation of transportation by air for flight simmers. Cities generate some flow of passengers, 
passengers look for tickets, airlines open flights between airports, pilots take aircrafts and fly flights. 
There will be computer-driven pilots and human-driven pilots. Later it will support flights in VATSIM. 
Simmer will be able to declare some flight from A to B and passengers will buy tickets and board on 
the flight. Simmer will gain some money for completion of the flight. Stay with the project!

##Next Steps
Pilot App

###Two types of pilots
There are two types of pilots in the system - player-controlled (player character) 
and computer-controlled (non-player character). A player controls a player-controlled pilot.

Player-controlled pilot can be managed by player primarily. In general the system can not 
freely assign player-controlled pilot to a flight.

###Pilot App
Pilot App is the tool for a player to control pilot-controlled pilot. It works in following way: 
1. a player selects a flight and assigns his pilot to the flight, it will vacate NPC-pilot if any assigned
1. a player starts flight, does preflight activities, commands starting of boarding, etc
1. all these actions substitute PilotOnDuty activity logics
1. in future, the app will use VATSIM/IVAO connection
1. in future, the app will allow to declare any flight between any airport

