![Kuvasz](https://github.com/kuvasz-uptime/kuvasz/raw/main/docs/kuvasz_banner.png)

[![CI](https://github.com/kuvasz-uptime/kuvasz/actions/workflows/main.yml/badge.svg)](https://github.com/kuvasz-uptime/kuvasz/actions/workflows/main.yml)
[![codecov](https://codecov.io/gh/kuvasz-uptime/kuvasz/branch/main/graph/badge.svg)](https://codecov.io/gh/kuvasz-uptime/kuvasz)
[![CodeFactor](https://www.codefactor.io/repository/github/kuvasz-uptime/kuvasz/badge)](https://www.codefactor.io/repository/github/kuvasz-uptime/kuvasz)
![Latest Release](https://badgen.net/github/release/kuvasz-uptime/kuvasz)
[![DockerHub](https://badgen.net/badge/docker/hub/blue?icon=docker)](https://hub.docker.com/r/kuvaszmonitoring/kuvasz)

---

## ℹ️ What is Kuvasz?

Kuvasz is a **headless uptime monitor service**, which means that it is able to watch all of your precious websites and notifies you if something bad happens to them.

### Where does the name come from?

Kuvasz (pronounce as [ˈkuvɒs]) is an ancient hungarian breed of livestock & guard dog. You can read more about them on [Wikipedia](https://en.wikipedia.org/wiki/Kuvasz).

### Features

- Uptime & latency monitoring with a configurable interval
- SSL certification monitoring (once a day)
- Email notifications through SMTP
- Slack notifications through webhoooks
- Telegram notifications through the Bot API
- [PagerDuty integration](https://github.com/kuvasz-uptime/kuvasz/blob/main/docs/Integrating-with-PagerDuty.md) with automatic incident resolution
- Configurable data retention period

### Future ideas 🚧

- Regular Lighthouse audits for your websites

## ⚡️  Quick start guide

### Requirements

- You have **a running PostgreSQL instance** (Preferably 12+)

### Starting Kuvasz

The quickest way to spin up an instance of Kuvasz is something like this:

```shell
docker run -p 8080:8080 \
-e ADMIN_USER=admin \
-e ADMIN_PASSWORD=ThisShouldBeVeryVerySecure \
-e DATABASE_HOST=127.0.0.1 \
-e DATABASE_PORT=5432 \
-e DATABASE_NAME=your_database \
-e DATABASE_USER=your_db_user \
-e DATABASE_PASSWORD=OhThisIsSoSecure \
-e JWT_SIGNATURE_SECRET=testSecretItsVeryVerySecretSecret \
kuvaszmonitoring/kuvasz:latest
```

At this point you shouldn't see any error in your logs, so you're able to create your first monitor with an API call. Please, take a look at the [**API section**](https://github.com/kuvasz-uptime/kuvasz/wiki/API) of the Wiki, to get familiar with the authentication method that Kuvasz provides.
If you have a valid access token, then creating a monitor and scheduling an uptime check for it, is simple like that:

```shell
curl --location --request POST 'https://your.host:8080/api/v1/monitors/' \
--header 'Authorization: Bearer YourAccessToken' \
--header 'Content-Type: application/json' \
--data-raw '{
    "name": "my_first_monitor",
    "url": "https://website.to.check",
    "uptimeCheckInterval": 60
}'
```

You can read more about the **monitor management** in the [dedicated section](https://github.com/kuvasz-uptime/kuvasz/wiki/Monitor-management) of the Wiki.

## ⛴ Deployment

Although the example above is simple, when you want to deploy Kuvasz to production you'll probably end up with a more mature tooling or configuration. You can find the available **configuration properties [here](https://github.com/kuvasz-uptime/kuvasz/wiki/Configuration)**.
If you are going to deploy Kuvasz with **docker-compose or Kubernetes**, you should take a look at the [**deployment related examples**](https://github.com/kuvasz-uptime/kuvasz/tree/main/examples), or the [**Deployment**](https://github.com/kuvasz-uptime/kuvasz/wiki/Deployment) section of the Wiki.

## 📚 Further reading

If you want to know more about the fundamentals of Kuvasz, head to the [**Events & Event handlers**](https://github.com/kuvasz-uptime/kuvasz/wiki/Events-&-Event-handlers) section!

## ⁉️ Do you have a question?

Let's go to the [discussions](https://github.com/kuvasz-uptime/kuvasz/discussions)!

## ❤️ Sponsors

[![JetBrains](https://github.com/kuvasz-uptime/kuvasz/raw/main/docs/jetbrains-logo.png)](https://www.jetbrains.com/?from=kuvasz)

Thanks to [JetBrains](https://www.jetbrains.com/?from=kuvasz) for supporting the development of Kuvasz with their Open Source License Program 🙏
