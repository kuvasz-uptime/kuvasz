_Kuvasz_ is easily localizable (UI, notifications), if you would like to add a new language, you can do so by following the steps below.

## Adding a new language

1. **Fork** the [Kuvasz repository](https://github.com/kuvasz-uptime/kuvasz){target="_blank"}
2. **Clone** the [English](https://github.com/kuvasz-uptime/kuvasz/blob/main/shared/src/main/i18n/com/kuvaszuptime/kuvasz/i18n/Messages_en.properties){target="_blank""} (or any other) translation, and name it after your language's 2-letter ISO code, for example `Messages_de.properties` for German
3. **Translate** the messages in the file to your language, keeping the keys intact. Regarding the format, you can find more details [**here**](https://github.com/comahe-de/i18n4k?tab=readme-ov-file#message-format){target="_blank""}. **Do NOT remove the comment lines** (starting with `#`), as they contain important information about the message keys and their usage. Also, please **don't change the order of the keys**, as this would make it harder to compare the translations with the English version.
4. Make sure that you've **updated the documentation** regarding the available languages.
5. **Try out** your translation by running the application with your new translation file <!-- md:config setup/configuration.md#language -->
6. **Use the convenience script** to check if your translation file matches the English version structurally, by running `./gradlew validateI18N`
7. **Submit your PR** with the new translation file

!!! tip

    If you are not sure about the 2-letter ISO code of your language, you can find them [**here**](https://en.wikipedia.org/wiki/List_of_ISO_639-1_codes){target="_blank"}.
