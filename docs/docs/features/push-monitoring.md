Push (or also known as "cron" or "heartbeat") monitoring allows you to monitor your services by sending HTTP requests to _Kuvasz_ periodically. This is useful to monitor backups, or any other service that is not possible to monitor by sending an HTTP request to it. 

![Creating a push monitor](../images/ui/create_push_monitor.webp)

## How does it work?

You can set up your push monitors by specifying the following:

- a **heartbeat interval** which determines how often a heartbeat is expected to be sent from your service to _Kuvasz_
- a **grace period** which determines how long a heartbeat can be missed before an alert is triggered
- a unique and secure **client secret** that identifies your monitor and which will be used in the requests when your services signal a heartbeat or an explicit failure

The heartbeat interval and grace period are both **specified in seconds**.

_Kuvasz_ will then check every push monitors every 5 seconds to see if a heartbeat has been missed. Only **monitors with an already received heartbeat will be evaulated**, since otherwise it would be impossible to determine if the next heartbeat was missed or not.

You can find more details in the [**Managing push monitors**](../management/push-monitors.md) section of the documentation.

### Example

If you specify a heartbeat interval of 60 seconds and a grace period of 10 seconds, and the first (or previous) heartbeat **was received at** `17:50:10`, then the next heartbeat **is expected until** `17:51:20` (60 + 10 seconds after the previous heartbeat).

!!!info "Sending heartbeats / signaling failures"

    You can send heartbeats or even signal explicit failures by sending a `POST` or `GET` request to the right endpoints using your client secret. More details can be found [**here**](../management/push-monitors.md#sending-heartbeats-signaling-failures).

## Configuration <!-- md:config ../management/push-monitors.md -->

Please refer to the [**Managing push monitors**](../management/push-monitors.md) section of the documentation for more information on how to configure push monitoring.
