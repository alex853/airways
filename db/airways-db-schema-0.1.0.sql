--
-- PostgreSQL database dump
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: 
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: aw_aircraft; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_aircraft (
    id integer NOT NULL,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone,
    reg_no character varying(255),
    status integer,
    version integer,
    airline_id integer,
    aircraft_type_id integer,
    location_latitude double precision,
    location_longitude double precision,
    location_airport_id integer
);


ALTER TABLE public.aw_aircraft OWNER TO postgres;

--
-- Name: aw_aircraft_assignment; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_aircraft_assignment (
    id bigint NOT NULL,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone,
    status integer,
    version integer,
    aircraft_id integer,
    flight_id integer
);


ALTER TABLE public.aw_aircraft_assignment OWNER TO postgres;

--
-- Name: aw_aircraft_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_aircraft_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_aircraft_assignment_id_seq OWNER TO postgres;

--
-- Name: aw_aircraft_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_aircraft_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_aircraft_id_seq OWNER TO postgres;

--
-- Name: aw_aircraft_type; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_aircraft_type (
    id integer NOT NULL,
    climb_vertical_speed integer,
    descent_vertical_speed integer,
    iata character varying(255),
    icao character varying(255),
    landing_speed integer,
    takeoff_speed integer,
    typical_cruise_altitude integer,
    typical_cruise_speed integer,
    version integer
);


ALTER TABLE public.aw_aircraft_type OWNER TO postgres;

--
-- Name: aw_aircraft_type_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_aircraft_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_aircraft_type_id_seq OWNER TO postgres;

--
-- Name: aw_airline; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_airline (
    id integer NOT NULL,
    create_dt timestamp without time zone,
    iata character varying(255),
    icao character varying(255),
    modify_dt timestamp without time zone,
    name character varying(255),
    version integer
);


ALTER TABLE public.aw_airline OWNER TO postgres;

--
-- Name: aw_airline_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_airline_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_airline_id_seq OWNER TO postgres;

--
-- Name: aw_airport; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_airport (
    id integer NOT NULL,
    dataset integer,
    iata character varying(255),
    icao character varying(255),
    latitude double precision,
    longitude double precision,
    name character varying(255),
    version integer
);


ALTER TABLE public.aw_airport OWNER TO postgres;

--
-- Name: aw_airport2city; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_airport2city (
    id integer NOT NULL,
    dataset integer,
    version integer,
    airport_id integer,
    city_id integer
);


ALTER TABLE public.aw_airport2city OWNER TO postgres;

--
-- Name: aw_airport_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_airport_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_airport_id_seq OWNER TO postgres;

--
-- Name: aw_city; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city (
    id integer NOT NULL,
    dataset integer,
    latitude double precision,
    longitude double precision,
    name character varying(255),
    population integer,
    version integer,
    country_id integer
);


ALTER TABLE public.aw_city OWNER TO postgres;

--
-- Name: aw_city2city_flow; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city2city_flow (
    id integer NOT NULL,
    accumulated_flow double precision,
    accumulated_flow_dt timestamp without time zone,
    active boolean,
    availability double precision,
    heartbeat_dt timestamp without time zone,
    next_group_size integer,
    percentage double precision,
    units double precision,
    version integer,
    from_flow_id integer,
    to_flow_id integer
);


ALTER TABLE public.aw_city2city_flow OWNER TO postgres;

--
-- Name: aw_city2city_flow_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_city2city_flow_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_city2city_flow_id_seq OWNER TO postgres;

--
-- Name: aw_city2city_flow_stats; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city2city_flow_stats (
    id integer NOT NULL,
    availability_after double precision,
    availability_before double precision,
    availability_delta double precision,
    date date,
    heartbeat_dt timestamp without time zone,
    no_tickets integer,
    tickets_bought integer,
    travelled integer,
    version integer,
    c2c_flow_id integer
);


ALTER TABLE public.aw_city2city_flow_stats OWNER TO postgres;

--
-- Name: aw_city2city_flow_stats_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_city2city_flow_stats_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_city2city_flow_stats_id_seq OWNER TO postgres;

