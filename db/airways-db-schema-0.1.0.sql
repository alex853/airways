-- Airways 0.1.0 - DB Creation Script

------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_country_id_seq;

CREATE TABLE aw_country (
    id integer DEFAULT nextval('aw_country_id_seq'::regclass) NOT NULL CONSTRAINT aw_country_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    name character varying(50) NOT NULL CONSTRAINT aw_country_name_key UNIQUE,
    code character varying(2) NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city_id_seq;

CREATE TABLE aw_city (
    id integer DEFAULT nextval('aw_city_id_seq'::regclass) NOT NULL CONSTRAINT aw_city_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
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
    airport_id integer NOT NULL CONSTRAINT aw_airport2city_airport_id_fkey REFERENCES aw_airport(id),
    city_id integer NOT NULL CONSTRAINT aw_airport2city_city_id_fkey REFERENCES aw_city(id),
    dataset smallint NOT NULL
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city_flow_id_seq;

CREATE TABLE aw_city_flow (
    id integer DEFAULT nextval('aw_city_flow_id_seq'::regclass) NOT NULL CONSTRAINT aw_city_flow_id_pkey PRIMARY KEY,
    version smallint NOT NULL,
    city_id integer NOT NULL CONSTRAINT aw_city_flow_city_id_key UNIQUE CONSTRAINT aw_city_flow_city_id_fkey REFERENCES aw_city(id),
    heartbeat_dt timestamp without time zone,
    status smallint NOT NULL,
    last_redistribution_dt timestamp without time zone,
    attraction real,
    units_threshold real,
    default_availability real
);


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city2city_flow_id_seq;

CREATE TABLE aw_city2city_flow (
                                   id integer DEFAULT nextval('aw_city2city_flow_id_seq'::regclass) NOT NULL CONSTRAINT pk_aw_city2city_flow PRIMARY KEY,
                                   version smallint NOT NULL,
                                   from_flow_id integer NOT NULL CONSTRAINT fk_from_flow_id REFERENCES aw_city_flow(id),
                                   to_flow_id integer NOT NULL CONSTRAINT fk_to_flow_id REFERENCES aw_city_flow(id),
                                   heartbeat_dt timestamp without time zone,
                                   active boolean NOT NULL,
                                   units real NOT NULL,
                                   percentage real NOT NULL,
                                   availability real NOT NULL,
                                   next_group_size smallint NOT NULL,
                                   accumulated_flow real NOT NULL,
                                   accumulated_flow_dt timestamp without time zone NOT NULL,
                                   CONSTRAINT uq_from_to_flows_id UNIQUE (from_flow_id, to_flow_id)
);

CREATE INDEX idx_aw_city2city_flow_heartbeat_dt ON aw_city2city_flow (heartbeat_dt);



------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE aw_city2city_flow_stats_id_seq;

CREATE TABLE aw_city2city_flow_stats (
                                         id integer DEFAULT nextval('aw_city2city_flow_stats_id_seq'::regclass) NOT NULL CONSTRAINT pk_aw_city2city_flow_stats PRIMARY KEY,
                                         version smallint NOT NULL,
                                         c2c_flow_id integer NOT NULL CONSTRAINT fk_c2c_flow_id REFERENCES aw_city2city_flow(id),
                                         date date NOT NULL,
                                         heartbeat_dt timestamp without time zone,
                                         availability_before real NOT NULL,
                                         availability_after real NOT NULL,
                                         availability_delta real NOT NULL,
                                         no_tickets integer NOT NULL,
                                         tickets_bought integer NOT NULL,
                                         travelled integer NOT NULL,
                                         CONSTRAINT uq_c2c_flow_id_date UNIQUE (c2c_flow_id, date)
);

CREATE INDEX idx_aw_city2city_flow_stats_heartbeat_dt ON aw_city2city_flow_stats (heartbeat_dt);
