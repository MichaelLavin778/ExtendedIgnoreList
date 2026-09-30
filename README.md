# Extended Ignore List

A starter project for an Extended Ignore List RuneLite plugin. **Ignore-list features have not been implemented yet.** This repository is not ready for submission to Plugin Hub until the plugin does what its description promises.

## Development

Install Java 11 and open the project in IntelliJ IDEA. Run `./gradlew run` (or `gradlew.bat run` on Windows) to launch RuneLite with the plugin loaded. The main plugin class is `com.extendedignorelist.ExtendedIgnoreListPlugin`; metadata is in `runelite-plugin.properties`.

The project uses the RuneLite [example-plugin](https://github.com/runelite/example-plugin) template and `build=standard`, which lets Plugin Hub use its standard build instead of custom Gradle build logic. Avoid adding third-party dependencies unless necessary; Plugin Hub requires cryptographic hash verification for them.

## Plugin Hub submission

Once the plugin works, update its user-facing description and tags, push a commit to this public repository, then fork [runelite/plugin-hub](https://github.com/runelite/plugin-hub). Add `plugins/extendedignorelist` to your fork containing:

```properties
repository=https://github.com/MichaelLavin778/ExtendedIgnoreList.git
commit=<full 40-character SHA of the plugin commit>
```

Open a pull request to `runelite/plugin-hub`. If you update this plugin later, update the `commit=` SHA in that manifest. See the [Plugin Hub submission guide](https://github.com/runelite/plugin-hub) for review and build requirements. This repository alone does not install the plugin in RuneLite.