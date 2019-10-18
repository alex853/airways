-- Airways 0.1.0 - Airways tables
insert into database_update(id) values('0.2.0-airways');


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_country_id_seq;

CREATE TABLE aw_country (
    id integer DEFAULT nextval('aw_country_id_seq'::regclass) NOT NULL CONSTRAINT aw_country_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    name character varying(50) NOT NULL CONSTRAINT aw_country_name_key UNIQUE,
    code character varying(2) NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city_id_seq;

CREATE TABLE aw_city (
    id integer DEFAULT nextval('aw_city_id_seq'::regclass) NOT NULL CONSTRAINT aw_city_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    country_id integer NOT NULL CONSTRAINT aw_city_country_id_fkey REFERENCES aw_country(id),
    name character varying(50) NOT NULL,
    latitude real NOT NULL,
    longitude real NOT NULL,
    population integer NOT NULL,
    dataset smallint NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_airport_id_seq;

CREATE TABLE aw_airport (
    id integer DEFAULT nextval('aw_airport_id_seq'::regclass) NOT NULL CONSTRAINT aw_airport_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    iata character varying(3) CONSTRAINT aw_airport_iata_key UNIQUE,
    icao character varying(4) NOT NULL CONSTRAINT aw_airport_icao_key UNIQUE,
    name character varying(50),
    latitude real NOT NULL,
    longitude real NOT NULL,
    dataset smallint NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_airport2city_id_seq;

CREATE TABLE aw_airport2city (
    id integer DEFAULT nextval('aw_airport2city_id_seq'::regclass) NOT NULL CONSTRAINT aw_airport2city_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    airport_id integer NOT NULL CONSTRAINT aw_airport2city_airport_id_fkey REFERENCES aw_airport(id),
    city_id integer NOT NULL CONSTRAINT aw_airport2city_city_id_fkey REFERENCES aw_city(id),
    dataset smallint NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city_flow_id_seq;

CREATE TABLE aw_city_flow (
    id integer DEFAULT nextval('aw_city_flow_id_seq'::regclass) NOT NULL CONSTRAINT aw_city_flow_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,
    heartbeat_dt timestamp without time zone,

    city_id integer NOT NULL CONSTRAINT aw_city_flow_city_id_key UNIQUE CONSTRAINT aw_city_flow_city_id_fkey REFERENCES aw_city(id),
    status smallint NOT NULL,
    last_redistribution_dt timestamp without time zone,
    attraction real,
    units_threshold real,
    default_availability real,
    mobility real
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city2city_flow_id_seq;

CREATE TABLE aw_city2city_flow (
    id integer DEFAULT nextval('aw_city2city_flow_id_seq'::regclass) NOT NULL CONSTRAINT aw_city2city_flow_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,
    heartbeat_dt timestamp without time zone,

    from_flow_id integer NOT NULL CONSTRAINT aw_city2city_flow_from_flow_id_fkey REFERENCES aw_city_flow(id),
    to_flow_id integer NOT NULL CONSTRAINT aw_city2city_flow_to_flow_id_fkey REFERENCES aw_city_flow(id),
    active boolean NOT NULL,
    units real NOT NULL,
    percentage real NOT NULL,
    availability real NOT NULL,
    next_group_size smallint NOT NULL,
    accumulated_flow real NOT NULL,
    accumulated_flow_dt timestamp without time zone NOT NULL,

    CONSTRAINT aw_city2city_flow_from_to_flows_id_key UNIQUE (from_flow_id, to_flow_id)
);

CREATE INDEX aw_city2city_flow_heartbeat_dt_idx ON aw_city2city_flow (heartbeat_dt);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city2city_flow_stats_id_seq;

CREATE TABLE aw_city2city_flow_stats (
    id integer DEFAULT nextval('aw_city2city_flow_stats_id_seq'::regclass) NOT NULL CONSTRAINT aw_city2city_flow_stats_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,
    heartbeat_dt timestamp without time zone,

    c2c_flow_id integer NOT NULL CONSTRAINT aw_city2city_flow_stats_c2c_flow_id_fkey REFERENCES aw_city2city_flow(id),
    date date NOT NULL,
    availability_before real NOT NULL,
    availability_after real NOT NULL,
    availability_delta real NOT NULL,
    no_tickets integer NOT NULL,
    tickets_bought integer NOT NULL,
    travelled integer NOT NULL,
    CONSTRAINT aw_city2city_flow_stats_c2c_flow_id_date_key UNIQUE (c2c_flow_id, date)
);

CREATE INDEX aw_city2city_flow_stats_heartbeat_dt_idx ON aw_city2city_flow_stats (heartbeat_dt);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_journey_id_seq;

CREATE TABLE aw_journey (
    id integer DEFAULT nextval('aw_journey_id_seq'::regclass) NOT NULL CONSTRAINT aw_journey_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    c2c_flow_id integer NOT NULL CONSTRAINT aw_journey_c2c_flow_id_fkey REFERENCES aw_city2city_flow(id),
    from_city_id integer NOT NULL CONSTRAINT aw_journey_from_city_id_fkey REFERENCES aw_city(id),
    to_city_id integer NOT NULL CONSTRAINT aw_journey_to_city_id_fkey REFERENCES aw_city(id),
    group_size smallint NOT NULL,

    status smallint NOT NULL,
    status_dt timestamp without time zone,

    itinerary_id integer, -- foreign key will be created later
    transfer_id integer -- foreign key will be created later
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_person_id_seq;

CREATE TABLE aw_person (
    id integer DEFAULT nextval('aw_person_id_seq'::regclass) NOT NULL CONSTRAINT aw_person_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    type smallint NOT NULL,

    status smallint NOT NULL,
    status_dt timestamp without time zone,

    name character varying(20) NOT NULL,
    surname character varying(20) NOT NULL,
    sex character varying(1) NOT NULL,
    origin_city_id integer NOT NULL CONSTRAINT aw_person_origin_city_id_fkey REFERENCES aw_city(id),
    journey_id integer CONSTRAINT aw_person_current_journey_id_fkey REFERENCES aw_journey(id),
    location_city_id integer CONSTRAINT aw_person_location_city_id_fkey REFERENCES aw_city(id),
    location_airport_id integer CONSTRAINT aw_person_location_airport_id_fkey REFERENCES aw_airport(id)
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_aircraft_type_id_seq;

CREATE TABLE aw_aircraft_type (
    id integer DEFAULT nextval('aw_aircraft_type_id_seq'::regclass) NOT NULL CONSTRAINT aw_aircraft_type_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    icao character varying(4) NOT NULL CONSTRAINT aw_aircraft_type_icao_key UNIQUE,
    iata character varying(3) NOT NULL CONSTRAINT aw_aircraft_type_iata_key UNIQUE,
    typical_cruise_altitude integer NOT NULL,
    typical_cruise_speed smallint NOT NULL,
    climb_vertical_speed smallint NOT NULL,
    descent_vertical_speed smallint NOT NULL,
    takeoff_speed smallint NOT NULL,
    landing_speed smallint NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_airline_id_seq;

CREATE TABLE aw_airline (
    id integer DEFAULT nextval('aw_airline_id_seq'::regclass) NOT NULL CONSTRAINT aw_airline_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    icao character varying(3) NOT NULL CONSTRAINT aw_airline_icao_key UNIQUE,
    iata character varying(2) NOT NULL CONSTRAINT aw_airline_iata_key UNIQUE,
    name character varying(30) NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_timetable_row_id_seq;

CREATE TABLE aw_timetable_row (
    id integer DEFAULT nextval('aw_timetable_row_id_seq'::regclass) NOT NULL CONSTRAINT aw_timetable_row_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,
    heartbeat_dt timestamp without time zone,

    airline_id integer NOT NULL CONSTRAINT aw_timetable_row_airline_id_fkey REFERENCES aw_airline(id),
    number character varying(7) NOT NULL,
    from_airport_id integer NOT NULL CONSTRAINT aw_timetable_row_from_airport_id_fkey REFERENCES aw_airport(id),
    to_airport_id integer NOT NULL CONSTRAINT aw_timetable_row_to_airport_id_fkey REFERENCES aw_airport(id),
    aircraft_type_id integer NOT NULL CONSTRAINT aw_timetable_row_aircraft_type_id_fkey REFERENCES aw_aircraft_type(id),
    weekdays character varying(7) NOT NULL,
    departure_time character varying(5) NOT NULL,
    duration character varying(5) NOT NULL,

    status smallint NOT NULL,
    status_dt timestamp without time zone,

    total_tickets smallint NOT NULL,
    horizon smallint
);

CREATE INDEX aw_timetable_row_heartbeat_dt_idx ON aw_timetable_row (heartbeat_dt);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_flight_id_seq;

CREATE TABLE aw_flight (
    id integer DEFAULT nextval('aw_flight_id_seq'::regclass) NOT NULL CONSTRAINT aw_flight_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    date_of_flight date NOT NULL,
    callsign character varying(10) NOT NULL,
    aircraft_type_id integer NOT NULL CONSTRAINT aw_flight_aircraft_type_id_fkey REFERENCES aw_aircraft_type(id),

    transport_flight_id integer, -- foreign key will be created later
    flight_number character varying(10),

    from_airport_id integer CONSTRAINT aw_flight_from_airport_id_fkey REFERENCES aw_airport(id),
    to_airport_id integer CONSTRAINT aw_flight_to_airport_id_fkey REFERENCES aw_airport(id),

    scheduled_departure_time timestamp without time zone,
    actual_departure_time timestamp without time zone,
    scheduled_takeoff_time timestamp without time zone,
    actual_takeoff_time timestamp without time zone,
    scheduled_landing_time timestamp without time zone,
    actual_landing_time timestamp without time zone,
    scheduled_arrival_time timestamp without time zone,
    actual_arrival_time timestamp without time zone,

    status smallint NOT NULL,
    status_dt timestamp without time zone
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_transport_flight_id_seq;

CREATE TABLE aw_transport_flight (
    id integer DEFAULT nextval('aw_transport_flight_id_seq'::regclass) NOT NULL CONSTRAINT aw_transport_flight_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    timetable_row_id integer CONSTRAINT aw_transport_flight_timetable_row_id_fkey REFERENCES aw_timetable_row(id),
    flight_id integer CONSTRAINT aw_transport_flight_flight_id_fkey REFERENCES aw_flight(id),

    date_of_flight date NOT NULL,
    flight_number character varying(10),

    from_airport_id integer NOT NULL CONSTRAINT aw_transport_flight_from_airport_id_fkey REFERENCES aw_airport(id),
    to_airport_id integer NOT NULL CONSTRAINT aw_transport_flight_to_airport_id_fkey REFERENCES aw_airport(id),

    departure_dt timestamp without time zone NOT NULL,
    arrival_dt timestamp without time zone NOT NULL,

    status smallint NOT NULL,
    status_dt timestamp without time zone,

    total_tickets integer NOT NULL,
    free_tickets integer NOT NULL
);

ALTER TABLE aw_flight
  ADD CONSTRAINT aw_flight_transport_flight_id_fkey FOREIGN KEY (transport_flight_id)
    REFERENCES aw_transport_flight (id);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_pilot_id_seq;

CREATE TABLE aw_pilot (
    id integer DEFAULT nextval('aw_pilot_id_seq'::regclass) NOT NULL CONSTRAINT aw_pilot_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    status smallint NOT NULL,
    status_dt timestamp without time zone,

    person_id integer NOT NULL CONSTRAINT aw_pilot_person_id_fkey REFERENCES aw_person(id)
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_aircraft_id_seq;

CREATE TABLE aw_aircraft (
    id integer DEFAULT nextval('aw_aircraft_id_seq'::regclass) NOT NULL CONSTRAINT aw_aircraft_id_fkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    aircraft_type_id integer NOT NULL CONSTRAINT aw_aircraft_aircraft_type_id_fkey REFERENCES aw_aircraft_type(id),
    reg_no character varying(10) NOT NULL CONSTRAINT aw_aircraft_reg_no_key UNIQUE,
    airline_id integer CONSTRAINT aw_aircraft_airline_id_fkey REFERENCES aw_airline(id),

    status smallint NOT NULL,
    status_dt timestamp without time zone,

    location_latitude real,
    location_longitude real,
    location_airport_id integer CONSTRAINT aw_aircraft_position_airport_id_fkey REFERENCES aw_airport(id)
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_aircraft_assignment_id_seq;

CREATE TABLE aw_aircraft_assignment (
    id integer DEFAULT nextval('aw_aircraft_assignment_id_seq'::regclass) NOT NULL CONSTRAINT aw_aircraft_assignment_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    flight_id integer NOT NULL CONSTRAINT aw_aircraft_assignment_flight_id_fkey REFERENCES aw_flight(id),
    aircraft_id integer NOT NULL CONSTRAINT aw_aircraft_assignment_aircraft_id_fkey REFERENCES aw_aircraft(id),

    status smallint NOT NULL,
    status_dt timestamp without time zone
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_pilot_assignment_id_seq;

CREATE TABLE aw_pilot_assignment (
    id integer DEFAULT nextval('aw_pilot_assignment_id_seq'::regclass) NOT NULL CONSTRAINT aw_pilot_assignment_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    flight_id integer NOT NULL CONSTRAINT aw_pilot_assignment_flight_id_fkey REFERENCES aw_flight(id),
    pilot_id integer NOT NULL CONSTRAINT aw_pilot_assignment_pilot_id_fkey REFERENCES aw_pilot(id),
    role character varying(20),

    status smallint NOT NULL,
    status_dt timestamp without time zone
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_event_log_entry_id_seq;

CREATE TABLE aw_event_log_entry (
    id integer NOT NULL DEFAULT nextval('aw_event_log_entry_id_seq'::regclass) NOT NULL CONSTRAINT aw_event_log_entry_id_pkey PRIMARY KEY,
    version smallint,

    dt timestamp without time zone NOT NULL,
    primary_id character varying(30) NOT NULL,
    msg character varying(100) NOT NULL,
    secondary_id_1 character varying(30),
    secondary_id_2 character varying(30),
    secondary_id_3 character varying(30)
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_journey_itinerary_id_seq;

CREATE TABLE aw_journey_itinerary (
    id integer NOT NULL DEFAULT nextval('aw_journey_itinerary_id_seq'::regclass) NOT NULL CONSTRAINT aw_journey_itinerary_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    create_dt timestamp without time zone NOT NULL,
    modify_dt timestamp without time zone NOT NULL,

    journey_id integer NOT NULL CONSTRAINT aw_journey_itinerary_journey_id_fkey REFERENCES aw_journey (id),
    item_order integer NOT NULL,
    flight_id integer NOT NULL CONSTRAINT aw_journey_itinerary_flight_id_fkey REFERENCES aw_transport_flight (id)
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_journey_transfer_id_seq;

CREATE TABLE aw_journey_transfer (
  id integer NOT NULL DEFAULT nextval('aw_journey_transfer_id_seq'::regclass) NOT NULL CONSTRAINT aw_journey_transfer_id_pkey PRIMARY KEY,
  version smallint NOT NULL,
  create_dt timestamp without time zone NOT NULL,
  modify_dt timestamp without time zone NOT NULL,

  journey_id integer NOT NULL CONSTRAINT aw_journey_transfer_journey_id_fkey REFERENCES aw_journey (id),
  to_city_id integer CONSTRAINT aw_journey_transfer_to_city_id_fkey REFERENCES aw_city(id),
  to_airport_id integer CONSTRAINT aw_journey_transfer_to_airport_id_fkey REFERENCES aw_airport(id),
  distance real NOT NULL,
  duration smallint NOT NULL,
  on_started_status smallint,
  on_finished_status smallint,
  on_finished_event character varying(255)
);



------------------------------------------------------------------------------------------------------------------------
ALTER TABLE aw_journey
  ADD CONSTRAINT aw_journey_itinerary_id_fkey FOREIGN KEY (itinerary_id)
    REFERENCES aw_journey_itinerary (id);

ALTER TABLE aw_journey
  ADD CONSTRAINT aw_journey_transfer_id_fkey FOREIGN KEY (transfer_id)
    REFERENCES aw_journey_transfer (id);


------------------------------------------------------------------------------------------------------------------------
update database_update set completed = true where id = '0.2.0-airways';
