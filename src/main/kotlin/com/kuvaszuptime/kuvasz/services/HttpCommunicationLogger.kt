package com.kuvaszuptime.kuvasz.services

import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.netty.channel.ChannelPipelineCustomizer
import io.netty.channel.ChannelPipeline
import org.zalando.logbook.Logbook
import org.zalando.logbook.netty.LogbookClientHandler
import org.zalando.logbook.netty.LogbookServerHandler
import javax.inject.Singleton

@Singleton
@Requires(property = "http-communication-log.enabled", value = "true")
class HttpCommunicationLogger(private val logbook: Logbook) : BeanCreatedEventListener<ChannelPipelineCustomizer> {

    override fun onCreated(event: BeanCreatedEvent<ChannelPipelineCustomizer>): ChannelPipelineCustomizer {
        val customizer = event.bean
        if (customizer.isServerChannel) {
            customizer.doOnConnect { pipeline: ChannelPipeline ->
                pipeline.addAfter(
                    ChannelPipelineCustomizer.HANDLER_HTTP_SERVER_CODEC,
                    "logbook",
                    LogbookServerHandler(logbook)
                )
                pipeline
            }
        } else {
            customizer.doOnConnect { pipeline: ChannelPipeline ->
                pipeline.addAfter(
                    ChannelPipelineCustomizer.HANDLER_HTTP_CLIENT_CODEC,
                    "logbook",
                    LogbookClientHandler(logbook)
                )
                pipeline
            }
        }
        return customizer
    }
}
