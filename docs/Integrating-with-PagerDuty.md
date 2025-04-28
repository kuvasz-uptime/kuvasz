# PagerDuty + Kuvasz Integration Benefits

* Notify on-call responders based on alerts sent from Kuvasz if any of your monitors is down or has an invalid SSL certificate
* Incidents will automatically resolve in PagerDuty when the metric in Kuvasz returns to normal (e.g. your monitor is up again, or you renew your site's certificate)

# How it Works

* If one of your monitor is down, has an invalid SSL certificate, or the certificate will expire in 30 days, Kuvasz will send an event to a service in PagerDuty. Events from Kuvasz will trigger a new incident on the corresponding PagerDuty service, or group as alerts into an existing incident.
* Once your monitor is up again, or has a valid SSL certificate, a resolve event will be sent to the PagerDuty service to resolve the alert, and associated incident on that service.

# Requirements
There are no special requirements prior to this setup to successfully integrate PagerDuty and Kuvasz.

# Support

If you need help with this integration, please contact the maintainer of Kuvasz, Adam Kobor at adam@akobor.me.

# Integration Walkthrough
## In PagerDuty

### Integrating With a PagerDuty Service
1. From the **Configuration** menu, select **Services**.
2. There are two ways to add an integration to a service:
   * **If you are adding your integration to an existing service**: Click the **name** of the service you want to add the integration to. Then, select the **Integrations** tab and click the **New Integration** button.
   * **If you are creating a new service for your integration**: Please read our documentation in section [Configuring Services and Integrations](https://support.pagerduty.com/docs/services-and-integrations#section-configuring-services-and-integrations) and follow the steps outlined in the [Create a New Service](https://support.pagerduty.com/docs/services-and-integrations#section-create-a-new-service) section, selecting "Kuvasz" as the **Integration Type** in step 4. Continue with the "In Kuvasz"  section (below) once you have finished these steps.
3. Enter an **Integration Name** in the format `monitoring-tool-service-name` (e.g.  Kuvasz-Shopping-Cart) and select "Kuvasz" from the Integration Type menu.
4. Click the **Add Integration** button to save your new integration. You will be redirected to the Integrations tab for your service.
5. An **Integration Key** will be generated on this screen. Keep this key saved in a safe place, as it will be used when you configure the integration with Kuvasz in the next section.
![](https://pdpartner.s3.amazonaws.com/ig-template-copy-integration-key.png)

## In Kuvasz

You have to set up two things in Kuvasz in order to make the integration work:

1. Provide an environment variable to the application with the name `ENABLE_PAGERDUTY_EVENT_HANDLER` and a value of `true`. You can read more about the available configuration variables of Kuvasz [here](https://github.com/kuvasz-uptime/kuvasz/wiki/Configuration).
2. Set your PagerDuty integration key for the desired monitor in one of the following ways:

**Providing a key when you create your monitor:**

```shell
curl --location --request POST 'https://your.kuvasz.host:8080/api/v1/monitors/' \
--header 'Authorization: Bearer YourKuvaszAccessToken' \
--header 'Content-Type: application/json' \
--data-raw '{
    "name": "my_first_monitor",
    "url": "https://website.to.check",
    "uptimeCheckInterval": 60,
    "pagerdutyIntegrationKey": "YourSecretIntegrationKeyFromPagerDuty"
}'
```

**Adding/updating a key for an existing monitor:**

```shell
curl --location --request PUT 'https://your.kuvasz.host:8080/api/v1/monitors/4/pagerduty-integration-key' \
--header 'Authorization: Bearer YourKuvaszAccessToken' \
--header 'Content-Type: application/json' \
--data-raw '{
    "pagerdutyIntegrationKey": "YourSecretIntegrationKeyFromPagerDuty"
}'
```

# How to Uninstall

If you want to disable a monitor's integration with PagerDuty, you can just simply delete the integration key of it with an API call like that:

```shell
curl --location --request DELETE 'https://your.kuvasz.host:8080/api/v1/monitors/4/pagerduty-integration-key' \
--header 'Authorization: Bearer YourKuvaszAccessToken'
```

Alternatively you can disable the integration between Kuvasz and PagerDuty globally by setting the value of the `ENABLE_PAGERDUTY_EVENT_HANDLER` to `false` (or by omitting it completely, since its default value is `false` too).
