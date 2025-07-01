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

1. To set up _Kuvasz_, follow the installation guide in the [**documentation**](https://kuvasz-uptime.dev/setup/installation/).
2. Once you have Kuvasz up and running, you need to configure the _PagerDuty_ integration in your _YAML_ configuration file. Please refer to the [**Integrations setup**](https://kuvasz-uptime.dev/setup/integrations#pagerduty) section of the documentation for more information on how to configure _PagerDuty_.
3. Assuming you have your integration set up, refer to the [Managing monitors](https://kuvasz-uptime.dev/setup/managing-monitors/) section of the documentation to create/update a monitor that uses your brand new _PagerDuty_ integration.
