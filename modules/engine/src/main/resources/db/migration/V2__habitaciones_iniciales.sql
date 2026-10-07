-- Asignacion inicial termostato/switch por habitacion (antes:
-- HabitacionDAO.seedearSiVacia). Simula la configuracion que el cliente ya
-- hizo al instalar los dispositivos. Solo inserta si la tabla esta vacia.

INSERT INTO habitaciones (nombre, termostato_id, switch_id, temperatura_objetivo)
SELECT v.nombre, v.termostato_id, v.switch_id, v.temperatura_objetivo
FROM (VALUES
    ('living',     'shellyhtg3-a1b2c3d4e5f6', 'shellypro1pm-30c6f780e918', 21.0),
    ('dormitorio', 'shellyhtg3-b2c3d4e5f6a1', 'shellypro1pm-30c6f781e6bc', 20.0),
    ('cocina',     'shellyhtg3-c3d4e5f6a1b2', 'shellypro1pm-30c6f780c14c', 19.0)
) AS v(nombre, termostato_id, switch_id, temperatura_objetivo)
WHERE NOT EXISTS (SELECT 1 FROM habitaciones);
