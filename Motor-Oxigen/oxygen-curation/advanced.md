# Advanced Usage of InnerSource Curation <a name="pagetop"></a>

1. [Curation output path options](#outputs)
    - [Custom output path](#outputscustom)
    - [Default output structure](#outputsdefault)

---
## Curation output path options <a name="outputs"></a>
Users have the option to choose a custom output path or a default output structure to house the Curation outputs. All parameters can be specified in the configurations.

### Custom output path <a name="outputscustom"></a>
To direct Curation results to a custom output path, configure the following values:

1. Define a custom Curation output path.
> oxygen.stage.curation.paths.curatedDir = "/data/example/output-demo"

2. Define userHome and appName for temporary file storage.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

3. Enable automatic removal of temporary files at the end of the process (enabled by default).
> oxygen.features.enable.defaultOutputStructure = false

### Default output structure <a name="outputsdefault"></a>
Curation outputs can also be directed to a [default output structure](example/default-output-structure.txt). To utilize this feature, configure the following values:

1. Define userHome and appName. The userHome and appName parameters establish the root path of the output structure. Subsequently, the Curation output is directed to the following location: *{userHome}/{appName}-app/curation/curated/
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

2. Retain the default output structure.
> oxygen.features.enable.defaultOutputStructure = true

3. Comment out or remove the custom Curation output path.
> ~~oxygen.stage.curation.paths.curatedDir = "/data/example/output-demo"~~

[Return to Top](#pagetop)