--
-- Name: aw_city_flow; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city_flow (
    id integer NOT NULL,
    attraction double precision,
    default_availability double precision,
    heartbeat_dt timestamp without time zone,
    last_redistribution_dt timestamp without time zone,
    status integer,
    units_threshold double precision,
    version integer,
    city_id integer
);


ALTER TABLE public.aw_city_flow OWNER TO postgres;

--
-- Name: aw_city_flow_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_city_flow_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_city_flow_id_seq OWNER TO postgres;

--
-- Name: aw_city_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_city_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_city_id_seq OWNER TO postgres;

--
-- Name: aw_country; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_country (
    id integer NOT NULL,
    code character varying(255),
    name character varying(255),
    version integer
);


ALTER TABLE public.aw_country OWNER TO postgres;

--
-- Name: aw_country_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_country_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_country_id_seq OWNER TO postgres;

--
-- Name: aw_event_log_entry; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_event_log_entry (
    id integer NOT NULL,
    dt timestamp without time zone,
    msg character varying(255),
    primary_id character varying(255),
    secondary_id_1 character varying(255),
    secondary_id_2 character varying(255),
    secondary_id_3 character varying(255),
    version integer
);


ALTER TABLE public.aw_event_log_entry OWNER TO postgres;

--
-- Name: aw_event_log_entry_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_event_log_entry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_event_log_entry_id_seq OWNER TO postgres;

--
-- Name: aw_flight; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_flight (
    id integer NOT NULL,
    actual_arrival_time timestamp without time zone,
    actual_departure_time timestamp without time zone,
    actual_landing_time timestamp without time zone,
    actual_takeoff_time timestamp without time zone,
    callsign character varying(255),
    create_dt timestamp without time zone,
    date_of_flight date,
    heartbeat_dt timestamp without time zone,
    modify_dt timestamp without time zone,
    number character varying(255),
    scheduled_arrival_time timestamp without time zone,
    scheduled_departure_time timestamp without time zone,
    scheduled_landing_time timestamp without time zone,
    scheduled_takeoff_time timestamp without time zone,
    status integer,
    status_dt timestamp without time zone,
    version integer,
    aircraft_type_id integer,
    alternative_airport_id integer,
    from_airport_id integer,
    to_airport_id integer,
    transport_flight_id integer
);


ALTER TABLE public.aw_flight OWNER TO postgres;

--
-- Name: aw_flight_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_flight_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_flight_id_seq OWNER TO postgres;

--
-- Name: aw_journey; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_journey (
    id integer NOT NULL,
    group_size integer,
    status integer,
    version integer,
    c2c_flow_id integer,
    from_city_id integer,
    to_city_id integer,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone,
    itinerary_id integer,
    transfer_id integer
);


ALTER TABLE public.aw_journey OWNER TO postgres;

--
-- Name: aw_journey_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_journey_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_journey_id_seq OWNER TO postgres;

--
-- Name: aw_journey_itinerary; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_journey_itinerary (
    id integer NOT NULL,
    create_dt timestamp without time zone,
    item_order integer,
    modify_dt timestamp without time zone,
    version integer,
    flight_id integer,
    journey_id integer
);


ALTER TABLE public.aw_journey_itinerary OWNER TO postgres;

--
-- Name: aw_journey_itinerary_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_journey_itinerary_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_journey_itinerary_id_seq OWNER TO postgres;

--
-- Name: aw_journey_transfer; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_journey_transfer (
    id integer NOT NULL,
    create_dt timestamp without time zone,
    distance double precision,
    duration integer,
    modify_dt timestamp without time zone,
    on_finished_status integer,
    on_started_status integer,
    version integer,
    journey_id integer,
    to_airport_id integer,
    to_city_id integer,
    on_finished_event character varying(255)
);


ALTER TABLE public.aw_journey_transfer OWNER TO postgres;

--
-- Name: aw_journey_transfer_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_journey_transfer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_journey_transfer_id_seq OWNER TO postgres;

--
-- Name: aw_person; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_person (
    id integer NOT NULL,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone,
    name character varying(255),
    sex character varying(255),
    status integer,
    surname character varying(255),
    type integer,
    version integer,
    journey_id integer,
    origin_city_id integer,
    location_airport_id integer,
    location_city_id integer
);


