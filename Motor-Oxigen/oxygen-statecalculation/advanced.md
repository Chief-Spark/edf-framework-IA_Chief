# Advanced Usage of InnerSource StateCalc <a name="pagetop"></a>

1. [StateCalc output path options](#outputs)
    - [Custom output path](#outputscustom)
    - [Default output structure](#outputsdefault)
 

---
##    StateCalc output path options <a name="outputs"></a>
Users have the option to choose a custom output path or a default output structure to house the StateCalc outputs. All parameters can be specified in the configurations.

### Custom output path <a name="outputscustom"></a>
To direct StateCalc results to a custom output path, configure the following values:

1. Define a custom StateCalc output path.
> oxygen.stage.consistencyCheck.paths.statecalcDir = "/data/example/output-demo"

2. Define userHome and appName for temporary file storage.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

3. Enable automatic removal of temporary files at the end of the process (enabled by default).
> oxygen.features.enable.defaultOutputStructure = false

### Default output structure <a name="outputsdefault"></a>
StateCalc outputs can also be directed to a [default output structure](example/default-output-structure.txt). To utilize this feature, configure the following values:

1. Define userHome and appName. The userHome and appName parameters establish the root path of the output structure. Subsequently, the StateCalc output is directed to the following location: *{userHome}/{appName}-app/generations/{generation}/person/statecalc/calculated/{timestamp}/*.json.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
 
> oxygen.application.paths.appName = "oxygen-is"

2. Define the candidate generation name.
> oxygen.application.generation.candidate = default

3. Retain the default output structure.
> oxygen.features.enable.defaultOutputStructure = true

4. Comment out or remove the custom Statecalc output path.
> oxygen.stage.consistencyCheck.paths.statecalcDir = "/data/example/output-demo"

[Return to Top](#pagetop)

