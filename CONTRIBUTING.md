# Cómo colaborar en Marketradar

La rúbrica del curso evalúa la gestión con Git en las cuatro fases, así que cada cambio debe quedar trazable por integrante.

## Reglas obligatorias

1. **Nunca use force push** (`git push --force`, `-f` ni `--force-with-lease`). Si hay un conflicto, resuélvalo con un merge o avise al equipo.
2. **No trabaje directamente en `main`.** Cree una rama por funcionalidad y súbala con un pull request.
3. **Cada pull request lo revisa otro integrante** antes de fusionarlo.
4. **Commits con su propia identidad.** Configure su nombre y correo reales de GitHub antes de hacer commits y no agregue líneas `Co-authored-by` de herramientas de IA.
5. **Nunca suba credenciales.** Las claves van en el archivo `.env`, que está en `.gitignore`. Use `.env.example` como plantilla.

## Ramas

| Rama | Uso |
| --- | --- |
| `main` | Versión estable, siempre compila y pasa las pruebas |
| `feature/<tema>` | Una funcionalidad, por ejemplo `feature/adaptador-aliexpress` |
| `fix/<tema>` | Corrección de un error |
| `docs/<tema>` | Documentación de una fase, por ejemplo `docs/fase-2-arquitectura` |

## Mensajes de commit

Escríbalos en español, en presente y empezando por un verbo: qué hace el commit, no qué hizo usted.

- Bien: `Agrega DAO de ofertas`, `Corrige el orden por precio total`, `Documenta el diagrama de clases`
- Mal: `cambios`, `arreglos varios`, `final final`

Haga commits pequeños: un cambio con sentido propio por commit.

## Flujo desde la interfaz web de GitHub

1. En la página del repositorio, abra el selector de ramas (dice `main`), escriba el nombre de su rama nueva, por ejemplo `feature/frontend-busqueda`, y elija **Create branch**.
2. Con su rama seleccionada, edite o suba archivos (**Add file → Upload files** o el ícono de lápiz). En **Commit changes** escriba un mensaje claro y elija **Commit directly to the feature/... branch**.
3. Cuando termine, GitHub muestra el aviso **Compare & pull request**. Ábralo, llene la plantilla y asigne como revisor a otro integrante.
4. El revisor deja comentarios o aprueba con **Review changes → Approve**.
5. Cuando la verificación automática (CI) esté en verde y haya aprobación, fusione con **Merge pull request**.

## Antes de abrir un pull request

- `mvn -f backend/pom.xml verify` pasa sin errores.
- Las clases nuevas tienen Javadoc y nombres en español con las convenciones de Java.
- Actualizó [docs/tareas.md](docs/tareas.md) si cerró una tarea.