ALTER TABLE public.aw_person OWNER TO postgres;

--
-- Name: aw_person_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_person_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_person_id_seq OWNER TO postgres;

--
-- Name: aw_pilot; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_pilot (
    id integer NOT NULL,
    heartbeat_dt timestamp without time zone,
    status integer,
    version integer,
    person_id integer,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone
);


ALTER TABLE public.aw_pilot OWNER TO postgres;

--
-- Name: aw_pilot_assignment; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_pilot_assignment (
    id bigint NOT NULL,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone,
    role character varying(255),
    status integer,
    version integer,
    flight_id integer,
    pilot_id integer
);


ALTER TABLE public.aw_pilot_assignment OWNER TO postgres;

--
-- Name: aw_pilot_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_pilot_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_pilot_assignment_id_seq OWNER TO postgres;

--
-- Name: aw_pilot_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_pilot_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_pilot_id_seq OWNER TO postgres;

--
-- Name: aw_timetable_row; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_timetable_row (
    id integer NOT NULL,
    departure_time character varying(255),
    duration character varying(255),
    heartbeat_dt timestamp without time zone,
    horizon integer,
    number character varying(255),
    status integer,
    total_tickets integer,
    version integer,
    weekdays character varying(255),
    aircraft_type_id integer,
    airline_id integer,
    from_airport_id integer,
    to_airport_id integer,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone
);


ALTER TABLE public.aw_timetable_row OWNER TO postgres;

--
-- Name: aw_timetable_row_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_timetable_row_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_timetable_row_id_seq OWNER TO postgres;

--
-- Name: aw_transport_flight; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_transport_flight (
    id integer NOT NULL,
    arrival_dt timestamp without time zone,
    date_of_flight date,
    departure_dt timestamp without time zone,
    free_tickets integer,
    number character varying(255),
    status integer,
    status_dt timestamp without time zone,
    total_tickets integer,
    version integer,
    flight_id integer,
    from_airport_id integer,
    timetable_row_id integer,
    to_airport_id integer,
    create_dt timestamp without time zone,
    modify_dt timestamp without time zone
);


ALTER TABLE public.aw_transport_flight OWNER TO postgres;

--
-- Name: aw_transport_flight_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_transport_flight_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_transport_flight_id_seq OWNER TO postgres;

--
-- Name: engine_task; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.engine_task (
    id integer NOT NULL,
    entityclassname character varying(255),
    entityid integer,
    expirytime timestamp without time zone,
    processorclassname character varying(255),
    retrycount integer,
    status integer,
    tasktime timestamp without time zone,
    version integer
);


ALTER TABLE public.engine_task OWNER TO postgres;

--
-- Name: engine_task_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.engine_task_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.engine_task_id_seq OWNER TO postgres;

--
-- Name: aw_aircraft_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_aircraft_assignment
    ADD CONSTRAINT aw_aircraft_assignment_pkey PRIMARY KEY (id);


--
-- Name: aw_aircraft_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_aircraft
    ADD CONSTRAINT aw_aircraft_pkey PRIMARY KEY (id);


--
-- Name: aw_aircraft_type_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_aircraft_type
    ADD CONSTRAINT aw_aircraft_type_pkey PRIMARY KEY (id);


--
-- Name: aw_airline_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_airline
    ADD CONSTRAINT aw_airline_pkey PRIMARY KEY (id);


--
-- Name: aw_airport2city_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_airport2city
    ADD CONSTRAINT aw_airport2city_pkey PRIMARY KEY (id);


--
-- Name: aw_airport_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_airport
    ADD CONSTRAINT aw_airport_pkey PRIMARY KEY (id);


--
-- Name: aw_city2city_flow_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city2city_flow
    ADD CONSTRAINT aw_city2city_flow_pkey PRIMARY KEY (id);


--
-- Name: aw_city2city_flow_stats_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city2city_flow_stats
    ADD CONSTRAINT aw_city2city_flow_stats_pkey PRIMARY KEY (id);


--
-- Name: aw_city_flow_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city_flow
    ADD CONSTRAINT aw_city_flow_pkey PRIMARY KEY (id);


