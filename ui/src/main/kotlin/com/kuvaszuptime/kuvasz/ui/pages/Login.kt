package com.kuvaszuptime.kuvasz.ui.pages

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.ui.*
import com.kuvaszuptime.kuvasz.ui.CSSClass.*
import com.kuvaszuptime.kuvasz.ui.icons.*
import com.kuvaszuptime.kuvasz.ui.utils.*
import kotlinx.html.*

fun renderLoginPage(appGlobals: AppGlobals, loginErrorMessage: String? = null): String {
    return withLayout(appGlobals, "Login") {
        loginForm(loginErrorMessage)
    }
}

internal fun FlowContent.loginForm(loginErrorMessage: String?) {
    div {
        classes(CONTAINER)
        div {
            classes(ROW, JUSTIFY_CONTENT_CENTER)
            div {
                classes(COL_MD_6, COL_LG_4)
                div {
                    classes(LOGIN_CARD)
                    div {
                        classes(CARD)
                        div {
                            classes(CARD_BODY)
                            h2 {
                                classes(CARD_TITLE, TEXT_CENTER, MB_4)
                                +Messages.signIn()
                            }
                            // Display error message if login fails
                            if (!loginErrorMessage.isNullOrEmpty()) {
                                div {
                                    classes(ALERT, ALERT_IMPORTANT, ALERT_DANGER, ALERT_DISMISSIBLE)
                                    attributes["role"] = "alert"
                                    div {
                                        classes(ALERT_ICON)
                                        icon(Icon.CIRCLE_EXCLAMATION)
                                    }
                                    div {
                                        h4 {
                                            classes(ALERT_HEADING)
                                            +loginErrorMessage
                                        }
                                    }
                                    a {
                                        classes(BTN_CLOSE)
                                        alertCloser()
                                    }
                                }
                            }
                            // Login form
                            form(action = "/auth/login", method = FormMethod.post) {
                                div {
                                    classes(MB_3)
                                    label {
                                        classes(FORM_LABEL, REQUIRED)
                                        +Messages.username()
                                    }
                                    input(type = InputType.text) {
                                        classes(FORM_CONTROL)
                                        name = "username"
                                        autoComplete = "username"
                                        placeholder = Messages.enterUsername()
                                        required()
                                    }
                                }
                                div {
                                    classes(MB_3)
                                    label {
                                        classes(FORM_LABEL, REQUIRED)
                                        +Messages.password()
                                    }
                                    input(type = InputType.password) {
                                        classes(FORM_CONTROL, REQUIRED)
                                        name = "password"
                                        autoComplete = "current-password"
                                        placeholder = Messages.enterPassword()
                                        required()
                                    }
                                }
                                div {
                                    classes(FORM_FOOTER)
                                    button(type = ButtonType.submit) {
                                        classes(BTN, BTN_PRIMARY, W_100)
                                        +Messages.signIn()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
