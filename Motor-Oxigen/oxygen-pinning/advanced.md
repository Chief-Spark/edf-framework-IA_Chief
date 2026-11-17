# Advanced Usage of InnerSource Pinning <a name="pagetop"></a>

1. [Pinning output path options](#outputs)
    - [Custom output path](#outputscustom)
    - [Default output structure](#outputsdefault)
2. [Reusing pins](#reusepin)
    - [Dynamic pinning - custom output path](#dynamiccustom)
    - [Static pinning - custom output path](#staticcustom)
    - [Dynamic pinning - default output structure](#dynamicdefault)
    - [Static pinning - default output structure](#staticdefault)
3. [Keep Together custom path](#keeptogether)
4. [Keep Apart custom path](#keepapart)

---
## Pinning output path options <a name="outputs"></a>
Users have the option to choose a custom output path or a default output structure to house the Pinning outputs. All parameters can be specified in the configurations.

### Custom output path <a name="outputscustom"></a>
To direct Pinning results to a custom output path, configure the following values:

1. Define a custom Pinning output path.
> oxygen.stage.pinning.paths.pinnedDir = "/data/example/output-demo"

2. Define userHome and appName for temporary file storage.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

3. Enable automatic removal of temporary files at the end of the process (enabled by default).
> oxygen.features.enable.defaultOutputStructure = false

### Default output structure <a name="outputsdefault"></a>
Pinning outputs can also be directed to a [default output structure](example/default-output-structure.txt). To utilize this feature, configure the following values:

1. Define userHome and appName. The userHome and appName parameters establish the root path of the output structure. Subsequently, the Pinning output is directed to the following location: *{userHome}/{appName}-app/generations/{generation}/person/pinning/pinned/{timestamp}-pin-hc*.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

2. Define the candidate generation name.
> oxygen.application.generation.candidate = default

3. Retain the default output structure.
> oxygen.features.enable.defaultOutputStructure = true

4. Comment out or remove the custom Pinning output path.
> ~~oxygen.stage.pinning.paths.pinnedDir = "/data/example/output-demo"~~

[Return to Top](#pagetop)

---

## Reusing pins <a name="reusepin"></a>
Pinning operates in two modes: dynamic and static. Dynamic pinning resets the pin numbers for all entities and is typically used in the initial run. In contrast, static pinning maintains the pin numbers of the same entities from the reference generation and is commonly used in successive runs to ensure pin consistency.

### Dynamic pinning - custom output path <a name="dynamiccustom"></a>
1. Define a custom pinning output path.
> oxygen.stage.pinning.paths.pinnedDir = "/data/example/output-demo-gen1"

2. Define userHome and appName for temporary file storage.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

3. Enable automatic removal of temporary files at the end of the process (enabled by default).
> oxygen.features.enable.defaultOutputStructure = false

4. Set reusePin to false.
> oxygen.stage.pinning.reusePin = false

### Static pinning - custom output path <a name="staticcustom"></a>
1. Define a custom pinning output path.
> oxygen.stage.pinning.paths.pinnedDir = "/data/example/output-demo-gen2"

2. Define the reference pinning output path.
> oxygen.stage.pinning.paths.pinnedReferenceDir = "/data/example/output-demo-gen1"

3. Define userHome and appName for temporary file storage.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

4. Enable automatic removal of temporary files at the end of the process (enabled by default).
> oxygen.features.enable.defaultOutputStructure = false

5. Set reusePin to true.
> oxygen.stage.pinning.reusePin = true

### Dynamic pinning - default output structure <a name="dynamicdefault"></a>
1. Define userHome and appName.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

2. Define the candidate generation name.
> oxygen.application.generation.candidate = gen1

3. Retain the default output structure.
> oxygen.features.enable.defaultOutputStructure = true

4. Comment out or remove the custom pinning output path.
> ~~oxygen.stage.pinning.paths.pinnedDir = "/data/example/output-demo"~~

5. Set reusePin to false.
> oxygen.stage.pinning.reusePin = false

### Static pinning - default output structure <a name="staticdefault"></a>
1. Define userHome and appName.
> oxygen.application.paths.userHome = "/data/example/oxygen-home"
> 
> oxygen.application.paths.appName = "oxygen-is"

2. Define the candidate generation name.
> oxygen.application.generation.candidate = gen2

3. Define the reference generation name.
> oxygen.application.generation.reference = gen1

4. Retain the default output structure.
> oxygen.features.enable.defaultOutputStructure = true

5. Comment out or remove the custom pinning output path and custom reference pinning output path.
> ~~oxygen.stage.pinning.paths.pinnedDir = "/data/example/output-demo-gen2"~~
> 
> ~~oxygen.stage.pinning.paths.pinnedReferenceDir = "/data/example/output-demo-gen1"~~

6. Set reusePin to true.
> oxygen.stage.pinning.reusePin = true

[Return to Top](#pagetop)

---

## Keep Together custom path <a name="keeptogether"></a>
The 'Keep Together' functionality enforces specified identities and their associated events to remain under the same pin. For more details, please refer to the 'Keep Together' [documentation](https://pages.experian.com/display/ODC/Building+your+pipeline+-+Pinning#BuildingyourpipelinePinning-KeepTogether). 

To establish the custom path for the 'Keep Together' files:
> oxygen.application.paths.keepTogetherHome = /data/example/keep-together

[Return to Top](#pagetop)

---

## Keep Apart custom path <a name="keepapart"></a>
The 'Keep Apart' functionality forces specified identities and their associated events to remain as separate pins. For more details, please refer to the 'Keep Apart' [documentation](https://pages.experian.com/display/ODC/Building+your+pipeline+-+Pinning#BuildingyourpipelinePinning-KeepApart). 

To establish the custom path for the 'Keep Apart' files:
> oxygen.application.paths.keepApartHome = /data/example/keep-apart

[Return to Top](#pagetop)

