# Deployment

_Kuvasz_ is distributed as a [**Docker image**](https://hub.docker.com/r/kuvaszmonitoring/kuvasz){target="blank"}, which makes it easy to deploy and run on any system that supports _Docker_.

This guide will walk you through the deployment process.

## Choose Your Deployment Method

Kuvasz can be deployed in several ways depending on your infrastructure:

[**Docker Compose**](#quick-start-with-docker-compose) - Recommended for quick start and simple deployments
[**Helm Chart**](./helm-deployment) - Recommended for Kubernetes clusters
[**Other Methods**](#other-deployment-methods) - For other container orchestration systems

!!! info "PostgreSQL"

    _Kuvasz_ relies on a _PostgreSQL_ database to store its data, but **if you don't have one** set up already,
    you can use the provided _Docker compose_ file to easily set up a _PostgreSQL_ instance alongside _Kuvasz_.
    The Helm chart also provides a basic database setup, if you don't want to bring your own PostgreSQL instance (be aware that it might not be a good fit for a production setup).

    The **minimum, tested version of _PostgreSQL_ is 14**, `alpine` distributions are supported.

## Quick start with Docker Compose

### 1. Configuration file

Create a file called `kuvasz.yml` somewhere on your machine, where you will create your Docker Compose file too in the next step.

For the sake of simplicity, **you can start with an empty file** and we'll go through the available configuration
options later in the process, or you can take a look at the **[Configuration](configuration.md)** section of the
documentation right now to see how you can set up integrations, app-level settings, or even your monitors there.

### 2. Docker Compose file

Create a file called `docker-compose.yml` in the same directory where you created the `kuvasz.yml` file in the previous step, and add the following content to it. Please **make sure to change the credentials** (see the comments below) to secure ones!

```yaml
services:
  kuvasz-db: # (7)!
    image: postgres:18-alpine
    container_name: kuvaszdb
    environment:
      POSTGRES_USER: kuvasz
      POSTGRES_PASSWORD: YourSuperSecretDbPassword # change it!
      TZ: 'UTC' # (4)!
    healthcheck:
      test: ["CMD", "pg_isready", "-U", "kuvasz"]
      interval: 10s
      start_period: 30s
    volumes:
      - kuvasz-db-data:/var/lib/postgresql
  kuvasz:
    image: kuvaszmonitoring/kuvasz:latest
    # platform: linux/arm64 # (8)
    container_name: kuvasz
    ports:
      - "8080:8080" # (9)!
    environment:
      TZ: 'UTC' # (5)!
      DATABASE_HOST: kuvaszdb # (1)!
      DATABASE_USER: kuvasz # (2)!
      DATABASE_PASSWORD: YourSuperSecretDbPassword # (6)!
      ADMIN_USER: YourSuperSecretUsername # change it
      ADMIN_PASSWORD: YourSuperSecretPassword # change it
      ADMIN_API_KEY: ThisShouldBeVeryVerySecureToo # change it
    volumes:
      - ./kuvasz.yml:/config/kuvasz.yml # (3)!
    healthcheck:
      test: ["CMD-SHELL", "wget --quiet --tries=1 --spider http://localhost:8080/api/v2/health || exit 1"]
      interval: 60s
      start_period: 30s
    depends_on:
      - kuvasz-db
volumes:
  kuvasz-db-data:
```

1. Use the container name from the PostgreSQL service above 
2. Use the same user and password as in the PostgreSQL service above
3. Make sure your config file is readable and the mount path is correct (`/config/kuvasz.yml`)
4. Optional, but recommended, use your own timezone
5. Optional, but recommended, match it with the PostgreSQL service above
6. Use the same password as in the PostgreSQL service above
7. You can omit this service if you already have a PostgreSQL instance running somewhere, but in this case make sure to adjust the connection details accordingly
8. If you plan to run Kuvasz on an ARM based system, you might need to uncomment this line, depending on your setup
9. If the port `8080` is already in use on your host machine, you can change the left side of the mapping to any other free port (e.g. `9090:8080`)

!!! important "Credential requirements"

    - `ADMIN_PASSWORD` must be at least **12 characters** and must **not be equal** to `ADMIN_USER`.
    - `ADMIN_API_KEY` must be at least **16 characters**.

    See the details in the [Configuration](configuration.md#credentials).

!!! tip "Disabling authentication"

    If you would like to completely **disable authentication**, you should set the `ENABLE_AUTH` environment variable to `false` and then you can just simply omit `ADMIN_USER`, `ADMIN_PASSWORD`, and `ADMIN_API_KEY`.

### 3. Starting the stack

Run the following command in the same directory where you created all the files mentioned above:

```bash
docker compose up -d
```

If you've done everything correctly, you should be able to access the
web UI of _Kuvasz_ at
[`http://0.0.0.0:8080`](http://0.0.0.0:8080){target="_blank"} (or the port you specified).

!!! tip

    If you run _Kuvasz_ on a **remote server**, you should **replace** `0.0.0.0` with the server's IP address or your custom domain name.

If you didn't disable authentication, you should see the login page, where you can log in with the credentials you specified. Otherwise, you should be redirected to the dashboard of _Kuvasz_.

## After a successful start

### Setting up integrations (a.k.a "Notifications") <!-- md:config ../management/integrations.md -->

Setting up integrations is as simple as adding a few lines to your _YAML_ configuration. You can find the available options in the
[**Integrations setup**](../management/integrations.md) section of the documentation.

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

### Creating your first monitor <!-- md:config ../management/managing-monitors/index.md -->

You have 3 options to [**manage your monitors**](../management/managing-monitors/index.md):

- using the **Web UI**, which is probably the most user-friendly way
- using the **REST API**, which is more suitable for automation and integration with other systems
- using the **YAML configuration**, which is useful if you would like to handle all of your configurations as code

## Keeping _Kuvasz_ up-to-date

To update _Kuvasz_ to the latest version, you just need to **bump the version of your image** and restart your container, if no other instructions were provided in the release notes.
Furthermore, to make it easier to get notified about new releases, the UI will show a notification if a new version is available. 

!!!question "Not using the Web UI?"

    If you don't use the Web UI, you can also check for new releases on GitHub, or directly on the [API](https://api-docs.kuvasz-uptime.dev){target="blank"} of _Kuvasz_, under `GET /api/v2/settings`. You'll find the version related information in the response under the `versionInfo` key.

## Readiness/health probes

If you run _Kuvasz_ in a container orchestration system, you can use the `GET /api/v2/health` endpoint as a readiness probe to check if the application is UP and running. The endpoint **doesn't need authentication**, and returns a simple JSON response with the status of the application.

```json
{
  "status": "UP"
}
```

!!! tip

    Besides the response body, the HTTP status code will also indicate the health of the application: **non 2xx status codes** indicate that the application is **not healthy**.

## Other deployment methods

### Kubernetes with Helm

For Kubernetes deployments, we provide an official Helm chart. This is the recommended method for deploying Kuvasz to Kubernetes clusters as it handles all the configuration complexity for you.
See the [Helm Chart Deployment Guide](./helm-deployment) for detailed instructions.

### Other Container Orchestration Systems

If you use another container orchestration system (e.g. _Kubernetes without Helm_, _Docker Swarm_, etc.), you can still use the same image and the same configuration options. Just make sure to set the environment variables and mount the configuration file as shown in the Docker Compose example above.

## Unofficial guides

!!!warning

    The guides linked below are **created and maintained by community members** and are **not official documentation**. They may contain opinions, inaccuracies, or outdated information. Use them at your own discretion.
    
    For official information, please **always refer to the project’s documentation** or source code.

- [How to install Kuvasz on your Synology NAS](https://mariushosting.com/how-to-install-kuvasz-on-your-synology-nas/){target="_blank" }
- [Install Kuvasz via Docker (French)](https://belginux.com/installer-kuvasz-avec-docker/){target="_blank" }
- [Install Kuvasz on Debian 12 (Spanish)](https://voidnull.es/instalacion-de-kuvasz-uptime-en-debian-12/){target="_blank" }
