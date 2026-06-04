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





###BusyBirds generator logics
// constantly running process which finds some, few, not too many W or J journeys in looking for tickets status
// and pick them up - mark them as 'special processing'
// save info into dedicated storage and sets expiration date - lets start from 24 hours
// that means 'mission', or 'job', or 'order'
// when mission expires without being picked up by any pilot, the journey is released back to 'normal processing'

// the screen with available missions shows list of missions, from-to airport,
// offered aircraft and its proposed full itinerary, including total flight time,
// plus mission cost, and pilot's paycheck

// the pilot can take the mission, this means pilot commits to make all the flights for the mission within 48 hours window
// all the flight missions are created, pilot can specify and change departure time of any mission
// departure time of passenger flight influences to journey's checkin time
// checkin time and boarding time can be shortened due to small airplane size
// comparison of planned and actual departure & arrival times can be used as a measure for kind of bonus or something else

// "back to base" flight can be omitted *** this will be reviewed later...... there is an obligation to fly back to base
// however if someone flies two missions in a row, this "back to base" flight and then positioning flight is weird
// on the other hand, if someone drops, leaves plane unattended for days or weeks, this should be avoided and punished

// so, the mission has execution time window (48 hours), the airplane should be returned back to the base by end
// of the window, if it is not returned - cancel the mission, pay for flown and charge the fine for non-flown leg
// on the other hand, if the "passenger" flight is already flown, this aircraft can be assigned to other missions,
// including by other pilots
// if such aircraft is assigned to another mission, the previous mission is considered finished and payments can be done

// mission cancellation.....
