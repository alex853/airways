CREATE TABLE database_update (
  id character varying(50) NOT NULL CONSTRAINT database_update_id_pkey PRIMARY KEY,
  dt timestamp without time zone NOT NULL DEFAULT current_timestamp,
  completed boolean NOT NULL DEFAULT false
);

insert into database_update(id) values('0.0.0-database_update');

update database_update set completed = true where id = '0.0.0-database_update';
