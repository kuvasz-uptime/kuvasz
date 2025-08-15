This section explains several conventions used in this documentation. Heavily influenced by the conventions used in
[mkdocs-material](https://squidfunk.github.io/mkdocs-material/) 🙏.

## Symbols

### <!-- md:version --> – Version { #version data-toc-label="Version" }

The tag symbol in conjunction with a version number denotes when a specific
feature or behavior was added. Make sure you're at least on this version
if you want to use it.

### <!-- md:default --> – Default value { #default data-toc-label="Default value" }

Some properties can have a default value, which is used if you don't specify it explicitly.

### <!-- md:type --> – Property type { #type data-toc-label="Property type" }

Every property has a type, which is usually one of the following:

- `string` – an arbitrary - string - value
- `boolean` – a boolean value, which can be `true` or `false`
- `number` – a numeric value
- `list` – a list of values
- `enum` - an enumerated value, which can be one of the predefined values

### <!-- md:flag experimental --> – Experimental { #experimental data-toc-label="Experimental" }

Some newer features are still considered experimental, which means they might
(although rarely) change at any time, or might be considered not so stable yet.

### <!-- md:flag required --> – Required { #required data-toc-label="Required" }

Some properties can be required, which means you must specify them in your configuration file.

### <!-- md:required_if --> – Required if { #required_if data-toc-label="Required if" }

Some properties can be required only if a certain condition is met, for example, if another feature is enabled. 
You'll find the condition next to the symbol.

### <!-- md:config #config --> – Configuration { #config data-toc-label="Configuration" }
This symbol indicates that a dedicated configuration section is available for the feature or integration you are reading about.

### <!-- md:yaml_prop --> – YAML property { #yaml_property data-toc-label="YAML property" }
When a setting in the documentation is marked with this symbol, it means that the setting can be configured in the YAML configuration file with the given property name.
