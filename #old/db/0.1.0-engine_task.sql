-- Airways 0.1.0 - Process Engine table
insert into database_update(id) values('0.1.0-engine_task');


------------------------------------------------------------------------------------------------------------------------
CREATE SEQUENCE engine_task_id_seq;

CREATE TABLE engine_task(
    id integer NOT NULL DEFAULT nextval('engine_task_id_seq'::regclass) CONSTRAINT engine_task_id_pkey PRIMARY KEY,
    version smallint NOT NULL,

    status smallint NOT NULL,
    retrycount smallint NOT NULL,
    tasktime timestamp without time zone,
    processorclassname character varying(255) NOT NULL,
    entityclassname character varying(255) NOT NULL,
    entityid integer NOT NULL,
    expirytime timestamp without time zone
);

CREATE INDEX engine_task_tasktime_idx ON engine_task(tasktime);


------------------------------------------------------------------------------------------------------------------------
update database_update set completed = true where id = '0.1.0-engine_task';
