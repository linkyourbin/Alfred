# Alfred

Alfred is a CLion plugin by linkyourbin: a dev environment butler for saving
and switching development environment profiles, especially for embedded and
multi-chip projects.

Profiles are stored under the CLion config directory:

```text
clion-dev-envs/<profile-name>/
```

## Features

- Save current CLion project and IDE settings as a named profile.
- Update the active profile without retyping its name.
- Switch between saved profiles for STM32, HPM, ESP, RISC-V, or other chip setups.
- Stage profile activation for the next CLion startup so live IDE settings are not overwritten while CLion is running.

## Plugin Menu

```text
Tools > Alfred
```

Actions:

- `Save Current Config as Profile...`
- `Update Active Profile`
- `Switch Profile...`

## Build

```powershell
.\gradlew.bat test :clion-plugin:buildPlugin
```

Upload artifact:

```text
clion-plugin/build/distributions/alfred.zip
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