--
-- Name: aw_city_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city
    ADD CONSTRAINT aw_city_pkey PRIMARY KEY (id);


--
-- Name: aw_country_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_country
    ADD CONSTRAINT aw_country_pkey PRIMARY KEY (id);


--
-- Name: aw_event_log_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_event_log_entry
    ADD CONSTRAINT aw_event_log_entry_pkey PRIMARY KEY (id);


--
-- Name: aw_flight_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_flight
    ADD CONSTRAINT aw_flight_pkey PRIMARY KEY (id);


--
-- Name: aw_journey_itinerary_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_journey_itinerary
    ADD CONSTRAINT aw_journey_itinerary_pkey PRIMARY KEY (id);


--
-- Name: aw_journey_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT aw_journey_pkey PRIMARY KEY (id);


--
-- Name: aw_journey_transfer_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_journey_transfer
    ADD CONSTRAINT aw_journey_transfer_pkey PRIMARY KEY (id);


--
-- Name: aw_person_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT aw_person_pkey PRIMARY KEY (id);


--
-- Name: aw_pilot_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_pilot_assignment
    ADD CONSTRAINT aw_pilot_assignment_pkey PRIMARY KEY (id);


--
-- Name: aw_pilot_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_pilot
    ADD CONSTRAINT aw_pilot_pkey PRIMARY KEY (id);


--
-- Name: aw_timetable_row_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_timetable_row
    ADD CONSTRAINT aw_timetable_row_pkey PRIMARY KEY (id);


--
-- Name: aw_transport_flight_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_transport_flight
    ADD CONSTRAINT aw_transport_flight_pkey PRIMARY KEY (id);


--
-- Name: engine_task_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.engine_task
    ADD CONSTRAINT engine_task_pkey PRIMARY KEY (id);


--
-- Name: idx_aw_person_journey_id; Type: INDEX; Schema: public; Owner: postgres; Tablespace: 
--

CREATE INDEX idx_aw_person_journey_id ON public.aw_person USING btree (journey_id);


--
-- Name: idx_engine_task_tasktime; Type: INDEX; Schema: public; Owner: postgres; Tablespace: 
--

CREATE INDEX idx_engine_task_tasktime ON public.engine_task USING btree (tasktime);


--
-- Name: fk249r8trn161iy6liv3nrtbn3y; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city2city_flow
    ADD CONSTRAINT fk249r8trn161iy6liv3nrtbn3y FOREIGN KEY (from_flow_id) REFERENCES public.aw_city_flow(id);


--
-- Name: fk38hjef3mij1wgiqd6cr0q2fte; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_pilot_assignment
    ADD CONSTRAINT fk38hjef3mij1wgiqd6cr0q2fte FOREIGN KEY (pilot_id) REFERENCES public.aw_pilot(id);


