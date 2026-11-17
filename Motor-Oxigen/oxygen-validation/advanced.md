# Advanced Usage of InnerSource Validation <a name="pagetop"></a>

1. [Validation output path options](#outputs)
    - [Custom output path](#outputscustom)
    - [Default output structure](#outputsdefault)

---
## Validation output path options <a name="outputs"></a>
Users have the option to choose a custom output path or a default output structure to house the Validation outputs. All parameters can be specified in the configurations.

### Custom output path <a name="outputscustom"></a>
To direct Validation results to a custom output path, configure the following values:

1. Define the custom structure (This is false as default)
> "oxygen.features.enable.defaultOutputStructure" = false

2. Define a custom Validation input path
> oxygen.stage.validation.paths.validationDir = "/example/discovery"

3. Define a cusFtom Validation output path
> oxygen.stage.validation.paths.validatedDir = "/example/validatedOutput"

3.Define userHome and appName for temporary file storage.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

---

### Default output structure <a name="outputsdefault"></a>
Validation outputs can also be directed to a [default output structure](example/default-output-structure.txt). To utilize this feature, configure the following values:

1. Define userHome and appName. The userHome and appName parameters establish the root path of the output structure. Subsequently, the Validation output is directed to the following location: *oxygen.stage.validation.paths.validatedDir = "/example/validatedOutput"*.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

2. Define the candidate generation name.
> oxygen.application.generation.candidate = default

3. Retain the default output structure.
> oxygen.features.enable.defaultOutputStructure = true

[Return to Top](#pagetop)
