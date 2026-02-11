![Kuvasz](docs/docs/images/kuvasz-banner-light.webp)

[![CI](https://github.com/kuvasz-uptime/kuvasz/actions/workflows/main.yml/badge.svg)](https://github.com/kuvasz-uptime/kuvasz/actions/workflows/main.yml)
[![GitHub known bugs](https://img.shields.io/github/issues-search/kuvasz-uptime/kuvasz?query=is%3Aopen%20label%3Abug&label=known%20bugs&color=red)](https://github.com/kuvasz-uptime/kuvasz/issues?q=is%3Aissue%20state%3Aopen%20label%3Abug)
[![codecov](https://codecov.io/gh/kuvasz-uptime/kuvasz/branch/main/graph/badge.svg?token=67X0CD3CGY)](https://codecov.io/gh/kuvasz-uptime/kuvasz)
![no vibe coded](https://img.shields.io/badge/vibe_coding-0%25-green)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fkuvasz-uptime%2Fkuvasz.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fkuvasz-uptime%2Fkuvasz?ref=badge_shield)
[![Docker Pulls](https://img.shields.io/docker/pulls/kuvaszmonitoring/kuvasz)](https://hub.docker.com/r/kuvaszmonitoring/kuvasz)
---

**Kuvasz** [ˈkuvɒs] is an open-source, self-hosted uptime & SSL monitoring service with [**status pages**](https://demo.kuvasz-uptime.dev/status), designed to help you keep track of your websites and services. It provides a modern, user-friendly interface, a powerful REST API, and supports multiple notification channels like email, Discord, Slack, Telegram, and PagerDuty.

![Kuvasz](docs/docs/images/feature_carousel.webp)

## [📖 Documentation](https://kuvasz-uptime.dev)

## 🛝 Live demo

You can try out _Kuvasz_ on the dedicated demo instance under [https://demo.kuvasz-uptime.dev](https://demo.kuvasz-uptime.dev)

Use the following credentials to log in:

- **Username**: `demo`
- **Password**: `secureDemoPassword`

## [🔮 Roadmap](https://github.com/orgs/kuvasz-uptime/projects/2/views/1)

## ⚡️  Quick start guide

If you want to get started quickly, please refer to the [**Deployment guide**](https://kuvasz-uptime.dev/setup/installation/) in the documentation.

## ✨ Features

- **HTTP(S) monitoring**: Monitor the availability and performance of your websites and services by sending HTTP(S) requests.
- **SSL certification monitoring**: Automatically check the SSL certificates of your monitored services to ensure they are valid and not expired.
- **Notifications on a per-monitor basis**: Configure different notification channels for each monitor, allowing you to tailor alerts to your specific needs.
- **Status pages**: Create public or private status pages to keep your users or your own team informed about the status of your services.
- **Sleek UI**: Kuvasz has a modern, responsive, and user-friendly interface that makes it easy to manage your monitors.
- **Full-fledged REST API**: Manage your monitors, check their status, and more through a powerful API.
- **Metrics exporters**: Export your metrics to _OpenTelemetry_ and _Prometheus_ for better observability and integration with your existing monitoring stack.
- More to come: Take a look at our [**Roadmap**](https://github.com/orgs/kuvasz-uptime/projects/2/views/1)

## 🚀  Kuvasz vs. UptimeRobot

|                                           |    Kuvasz     | UptimeRobot Free | UptimeRobot Solo |
|-------------------------------------------|:-------------:|:----------------:|:----------------:|
| Price                                     |     Free      |       Free       |     $84/year     |
| Monitoring interval                       | **5 seconds** |    5 minutes     |    60 seconds    |
| Monitors limit                            | **unlimited** |        50        |        10        |
| Location-specific monitoring              |      ✅\*      |        ❌         |        ✅         |
| Translations                              |       ✅       |        ❌         |        ❌         |
| Custom data retention                     |       ✅       |     3 months     |    12 months     |
| REST API                                  |       ✅       |        ✅         |        ✅         |
| Prometheus & OpenTelemetry exporters      |       ✅       |        ❌         |        ❌         |
| Backups & YAML configuration              |       ✅       |        ❌         |        ❌         |
| Status pages                              |       ✅       |      only 1      |      only 3      |
| Maintenance windows                       |      📆       |        ❌         |        ✅         |
| **HTTPs monitoring**                      |               |                  |                  |
| Keyword matching                          |       ✅       |        ✅         |        ✅         |
| Header matching                           |       ✅       |        ❌         |        ❌         |
| Slow response alerts                      |       ✅       |        ❌         |        ✅         |
| Custom HTTP methods                       |       ✅       |  ❌ (HEAD only)   |        ✅         |
| Custom status matcher                     |       ✅       |        ❌         |        ✅         |
| Custom headers                            |       ✅       |        ❌         |        ✅         |
| Custom request body                       |       ✅       |        ❌         |        ✅         |
| **SSL monitoring**                        |       ✅       |        ❌         |        ✅         |
| **Heartbeat (push) monitoring**           |       ✅       |        ❌         |        ✅         |
| **Ping (ICMP) monitoring**                |      📆       |        ✅         |        ✅         |
| **Port monitoring**                       |       ❌       |        ✅         |        ✅         |
| **DNS monitoring**                        |       ❌       |        ❌         |        ✅         |
| **Domain expiration monitoring**          |       ❌       |        ❌         |        ✅         |
| **Notifications**                         |               |                  |                  |
| Email                                     |       ✅       |        ✅         |        ✅         |
| Discord                                   |       ✅       |        ✅         |        ✅         |
| Slack                                     |       ✅       |        ❌         |        ✅         |
| Telegram                                  |       ✅       |        ❌         |        ✅         |
| Pagerduty                                 |       ✅       |        ❌         |        ❌         |
| MS Teams                                  |      📆       |        ❌         |        ✅         |
| Webhook                                   |      📆       |        ❌         |        ❌         |
| SMS / Voice call                          |     📆\**     |        ❌         |  10 incl./month  |
| Google Chat, Pushover, Pushbullet, Splunk |       ❌       |        ✅         |        ✅         |
| Mattermost                                |       ❌       |        ❌         |        ✅         |

✅ Supported | ❌ Not supported | 📆 Planned

- \* You can deploy _Kuvasz_ to multiple locations and monitor your services from those locations, but it does not support location-specific monitoring out of the box.
- \** _Kuvasz_ will only provide the integration, but you will need to pay for the SMS or voice call service yourself

### Where does the name come from?

Kuvasz (pronounce as [ˈkuvɒs]) is an ancient hungarian breed of livestock & guard dog. You can read more about them on [Wikipedia](https://en.wikipedia.org/wiki/Kuvasz).

## 📣  Don't miss out on the latest updates!

First and foremost, if you want to **stay up-to-date with the latest news**, features, and updates about _Kuvasz_, please consider:

- starring the project on [**GitHub**](https://github.com/kuvasz-uptime/kuvasz) and on [**Docker Hub**](https://hub.docker.com/r/kuvaszmonitoring/kuvasz)
- following us on [**X**](https://x.com/KuvaszUptime)
- following us on [**Mastodon**](https://techhub.social/@KuvaszUptime)

## ☕️ Do you like it?

While _Kuvasz_ is free and open-source, it still **requires a lot of time and effort** to maintain and develop. If you like it, please consider supporting the project by **donating** via Ko-fi:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/L4L31DH59D)

... or by [**sponsoring the project on GitHub**](https://github.com/sponsors/adamkobor/)
