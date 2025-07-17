# Installation

_Kuvasz_ is distributed as a [**Docker image**](https://hub.docker.com/r/kuvaszmonitoring/kuvasz){target="blank"}, which makes it easy to deploy and run on any system that supports _Docker_.
This guide will walk you through the installation process.

## Prerequisites

Whatever your setup will be, you'll need two things to make _Kuvasz_ work:

### PostgreSQL

_Kuvasz_ relies on a _PostgreSQL_ database to store its data, but **if you don't have one** set up already,
you can use the provided _Docker compose_ file to easily set up a _PostgreSQL_ instance alongside _Kuvasz_.

!!! info

    The **minimum, tested version is 12**, `alpine` distributions are supported.

### YAML configuration file

While you can run _Kuvasz_ without a configuration file using a very minimal setup, it probably won't make much sense,
because you won't be able to set up any integrations, for example. Fortunately, you just need a simple _YAML_ file,
which
is mounted as a volume in the _Docker_ container under `/config/kuvasz.yml`.

From now on we'll assume that **you have a `.yaml` file** somewhere on your machine where you would like to deploy _Kuvasz_.
**Watch out!** You'll probably need to adjust the volume mapping in the example below.

For the sake of simplicity, **you can start with an empty file** and we'll go through the available configuration
options later in the process, or you can take a look at the **[Configuration](configuration.md)** section of the
documentation right now to see how you can set up integrations, app-level settings, or even your monitors there.

## Quick start with Docker Compose

=== "With PostgreSQL"

    ```yaml
    services:
      kuvasz-db:
        image: postgres:17-alpine
        container_name: kuvaszdb
        environment:
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres # (1)!
          TZ: 'UTC' # (7)!
        ports:
          - "5432:5432"
        volumes:
          - kuvasz-db-data:/var/lib/postgresql/data # (2)!
      kuvasz:
        image: kuvaszmonitoring/kuvasz:latest
        container_name: kuvasz
        mem_limit: 384M # optional (3)
        ports:
          - "8080:8080"
        environment:
          TZ: 'UTC' # (8)!
          DATABASE_HOST: kuvaszdb # (4)!
          DATABASE_USER: postgres # (5)!
          DATABASE_PASSWORD: postgres # (9)!
          ADMIN_USER: YourSuperSecretUsername # change it
          ADMIN_PASSWORD: YourSuperSecretPassword # change it
          ADMIN_API_KEY: ThisShouldBeVeryVerySecureToo # change it
        volumes:
          - /path/to/your/kuvasz.yml:/config/kuvasz.yml # (6)!
        depends_on:
          - kuvasz-db
    volumes:
      kuvasz-db-data:
    ```

    1.  Better to use a secure one, it's up to you
    2.  You can change this to a bind mount on your host, but make sure it's writable
    3.  This is the recommended memory limit, the tested minimum is 256MB
    4.  Use the container name from the PostgreSQL service above
    5.  Use the same user and password as in the PostgreSQL service above
    6.  Make sure your config file is readable!
    7.  Optional, but recommended, use your own timezone
    8.  Optional, but recommended, match it with the PostgreSQL service above
    9.  Use the same password as in the PostgreSQL service above

=== "Without PostgreSQL"

    ```yaml
    services:
      kuvasz:
        image: kuvaszmonitoring/kuvasz:latest
        container_name: kuvasz
        mem_limit: 384M # optional (1)
        ports:
          - "8080:8080"
        environment:
          TZ: 'UTC' # (2)!
          DATABASE_HOST: localhost # optional, default is `localhost`
          DATABASE_PORT: 5432 # optional, default is 5432
          DATABASE_NAME: postgres # optional, default is `postgres`
          DATABASE_USER: postgres # use your own
          DATABASE_PASSWORD: postgres # use your own
          ADMIN_USER: YourSuperSecretUsername # change it
          ADMIN_PASSWORD: YourSuperSecretPassword # change it
          ADMIN_API_KEY: ThisShouldBeVeryVerySecureToo # change it
        volumes:
          - /path/to/your/kuvasz.yml:/config/kuvasz.yml # (3)!
    ```

    1.  This is the recommended memory limit, the tested minimum is 256MB
    2.  Optional, but recommended, use your own timezone
    3.  Make sure your config file is readable!

!!! tip "Disabling authentication"

    If you would like to completely **disable authentication**, you should set the `ENABLE_AUTH` environment variable to `false` and then you can just simply omit `ADMIN_USER`, `ADMIN_PASSWORD`, and `ADMIN_API_KEY`.

## After a successful start

If you've done everything correctly, and you've started the specified _Compose_ stack, you should be able to access the
web UI of _Kuvasz_ at
[`http://0.0.0.0:8080`](http://0.0.0.0:8080){target="_blank"} (or the port you specified).

!!! tip

    If you run _Kuvasz_ on a **remote server**, you should **replace** `0.0.0.0` with the server's IP address or your custom domain name.

If you didn't disable authentication, you should see the login page, where you can log in with the credentials you specified. Otherwise, you should be redirected to the dashboard of _Kuvasz_.

### Setting up integrations (a.k.a "Notifications") <!-- md:config integrations.md -->

Setting up integrations is as simple as adding a few lines to your _YAML_ configuration. You can find the available options in the
[**Integrations setup**](../setup/integrations.md) section of the documentation.

Setting up _Slack_ as a global notification channel for all of your monitors, for example, would look like this:

```yaml
integrations:
  slack:
    - name: use_your_desired_name
      webhook-url: 'https://hooks.slack.com/services/T00000000/B00000000/XXXXXXXXXXXXXXXX'
      global: true # (1)!
```

1.  If you set `global: true`, this integration will be used for all monitors by default, even if they don't have a specific integration assigned to them.

!!! tip

    If you modify your configuration (via _YAML_ or _ENV_, it doesn't matter), you need to restart the _Kuvasz_ container for the changes to take effect. In certain cases if you changed an environment variable, **you might need to rebuild the container** as well.

### Creating your first monitor <!-- md:config managing-monitors.md -->

You have 3 options to [**manage your monitors**](managing-monitors.md):

- using the **Web UI**, which is probably the most user-friendly way
- using the **REST API**, which is more suitable for automation and integration with other systems
- using the **YAML configuration**, which is useful if you would like to handle all of your configurations as code

## Other installation methods

If you use another container orchestration system (e.g. _k8s_, _Swarm_, etc.), you can still use the same image and the
same configuration options, of course. Just make sure to set the environment variables and mount the configuration file
as shown above.

## Readiness/health probes

If you run _Kuvasz_ in a container orchestration system, you can use the `GET /api/v1/health` endpoint as a readiness probe to check if the application is UP and running. The endpoint **doesn't need authentication**, and returns a simple JSON response with the status of the application.

```json
{
  "status": "UP"
}
```

!!! tip 

    Besides the response body, the HTTP status code will also indicate the health of the application: **non 2xx status codes** indicate that the application is **not healthy**.

## Upgrading from v1 to v2

If you're upgrading from _Kuvasz v1_ to _Kuvasz v2_, it's better if you just **start with a fresh setup**, except for the database (make sure that you do a backup of it), which should be backward compatible. 
Even if it's not a complete rewrite, a lot of things have changed under the hood, and the new version is not fully compatible with the old one.

All in all, you can use your old database, **your data will be migrated automatically**, but **there are a few notable breaking changes** you should be aware of:

- _Kuvasz_ is not distributed as a **native** (GraalVM based) Docker image anymore
- The minimum required _PostgreSQL_ version is now **12**
- The [**REST API**](../features/api.md) is versioned now, and a few **endpoints have been changed or removed**. You can find the new API documentation [here](../api-doc.md)
- [**Integrations**](integrations.md) are now configured via the _YAML_ file, and the old, environment-variable-based configuration is no longer supported
- The **authentication** and its configuration **has been simplified**, read the [**Authentication**](../setup/configuration.md#authentication) section carefully!

!!! warning

    This list **is not exhaustive**, there might be other - minor - breaking changes that are not listed here.

    Detailed upgrade notes (if necessary) for the future releases will be available in a dedicated section of the documentation.  
