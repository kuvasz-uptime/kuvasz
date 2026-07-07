# Contributing Code

If you are interested in contributing to the project and are looking for issues to work on, first take a look at the open issues.

## JDK Setup

Kuvasz currently requires JDK 25

## IDE Setup

Kuvasz can be imported into IntelliJ IDEA by opening the `build.gradle.kts` file.

## Docker Setup

Kuvasz tests currently require docker to be installed, because they are relying on Testcontainers.
 
## Running Tests

To run the tests use `./gradlew check`. 

## Localization

Please refer to the corresponding section of the documentation: [**Localization**](https://kuvasz-uptime.dev/localization).

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

## On the use of AI

AI-assisted development is a reality, and we are not against it. You are welcome to use AI tools to help you write code, as long as **you** remain the author of the contribution in every sense that matters. That means you understand what the code does, you can explain the decisions behind it, and you can maintain and defend it during review.

What we do care about is that there is a real, engaged human on the other side of every pull request. AI can help you write code, but it cannot own the contribution, participate in a discussion, or take responsibility for the result - that part is on you.

To keep the review process healthy and respectful of everyone's time, please keep the following in mind:

- **Write your own PR description.** The description should be written by you and genuinely explain what the change does and why. AI-generated descriptions tend to be verbose, generic, and add noise instead of signal.
- **Understand what you submit.** Even if AI helped you produce the changes, you should fully understand them and be able to answer questions about them on your own during the review.
- **Do your own reviewing.** The code review is a conversation between humans. Please participate in it personally - do not delegate writing review comments or replies to an AI agent.

### When a PR might be rejected without a review

To set clear expectations, a pull request may be closed or rejected **without a detailed review** if any of the following apply:

- The PR description is clearly AI-generated, missing, or does not meaningfully explain the change.
- The author is unable or unwilling to answer questions about their own changes during the review.
- Review comments or replies are written by an AI agent instead of the author.
- The changes appear to be an unreviewed AI dump: large, unfocused, or with no evidence that the author understands or has tested them.

None of this is meant to discourage you from contributing - quite the opposite. We simply want every contribution to come from, and be backed by, a real person who cares about the project.
