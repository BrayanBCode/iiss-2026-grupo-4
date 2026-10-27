# Estándar de codificación

El código Java sigue el **[Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)**
en su variante **AOSP** (sangría de 4 espacios en vez de 2). El formato no se discute en las
revisiones: lo aplica una herramienta.

## Herramienta: Spotless + google-java-format

Configurado en el [`pom.xml`](../pom.xml) padre, vale para `core` y `engine`:

- `google-java-format` 1.28.0, estilo `AOSP`, con `reflowLongStrings` (parte los strings largos).
- `removeUnusedImports`: borra los imports que no se usan.

```bash
mvn spotless:apply    # formatea todo el código
mvn spotless:check    # solo verifica (no modifica)
mvn verify            # compila, corre los tests y falla si algo no está formateado
```

Antes de commitear, correr `mvn spotless:apply`. En IntelliJ conviene instalar el plugin
**google-java-format** y activarlo con estilo *Android Open Source Project (AOSP)*, así el
formateo del IDE coincide con el del build.

## Lo que hace el formateador

- Sangría de 4 espacios; continuaciones de línea con 8.
- Largo máximo de línea: 100 columnas.
- Imports en un único bloque ordenado, sin comodines ni imports sin uso.
- Javadoc reacomodado a 100 columnas, con `<p>` entre párrafos.

## Lo que el formateador no hace (y hay que respetar a mano)

- **Llaves siempre**, aunque el `if`/`for` tenga una sola línea.
- **Nombres**: clases en `UpperCamelCase`, métodos y variables en `lowerCamelCase`, constantes en
  `UPPER_SNAKE_CASE`, paquetes en minúscula.
- **Idioma**: el dominio y los nombres van en español (`Habitacion`, `alRecibirLectura`); los
  sufijos técnicos del framework, en inglés (`Controller`, `Exception`, `Config`).
- **Nombres calificados**: usar `import` en lugar de escribir `java.util.List` en el código.

## Comentarios

- Javadoc corto en cada clase pública y en los métodos públicos que no se explican solos.
- Los comentarios explican el **por qué** (una decisión, una restricción del estándar), no repiten
  lo que el código ya dice.
- Referenciar el estándar de interoperabilidad por sección (`§2`, `§5.2`) en vez de copiar su texto.
- Si un cambio deja un comentario desactualizado, se corrige en el mismo commit.

## Commits

Formato `módulo: descripción en minúscula`, por ejemplo `core: decidir() pública y con la situación
por parámetro` o `engine: comunicación con los switches en un hilo propio`.
