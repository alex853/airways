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

SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: aw_airport; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_airport (
    id integer DEFAULT nextval('public.aw_airport_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    iata character varying(3),
    icao character varying(4) NOT NULL,
    name character varying(50),
    latitude real NOT NULL,
    longitude real NOT NULL,
    dataset smallint NOT NULL
);


ALTER TABLE public.aw_airport OWNER TO postgres;

--
-- Name: aw_airport2city_id_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.aw_airport2city_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER TABLE public.aw_airport2city_id_seq OWNER TO postgres;

--
-- Name: aw_airport2city; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_airport2city (
    id integer DEFAULT nextval('public.aw_airport2city_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    airport_id integer NOT NULL,
    city_id integer NOT NULL,
    dataset smallint NOT NULL
);


ALTER TABLE public.aw_airport2city OWNER TO postgres;

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
-- Name: aw_city; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city (
    id integer DEFAULT nextval('public.aw_city_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    country_id integer NOT NULL,
    name character varying(50) NOT NULL,
    latitude real NOT NULL,
    longitude real NOT NULL,
    population integer NOT NULL,
    dataset smallint NOT NULL
);


ALTER TABLE public.aw_city OWNER TO postgres;

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
-- Name: aw_city2city_flow; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city2city_flow (
    id integer DEFAULT nextval('public.aw_city2city_flow_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    from_flow_id integer NOT NULL,
    to_flow_id integer NOT NULL,
    heartbeat_dt timestamp without time zone,
    active boolean NOT NULL,
    units real NOT NULL,
    percentage real NOT NULL,
    availability real NOT NULL,
    next_group_size smallint NOT NULL,
    accumulated_flow real NOT NULL,
    accumulated_flow_dt timestamp without time zone NOT NULL
);


ALTER TABLE public.aw_city2city_flow OWNER TO postgres;

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
-- Name: aw_city2city_flow_stats; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city2city_flow_stats (
    id integer DEFAULT nextval('public.aw_city2city_flow_stats_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    c2c_flow_id integer NOT NULL,
    date date NOT NULL,
    heartbeat_dt timestamp without time zone,
    availability_before real NOT NULL,
    availability_after real NOT NULL,
    availability_delta real NOT NULL,
    no_tickets integer NOT NULL,
    tickets_bought integer NOT NULL,
    travelled integer NOT NULL
);


ALTER TABLE public.aw_city2city_flow_stats OWNER TO postgres;

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
-- Name: aw_city_flow; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_city_flow (
    id integer DEFAULT nextval('public.aw_city_flow_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    city_id integer NOT NULL,
    heartbeat_dt timestamp without time zone,
    status smallint NOT NULL,
    last_redistribution_dt timestamp without time zone,
    attraction real,
    units_threshold real,
    default_availability real
);


ALTER TABLE public.aw_city_flow OWNER TO postgres;

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
-- Name: aw_country; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_country (
    id integer DEFAULT nextval('public.aw_country_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    name character varying(50) NOT NULL,
    code character varying(2) NOT NULL
);


ALTER TABLE public.aw_country OWNER TO postgres;

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
-- Name: aw_event_log_entry; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_event_log_entry (
    id integer DEFAULT nextval('public.aw_event_log_entry_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    dt timestamp without time zone NOT NULL,
    primary_id character varying(30) NOT NULL,
    msg character varying(100) NOT NULL,
    secondary_id_1 character varying(30),
    secondary_id_2 character varying(30),
    secondary_id_3 character varying(30)
);


ALTER TABLE public.aw_event_log_entry OWNER TO postgres;

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
-- Name: aw_journey; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_journey (
    id integer DEFAULT nextval('public.aw_journey_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    c2c_flow_id integer NOT NULL,
    from_city_id integer NOT NULL,
    to_city_id integer NOT NULL,
    group_size smallint NOT NULL,
    status smallint NOT NULL,
    heartbeat_dt timestamp without time zone,
    expiration_dt timestamp without time zone,
    current_city_id integer,
    current_airport_id integer
);


ALTER TABLE public.aw_journey OWNER TO postgres;

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
-- Name: aw_person; Type: TABLE; Schema: public; Owner: postgres; Tablespace: 
--

CREATE TABLE public.aw_person (
    id integer DEFAULT nextval('public.aw_person_id_seq'::regclass) NOT NULL,
    version smallint NOT NULL,
    type smallint NOT NULL,
    status smallint NOT NULL,
    heartbeat_dt timestamp without time zone,
    name character varying(20) NOT NULL,
    surname character varying(20) NOT NULL,
    sex character varying(1) NOT NULL,
    origin_city_id integer NOT NULL,
    current_city_id integer,
    current_journey_id integer
);


ALTER TABLE public.aw_person OWNER TO postgres;

--
-- Name: pk_aw_airport; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_airport
    ADD CONSTRAINT pk_aw_airport PRIMARY KEY (id);


--
-- Name: pk_aw_airport2city; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_airport2city
    ADD CONSTRAINT pk_aw_airport2city PRIMARY KEY (id);


--
-- Name: pk_aw_city; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city
    ADD CONSTRAINT pk_aw_city PRIMARY KEY (id);


--
-- Name: pk_aw_city2city_flow; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city2city_flow
    ADD CONSTRAINT pk_aw_city2city_flow PRIMARY KEY (id);


--
-- Name: pk_aw_city2city_flow_stats; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city2city_flow_stats
    ADD CONSTRAINT pk_aw_city2city_flow_stats PRIMARY KEY (id);


--
-- Name: pk_aw_city_flow; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city_flow
    ADD CONSTRAINT pk_aw_city_flow PRIMARY KEY (id);


--
-- Name: pk_aw_country; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_country
    ADD CONSTRAINT pk_aw_country PRIMARY KEY (id);


--
-- Name: pk_aw_event_log_entry; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_event_log_entry
    ADD CONSTRAINT pk_aw_event_log_entry PRIMARY KEY (id);


--
-- Name: pk_aw_journey; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT pk_aw_journey PRIMARY KEY (id);


--
-- Name: pk_aw_person; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT pk_aw_person PRIMARY KEY (id);


--
-- Name: uq_c2c_flow_id_date; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city2city_flow_stats
    ADD CONSTRAINT uq_c2c_flow_id_date UNIQUE (c2c_flow_id, date);


--
-- Name: uq_city_id; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city_flow
    ADD CONSTRAINT uq_city_id UNIQUE (city_id);


--
-- Name: uq_from_to_flows_id; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_city2city_flow
    ADD CONSTRAINT uq_from_to_flows_id UNIQUE (from_flow_id, to_flow_id);


--
-- Name: uq_iata; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_airport
    ADD CONSTRAINT uq_iata UNIQUE (iata);


--
-- Name: uq_icao; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_airport
    ADD CONSTRAINT uq_icao UNIQUE (icao);


--
-- Name: uq_name; Type: CONSTRAINT; Schema: public; Owner: postgres; Tablespace: 
--

ALTER TABLE ONLY public.aw_country
    ADD CONSTRAINT uq_name UNIQUE (name);


--
-- Name: aw_person_heartbeat_dt; Type: INDEX; Schema: public; Owner: postgres; Tablespace: 
--

CREATE INDEX aw_person_heartbeat_dt ON public.aw_person USING btree (heartbeat_dt);


--
-- Name: idx_aw_city2city_flow_heartbeat_dt; Type: INDEX; Schema: public; Owner: postgres; Tablespace: 
--

CREATE INDEX idx_aw_city2city_flow_heartbeat_dt ON public.aw_city2city_flow USING btree (heartbeat_dt);


--
-- Name: idx_aw_city2city_flow_stats_heartbeat_dt; Type: INDEX; Schema: public; Owner: postgres; Tablespace: 
--

CREATE INDEX idx_aw_city2city_flow_stats_heartbeat_dt ON public.aw_city2city_flow_stats USING btree (heartbeat_dt);


--
-- Name: idx_aw_journey_heartbeat_dt; Type: INDEX; Schema: public; Owner: postgres; Tablespace: 
--

CREATE INDEX idx_aw_journey_heartbeat_dt ON public.aw_journey USING btree (heartbeat_dt);


--
-- Name: fk_airport_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_airport2city
    ADD CONSTRAINT fk_airport_id FOREIGN KEY (airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fk_c2c_flow_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city2city_flow_stats
    ADD CONSTRAINT fk_c2c_flow_id FOREIGN KEY (c2c_flow_id) REFERENCES public.aw_city2city_flow(id);


--
-- Name: fk_c2c_flow_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fk_c2c_flow_id FOREIGN KEY (c2c_flow_id) REFERENCES public.aw_city2city_flow(id);


--
-- Name: fk_city_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_airport2city
    ADD CONSTRAINT fk_city_id FOREIGN KEY (city_id) REFERENCES public.aw_city(id);


--
-- Name: fk_city_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city_flow
    ADD CONSTRAINT fk_city_id FOREIGN KEY (city_id) REFERENCES public.aw_city(id);


--
-- Name: fk_country_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city
    ADD CONSTRAINT fk_country_id FOREIGN KEY (country_id) REFERENCES public.aw_country(id);


--
-- Name: fk_current_airport_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fk_current_airport_id FOREIGN KEY (current_airport_id) REFERENCES public.aw_airport(id);


--
-- Name: fk_current_city_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fk_current_city_id FOREIGN KEY (current_city_id) REFERENCES public.aw_city(id);


--
-- Name: fk_current_city_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT fk_current_city_id FOREIGN KEY (current_city_id) REFERENCES public.aw_city(id);


--
-- Name: fk_current_journey_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT fk_current_journey_id FOREIGN KEY (current_journey_id) REFERENCES public.aw_journey(id);


--
-- Name: fk_from_city_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fk_from_city_id FOREIGN KEY (from_city_id) REFERENCES public.aw_city(id);


--
-- Name: fk_from_flow_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city2city_flow
    ADD CONSTRAINT fk_from_flow_id FOREIGN KEY (from_flow_id) REFERENCES public.aw_city_flow(id);


--
-- Name: fk_origin_city_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_person
    ADD CONSTRAINT fk_origin_city_id FOREIGN KEY (origin_city_id) REFERENCES public.aw_city(id);


--
-- Name: fk_to_city_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_journey
    ADD CONSTRAINT fk_to_city_id FOREIGN KEY (to_city_id) REFERENCES public.aw_city(id);


--
-- Name: fk_to_flow_id; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.aw_city2city_flow
    ADD CONSTRAINT fk_to_flow_id FOREIGN KEY (to_flow_id) REFERENCES public.aw_city_flow(id);


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

