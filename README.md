![Kuvasz](https://github.com/kuvasz-uptime/kuvasz/raw/main/docs/kuvasz_banner.png)

![CI](https://github.com/kuvasz-uptime/kuvasz/workflows/CI/badge.svg?branch=main)
[![codecov](https://codecov.io/gh/kuvasz-uptime/kuvasz/branch/main/graph/badge.svg)](https://codecov.io/gh/kuvasz-uptime/kuvasz)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fkuvasz-uptime%2Fkuvasz.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fkuvasz-uptime%2Fkuvasz?ref=badge_shield)
[![CodeFactor](https://www.codefactor.io/repository/github/kuvasz-uptime/kuvasz/badge)](https://www.codefactor.io/repository/github/kuvasz-uptime/kuvasz)
![Latest Release](https://badgen.net/github/release/kuvasz-uptime/kuvasz)
[![Gitter](https://badges.gitter.im/kuvasz-uptime/community.svg)](https://gitter.im/kuvasz-uptime/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![DockerHub](https://badgen.net/badge/docker/hub/blue?icon=docker)](https://hub.docker.com/r/kuvaszmonitoring/kuvasz)

---

## ℹ️ What is Kuvasz?

Kuvasz is a **headless uptime monitor service**, which means that it is able to watch all of your precious websites and notifies you if something bad happens to them. It is built in Kotlin, on top of the awesome [Micronaut framework](https://github.com/micronaut-projects). 
There are a lot of exciting, new features on the roadmap, you should check them out [here](https://github.com/kuvasz-uptime/kuvasz/projects/1).

### Where does the name come from?

Kuvasz (pronounce as [ˈkuvɒs]) is an ancient hungarian breed of livestock & guard dog. You can read more about them on [Wikipedia](https://en.wikipedia.org/wiki/Kuvasz).

### Features

- Uptime & latency monitoring with a configurable interval
- SSL certification monitoring (once a day)
- Email notifications through SMTP
- Slack notifications through webhoooks
- Telegram notifications through the Bot API
- Configurable data retention period

### Future ideas 🚧

- Regular Lighthouse audits for your websites
- Pagerduty, Opsgenie integration

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
-e JWT_SIGNATURE_SECRET=ThisOneShouldBeVeryRandomAsWell \
kuvaszmonitoring/kuvasz:latest
```

At this point you shouldn't see any error in your logs, so you're able to create your first monitor with an API call. Please, take a look at the [**API section**](https://github.com/kuvasz-uptime/kuvasz/wiki/API) of the Wiki, to get familiar with the authentication method that Kuvasz provides.
If you have a valid access token, then creating a monitor and scheduling an uptime check for it, is simple like that:

```shell
curl --location --request POST 'https://your.host:8080/monitors/' \
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

There is a dedicated room for Kuvasz on [Gitter](https://gitter.im/kuvasz-uptime/community).
