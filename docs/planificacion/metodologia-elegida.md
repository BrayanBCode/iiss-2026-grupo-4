# Metodología de trabajo: Scrumban

Trabajamos con **Scrumban**, como pide la consigna desde la iteración 4. Combina la planificación por iteración de Scrum con el tablero de flujo continuo de Kanban. Nos sirve porque el alcance cambia seguido (nuevas versiones del simulador, observaciones del docente), y el tablero nos deja ver en todo momento qué está haciendo cada uno.

## Planificación al inicio de cada iteración
Al empezar una iteración el equipo se reúne y:

1. Lee la consigna y define el **objetivo** de la iteración y sus entregables.
2. Divide el trabajo en **tarjetas** chicas. Cada tarjeta tiene un criterio de aceptación, sus dependencias y un tamaño:
   - S: menos de medio día;
   - M: alrededor de un día;
   - L: más de un día. Una tarjeta L que bloquearía el tablero varios días se parte.
3. Toma las **decisiones** que afectan a varias tarjetas, por ejemplo de diseño o de contrato con el simulador. Así nadie arranca una tarjeta con dudas abiertas.
4. Deja explícito lo que queda **fuera de alcance** de la iteración.

Las tarjetas, sus dependencias y su estado se llevan en el tablero de Jira (enlace en el README). Cómo atendimos las observaciones del docente en la iteración 5 está registrado en [`revision-docente.md`](revision-docente.md).

## Tablero

El tablero se lleva en Jira. El sitio de Atlassian tiene un dominio mal nombrado (`estudiantes-grupo8-2026`) porque aloja los espacios de dos materias; el proyecto del grupo 4 es G1234 "Grupo4_2026_Ing".

| Backlog | Listo | En curso | Revisión | Hecho |
|---|---|---|---|---|
| Tarjetas pendientes | Dependencias cerradas y decisiones tomadas | Con responsable | PR abierto, esperando revisión | Mergeado en la rama de la iteración |

### Políticas
- **Límite de trabajo en curso (WIP):**
  - como máximo **2 tarjetas en curso** en todo el equipo;
  - **1 sola** entre las que tocan los mismos archivos. Así se terminan las tarjetas antes de empezar otras y se evitan conflictos de merge.
- **Listo:** una tarjeta pasa a "Listo" cuando sus dependencias están en "Hecho" y las decisiones que la afectan están tomadas.
- **Hecho:** una tarjeta está hecha cuando:
  - `mvn verify` pasa (formato, tests y cobertura del core ≥ 85 %);
  - otro integrante revisó el PR;
  - el commit sigue el formato `módulo: descripción`;
  - los comentarios y la documentación afectados están actualizados.
- **Pull:** nadie asigna tarjetas. Quien termina una toma la siguiente de "Listo", respetando el orden de dependencias.
- **Ramas:** una rama por tarjeta (`<iteración>/<id>-<tema>`), con PR hacia la rama de la iteración.

## Revisión semanal
Una vez por semana se muestra el avance al Product Owner (el docente):

- el estado del tablero y lo que pasó a "Hecho";
- una demo o el diff de lo más relevante;
- las decisiones que necesitan su validación.

Con lo que surge se **ajusta el alcance**: se agregan, se parten o se postergan tarjetas para la iteración siguiente.

## Cierre de la iteración
Cuando todas las tarjetas del alcance están en "Hecho":

- se actualizan los documentos de la entrega;
- se crea el tag `versionN`;
- se sube el `.zip` a Moodle.
