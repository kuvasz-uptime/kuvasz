# Contributing Code

If you are interested in contributing to the project and are looking for issues to work on, first take a look at the open issues.

## JDK Setup

Kuvasz currently requires JDK 17

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

## Creating a pull request

Once you are satisfied with your changes:

- Commit your changes in your local branch
- Push your changes to your remote branch on GitHub
- Send a [pull request](https://help.github.com/articles/creating-a-pull-request)
