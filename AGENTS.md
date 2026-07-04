# Tech stack

- Kotlin 2.4+
- Micronaut 5
- DB: jOOQ + PostgreSQL, Flyway
- UI: SSR HTML via kotlinx.html + Alpine.js and htmx for interactivity
- Gradle
- Tests & static code analysis: Kotest, Testcontainers, Mockk, Detekt, kover
- UI tests: Playwright (under `app/src/uiTest`)
- Documentation: mkdocs-material under /docs

# Key rules for contributing to the project

- always follow the structures, naming conventions, approaches used in the project
- always write tests (the same or similar way how they are written already) to cover the new features, bug fixes, etc. Regular tests are written with Kotest, Testcontainers and Mockk, UI tests are written with Playwright
- never push or commit automatically anything
- never build or push a docker image (not even with an explicit permission)
- always extend/adjust the documentation under /docs (kuvasz-latest.yml with the OpenAPI spec is auto-generated, no need to worry about) if necessary. This includes configuration properties, new features, etc.
- when multiple DB migrations would be created as part of a new changeset, squash them together, but never touch the existing ones that were already committed, as they supposed to be immutable
- proper test coverage is really important, make sure that every possible new line is covered with meaningful tests, rely on the kover test coverage reports/checks
- i18n files live in `shared/src/main/i18n/com/kuvaszuptime/kuvasz/i18n`

# Important commands

- `./gradlew check` - runs all checks, including tests, code formatting, linting, etc. Needs to be executed before work is considered as done.
- `./gradlew detektAll` - runs only the static code analysis tool on all modules
- `./gradlew migrateAndGenerate` - runs the database migrations and generates the jOOQ boilerplate code
- `./gradlew validateI18n` - validates the i18n files, needs to be run after any change in the i18n files
- `./gradlew app:run` - runs the application which will be available on http://localhost:8080
- `./gradlew app:validateJsonSchemas` - validates the JSON schemas used in the project
- `./gradlew app:uiTest` - Runs the UI test suite

# Code style guidelines

- Use `/** ... **/` ONLY over methods and classes, for inline comments use `// ...`
- Be sparse with comments in general, only make one when it's really necessary to explain something that is not obvious from the code itself
- `DSLContext` should be reused from
  `Database*Spec` base classes when it's possible, instead of injecting a new one into the class
- Tests should not depend on each other (except the existing cases and where it is really necessary)
- Manual clearing/resetting of mocks should be avoided (unless it's really necessary), instead use
  `@MockkBean` and let the framework handle it
