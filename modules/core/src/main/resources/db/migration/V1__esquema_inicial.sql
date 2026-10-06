-- Esquema inicial de EcoWarm. Es el mismo DDL que hasta ahora creaban
-- HabitacionDAO y LecturaDAO del modulo subscriber; desde la Iteracion 4 lo
-- gestiona core con Flyway.
--
-- Bases ya existentes (creadas por el subscriber): Flyway las toma como
-- baseline en la version 1 (spring.flyway.baseline-on-migrate) y NO vuelve a
-- ejecutar este script, asi que el DDL tiene que seguir siendo identico.

CREATE TABLE IF NOT EXISTS habitaciones (
    id                    SERIAL       PRIMARY KEY,
    nombre                VARCHAR(50)  NOT NULL UNIQUE,
    termostato_id         VARCHAR(50)  NOT NULL UNIQUE,
    switch_id             VARCHAR(50)  NOT NULL UNIQUE,
    temperatura_objetivo  NUMERIC(4,1)
);

CREATE TABLE IF NOT EXISTS lecturas (
    id             SERIAL         PRIMARY KEY,
    habitacion_id  INTEGER        NOT NULL REFERENCES habitaciones(id),
    temperatura_c  NUMERIC(5,2)   NOT NULL,
    temperatura_f  NUMERIC(5,2)   NOT NULL,
    epoch_mili     BIGINT         NOT NULL
);
