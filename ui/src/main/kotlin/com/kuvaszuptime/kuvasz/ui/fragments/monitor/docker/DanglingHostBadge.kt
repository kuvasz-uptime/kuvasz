package com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.components.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

/**
 * Flags a monitor whose host was removed from the config: the monitor keeps the name, so it stays editable, but
 * every check of it fails until the host is added back.
 */
internal fun FlowContent.danglingDockerHostBadge(dockerHost: String) =
    inlineStatusBadge(
        text = Messages.dockerHostNotConfiguredBadge(),
        color = Color.ORANGE_LT,
        icon = Icon.ALERT_TRIANGLE,
        tooltip = Messages.dockerHostNotConfiguredTooltip(dockerHost),
    )

/** The label-less variant of [danglingDockerHostBadge], for the name cell of the list. */
internal fun FlowContent.danglingDockerHostIconBadge(dockerHost: String) {
    span {
        testId("docker-host-not-configured-badge")
        classes(BADGE, Color.ORANGE_LT.bgColor, Color.ORANGE_LT.textColor, MS_2)
        tooltip(Messages.dockerHostNotConfiguredTooltip(dockerHost))
        icon(Icon.ALERT_TRIANGLE)
    }
}
