_Kuvasz_ is flexible to [configure](../setup/configuration.md) and also to extend. You can configure your monitors with various options, for example as the **HTTP method, interval, headers**, etc. More features are coming soon, such as completely **custom headers, POST requests with arbitrary payload, response keyword matching**, and more. 

However, the flexibility is not only about the monitoring possibilities, but also about the way you can describe your monitors. If you prefer to use the comfortable **Web UI**, you can do that, but if you prefer to manage **all your configuration as code**, you can do that as well. In that case, it gets really simple to **keep your monitors version controlled** along with your existing infrastructure code.

You can also choose to set up your monitors **initially via the UI**, and then **export** them to a _YAML_ configuration file, which **can be fed back** into _Kuvasz_ and checked into your version control system.

!!! tip 

    You can find the monitoring configuration options in the [**Managing monitors**](../setup/managing-monitors.md) section of the documentation.

## Metrics exporters <!-- md:config ../setup/metrics-exporters.md -->

_Kuvasz_ supports **exporting metrics** to _Prometheus_ or to any _OTLP-compatible_ tool, to allow you to integrate with your existing monitoring and alerting systems. This means you can use _Kuvasz_ alongside your preferred observability stack.

!!! tip

    Do you miss a specific exporter? Please [open an issue](https://github.com/kuvasz-uptime/kuvasz/issues/new?template=feature_request.md){target="_blank"}, or consider contributing it yourself! We are always open to new integrations and **would love to see your contribution**.
