package com.kuvaszuptime.kuvasz.ui.utils

import com.kuvaszuptime.kuvasz.ui.*
import de.comahe.i18n4k.messages.MessageBundleLocalizedString
import kotlinx.html.*
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

internal fun CoreAttributeGroupFacade.classes(vararg classes: CSSClass) {
    this.classes = classes.map { it.className }.toSet()
}

internal fun MutableSet<CSSClass>.addIf(
    condition: Boolean,
    cssClass: CSSClass,
    default: CSSClass? = null
): Set<CSSClass> {
    if (condition) {
        this.add(cssClass)
    } else if (default != null) {
        this.add(default)
    }
    return this
}

internal fun MutableSet<CSSClass>.addIfNotNull(cssClass: CSSClass?): Set<CSSClass> {
    if (cssClass != null) {
        this.add(cssClass)
    }
    return this
}

internal fun CoreAttributeGroupFacade.classes(classes: Set<CSSClass>) {
    this.classes = classes.map { it.className }.toSet()
}

internal fun CoreAttributeGroupFacade.classes(block: () -> Set<CSSClass>) {
    this.classes = block().map { it.className }.toSet()
}

internal fun HTMLTag.ariaLabel(label: String) {
    attributes["aria-label"] = label
}

internal fun HTMLTag.ariaExpanded(expanded: Boolean) {
    attributes["aria-expanded"] = expanded.toString()
}

internal fun HTMLTag.ariaControls(controls: String) {
    attributes["aria-controls"] = controls
}

internal fun HTMLTag.relNoOpener() {
    attributes["rel"] = "noopener"
}

internal fun HTMLTag.required() {
    attributes["required"] = "true"
}

internal fun A.targetBlank() {
    target = "_blank"
}

/**
 * Needed only because kotlinx.html doesn't support `template` tags inside divs
 * @see https://github.com/Kotlin/kotlinx.html/issues/293
 */
@HtmlTagMarker
@OptIn(ExperimentalContracts::class)
inline fun FlowContent.templateTag(classes: String? = null, crossinline block: TEMPLATE.() -> Unit = {}) {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    TEMPLATE(attributesMapOf("class", classes), consumer).visit(block)
}

internal fun HTMLTag.unsafeText(text: MessageBundleLocalizedString) {
    unsafe { raw(text.invoke()) }
}