--
-- Name: fk3mnqs1qjx2sh9ptiyry9xmaxq; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_flight
    ADD CONSTRAINT fk3mnqs1qjx2sh9ptiyry9xmaxq FOREIGN KEY (alternative_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fk41huh6oh6k864t99peo1tn25w; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_timetable_row
    ADD CONSTRAINT fk41huh6oh6k864t99peo1tn25w FOREIGN KEY (airline_id) REFERENCES public.aw_airline(id);


--
-- Name: fk5uhw8jwmhr922s1x498og224o; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey_itinerary
    ADD CONSTRAINT fk5uhw8jwmhr922s1x498og224o FOREIGN KEY (flight_id) REFERENCES public.aw_transport_flight(id);


--
-- Name: fk6dbxuv9db6nkbwoo5q8fmi51b; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT fk6dbxuv9db6nkbwoo5q8fmi51b FOREIGN KEY (location_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fk7avr9ww39sqo7ulkqiqh23ob5; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fk7avr9ww39sqo7ulkqiqh23ob5 FOREIGN KEY (transfer_id) REFERENCES public.aw_journey_transfer(id);


--
-- Name: fk84v8aunifwj81j3x7la8wragk; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fk84v8aunifwj81j3x7la8wragk FOREIGN KEY (from_city_id) REFERENCES public.aw_city(id);


--
-- Name: fk94nqjgllpkk6fpk9nh2wnmcpb; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_transport_flight
    ADD CONSTRAINT fk94nqjgllpkk6fpk9nh2wnmcpb FOREIGN KEY (timetable_row_id) REFERENCES public.aw_timetable_row(id);


--
-- Name: fk9916yhnsk78wqprtoyvj0w7re; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey_transfer
    ADD CONSTRAINT fk9916yhnsk78wqprtoyvj0w7re FOREIGN KEY (to_city_id) REFERENCES public.aw_city(id);


--
-- Name: fka5xlym5o7edpdmswyax72xy5a; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey_itinerary
    ADD CONSTRAINT fka5xlym5o7edpdmswyax72xy5a FOREIGN KEY (journey_id) REFERENCES public.aw_journey(id);


--
-- Name: fkca166525c9ardjlc9rrrmocie; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_transport_flight
    ADD CONSTRAINT fkca166525c9ardjlc9rrrmocie FOREIGN KEY (flight_id) REFERENCES public.aw_flight(id);


--
-- Name: fkcljnvja7y101oai4xgaebd3dn; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT fkcljnvja7y101oai4xgaebd3dn FOREIGN KEY (journey_id) REFERENCES public.aw_journey(id);


--
-- Name: fke58f1loy3gsmv35x20xbkmmn1; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_aircraft
    ADD CONSTRAINT fke58f1loy3gsmv35x20xbkmmn1 FOREIGN KEY (location_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkepkrly52r5wj3xwt1nr6k8x2v; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_aircraft_assignment
    ADD CONSTRAINT fkepkrly52r5wj3xwt1nr6k8x2v FOREIGN KEY (flight_id) REFERENCES public.aw_flight(id);


--
-- Name: fkfbke3k8hh0dfmhr62hj5gb34p; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey_transfer
    ADD CONSTRAINT fkfbke3k8hh0dfmhr62hj5gb34p FOREIGN KEY (journey_id) REFERENCES public.aw_journey(id);


--
-- Name: fkfcbrqscmojrf40007a9j4ssj; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_timetable_row
    ADD CONSTRAINT fkfcbrqscmojrf40007a9j4ssj FOREIGN KEY (aircraft_type_id) REFERENCES public.aw_aircraft_type(id);


--
-- Name: fkfxa7p716gc6gq7bh5buiwcu5r; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_flight
    ADD CONSTRAINT fkfxa7p716gc6gq7bh5buiwcu5r FOREIGN KEY (aircraft_type_id) REFERENCES public.aw_aircraft_type(id);


--
-- Name: fkgdxucfx2ujwivne8e8aqgyli8; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT fkgdxucfx2ujwivne8e8aqgyli8 FOREIGN KEY (origin_city_id) REFERENCES public.aw_city(id);


--
-- Name: fkgl06tgkmw83yrhbt5g41v5plw; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city
    ADD CONSTRAINT fkgl06tgkmw83yrhbt5g41v5plw FOREIGN KEY (country_id) REFERENCES public.aw_country(id);


--
-- Name: fkiblnp7ogvsvd7bob7ndj0s2ym; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_transport_flight
    ADD CONSTRAINT fkiblnp7ogvsvd7bob7ndj0s2ym FOREIGN KEY (from_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkiixugsrhqhbwrgxkkdw3hbs5u; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_aircraft
    ADD CONSTRAINT fkiixugsrhqhbwrgxkkdw3hbs5u FOREIGN KEY (airline_id) REFERENCES public.aw_airline(id);


--
-- Name: fkijwtu7b7o40a7ip81pw6tp4n2; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey_transfer
    ADD CONSTRAINT fkijwtu7b7o40a7ip81pw6tp4n2 FOREIGN KEY (to_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkj3yp04l6a6rl5sfbwf17gelx5; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city_flow
    ADD CONSTRAINT fkj3yp04l6a6rl5sfbwf17gelx5 FOREIGN KEY (city_id) REFERENCES public.aw_city(id);


--
-- Name: fkjjny3waw4ivsgg1prj7cwgscv; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fkjjny3waw4ivsgg1prj7cwgscv FOREIGN KEY (c2c_flow_id) REFERENCES public.aw_city2city_flow(id);


--
-- Name: fkjqg21dastoljwexwtmknuaa01; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_flight
    ADD CONSTRAINT fkjqg21dastoljwexwtmknuaa01 FOREIGN KEY (transport_flight_id) REFERENCES public.aw_transport_flight(id);


--
-- Name: fkkxrtgj7pr11ur6jwt3s3q1aeq; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_aircraft_assignment
    ADD CONSTRAINT fkkxrtgj7pr11ur6jwt3s3q1aeq FOREIGN KEY (aircraft_id) REFERENCES public.aw_aircraft(id);


--
-- Name: fklpdcuvnf7lsikj3l3m5mf3v5d; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city2city_flow_stats
    ADD CONSTRAINT fklpdcuvnf7lsikj3l3m5mf3v5d FOREIGN KEY (c2c_flow_id) REFERENCES public.aw_city2city_flow(id);


--
-- Name: fkn2mt9qscoh7w37g9reybvkps5; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_flight
    ADD CONSTRAINT fkn2mt9qscoh7w37g9reybvkps5 FOREIGN KEY (to_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkn99wjiek8sfqrr9597a4es4g4; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_pilot
    ADD CONSTRAINT fkn99wjiek8sfqrr9597a4es4g4 FOREIGN KEY (person_id) REFERENCES public.aw_person(id);


--
-- Name: fknftspm3k4i9tfw62ijiyroyma; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_pilot_assignment
    ADD CONSTRAINT fknftspm3k4i9tfw62ijiyroyma FOREIGN KEY (flight_id) REFERENCES public.aw_flight(id);


--
-- Name: fko5v6qu6bu2o2yk2nnj3rvxgfe; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_transport_flight
    ADD CONSTRAINT fko5v6qu6bu2o2yk2nnj3rvxgfe FOREIGN KEY (to_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkosqedmdsa9seejumaqtdbkvw5; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_airport2city
    ADD CONSTRAINT fkosqedmdsa9seejumaqtdbkvw5 FOREIGN KEY (airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkpdav0atuoqh2xn20xpfkdc2mn; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fkpdav0atuoqh2xn20xpfkdc2mn FOREIGN KEY (to_city_id) REFERENCES public.aw_city(id);


--
-- Name: fkpngtt1bque6qp3lkupve9jvtc; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_timetable_row
    ADD CONSTRAINT fkpngtt1bque6qp3lkupve9jvtc FOREIGN KEY (from_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkpqqmf7mu9t8q4rea7ci2prg6v; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_timetable_row
    ADD CONSTRAINT fkpqqmf7mu9t8q4rea7ci2prg6v FOREIGN KEY (to_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkq9a9xdwywbiy87owennr90q17; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_flight
    ADD CONSTRAINT fkq9a9xdwywbiy87owennr90q17 FOREIGN KEY (from_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fkqa0y0prrg8pdfan8oy3bce79c; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_airport2city
    ADD CONSTRAINT fkqa0y0prrg8pdfan8oy3bce79c FOREIGN KEY (city_id) REFERENCES public.aw_city(id);


--
-- Name: fkqoxsuv74qplbtr04msao7ogdw; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city2city_flow
    ADD CONSTRAINT fkqoxsuv74qplbtr04msao7ogdw FOREIGN KEY (to_flow_id) REFERENCES public.aw_city_flow(id);


--
-- Name: fkqssby1o59cglyix6plksa6l3e; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_aircraft
    ADD CONSTRAINT fkqssby1o59cglyix6plksa6l3e FOREIGN KEY (aircraft_type_id) REFERENCES public.aw_aircraft_type(id);


--
-- Name: fkrhxkdhry01vh2jqrs3hkimpxh; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT fkrhxkdhry01vh2jqrs3hkimpxh FOREIGN KEY (location_city_id) REFERENCES public.aw_city(id);


--
-- Name: fks0upyiqbjhalsdj8od2gghksn; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fks0upyiqbjhalsdj8od2gghksn FOREIGN KEY (itinerary_id) REFERENCES public.aw_journey_itinerary(id);


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM postgres;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

