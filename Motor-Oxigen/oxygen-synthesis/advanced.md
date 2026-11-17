# Advanced Usage of InnerSource Synthesis <a name="pagetop"></a>

1. [Synthesis output path options](#outputs)
    - [Custom output path](#outputscustom)
    - [Default output structure](#outputsdefault)

---
## Synthesis output path options <a name="outputs"></a>
Users have the option to choose a custom output path or a default output structure to house the Synthesis outputs. All parameters can be specified in the configurations.

### Custom output path <a name="outputscustom"></a>
To direct Synthesis results to a custom output path, configure the following values:

1. Define the custom Synthesis output paths.
> oxygen.stage.consistencyCheck.paths.checkedDir = "/data/example/output-demo/checked"
> 
> oxygen.stage.consistencyCheck.paths.synthesizedDir = "/data/example/output-demo/synthesized"

2. Define userHome and appName for temporary file storage.
> oxygen.application.paths.userHome = "/data/example/home"
> 
> oxygen.application.paths.appName = "oxygen-is"

3. Enable automatic removal of temporary files at the end of the process (enabled by default).
> oxygen.features.enable.defaultOutputStructure = false

### Default output structure <a name="outputsdefault"></a>
Synthesis outputs can also be directed to a [default output structure](example/default-output-structure.txt). To utilize this feature, configure the following values:

1. Define userHome and appName. The userHome and appName parameters establish the root path of the output structure. Subsequently, the Synthesis output is directed to the following location: *{userHome}/{appName}-app/generations/{generation}/person/consistency/.
> oxygen.application.paths.userHome = "/data/example/home"
> 
> oxygen.application.paths.appName = "oxygen-is"

2. Retain the default output structure.
> oxygen.features.enable.defaultOutputStructure = true

3. Comment out or remove the custom Synthesis output paths.
> ~~oxygen.stage.consistencyCheck.paths.checkedDir = "/data/example/output-demo/checked"~~
>
> ~~oxygen.stage.consistencyCheck.paths.synthesizedDir = "/data/example/output-demo/synthesized"~~

[Return to Top](#pagetop)

