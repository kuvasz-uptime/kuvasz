# Contributing Code

If you are interested in contributing to the project and are looking for issues to work on, first take a look at the open issues.

## JDK Setup

Kuvasz currently requires JDK 21

## IDE Setup

Kuvasz can be imported into IntelliJ IDEA by opening the `build.gradle.kts` file.

## Docker Setup

Kuvasz tests currently require docker to be installed, because they are relying on Testcontainers.
 
## Running Tests

To run the tests use `./gradlew check`. 

## Working on the code base

The most important command you will have to run before sending your changes is the check command.

./gradlew check

For a successful contribution, all tests should be green and there shouldn't be any issue in detekt.

## Changing the DB schema

_Kuvasz_ uses [Flyway](https://flywaydb.org/) for database migrations and [jOOQ](https://www.jooq.org/) for the type-safe database access, and the convenience plugins to manage changes are set up in the `model` submodule.

The `app` itself takes care of migrating the database schema on startup, but the generated jOOQ classes are not automatically updated when the database schema changes.

This means that if you want to change the database schema, you will have to:

- create a new migration script in the `model/src/main/resources/db/migration` directory (you have to follow Flyway's naming conventions, e.g. `V1__Initial.sql`, `V2__Add_new_table.sql`, etc.)
- make sure that you don't change the existing migration scripts, as this would break the migration history (even a whitespace change would break it!)
- run the `flywayMigrate` Gradle task to apply the migration to your local dev database
- run the `generateJooq` Gradle task to regenerate the jOOQ classes based on the new database schema

Instead of the last two steps, you can just run the custom `migrateAndGenerate` Gradle task, which is a convenience task that runs both `flywayMigrate` and `generateJooq` in sequence.

## Creating a pull request

Once you are satisfied with your changes:

- Commit your changes in your local branch
- Push your changes to your remote branch on GitHub
- Send a [pull request](https://help.github.com/articles/creating-a-pull-request)
