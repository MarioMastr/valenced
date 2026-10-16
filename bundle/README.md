<p align="center">
  <picture>
    <source
      width="512px"
      media="(prefers-color-scheme: dark)"
      srcset="assets/wordmark/wordmark+slogan-dark.svg"
    >
    <img
      width="512px"
      src="assets/wordmark/wordmark+slogan-light.svg"
    >
  </picture>
  <br>
   <a href="https://discord.com/invite/ddcQf3s2Uq">
       <picture>
           <source height="32px" media="(prefers-color-scheme: dark)" srcset="https://user-images.githubusercontent.com/13122796/178032563-d4e084b7-244e-4358-af50-26bde6dd4996.png" />
           <img height="32px" src="https://user-images.githubusercontent.com/13122796/178032563-d4e084b7-244e-4358-af50-26bde6dd4996.png" />
       </picture>
   </a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
   <a href="https://github.com/Valenced">
       <picture>
           <source height="32px" media="(prefers-color-scheme: dark)" srcset="https://i.ibb.co/dMMmCrW/Git-Hub-Mark.png" />
           <img height="32px" src="https://i.ibb.co/9wV3HGF/Git-Hub-Mark-Light.png" />
       </picture>
   </a>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
   </a>
</p>

# Valenced

**Discord, your way.** Valenced is a client modification for Discord Android.

Valenced aims to be a lightweight and lightning-fast client modification for Discord Android, while being user-friendly and developer-first. It provides a powerful framework, allowing developers to make add-ons with ease. The sky is the limit!

## ❓ About

This repository releases Hermes Bytecode to be executed on official Discord Android clients. The bytecode is not standalone and is meant to be used with official bootstrappers listed in the [⬇️ Download](#️-download) section.

## 💪 Features

- **🔌 Plugins**: Extend Discord with custom features
- **🎨 Themes & Fonts**: Customize Discord's appearance to your liking
- **🧪 Experiments**: Try out Discord's new features before they're rolled out

## ⬇️ Download

These are the official Valenced ways to install Valenced:

- **📵 Non-root**: [Valenced Manager](https://github.com/Valenced/valenced-manager/releases/latest)
- **🩹 Root with Xposed**: [ValencedXposed](https://github.com/Valenced/valenced-xposed/releases/latest)

Using the installation methods above will currently install [Valenced Classic](https://github.com/Valenced/valenced-bundle). You'll need to update from Valenced Classic to Valenced Next manually.

### ⬆️ Updating to Valenced Next

> **It is recommended to do a separate installation of Valenced before updating to Valenced Next.**  
> Valenced Next does not offer an easy way to downgrade to Valenced Classic.

To update to Valenced Next from Valenced Classic, follow these steps:

1. Download the latest release asset from [Valenced Next's Actions tab](https://github.com/Valenced/valenced-xposed/actions/workflows/build.yml).
2. Extract the built bundle and host a local HTTP server that serves the `valenced.bundle` file.
3. In Valenced Classic, go to **Settings** > **Valenced**, and toggle on **Developer Settings**.
4. Navigate back, and head into the **Developer** section.
5. Edit the **Load from custom URL** field to point to the URL of the `valenced.bundle` file you hosted.
6. Restart Discord, and you should be running Valenced Next!

### 🔄️ Updating builds

Valenced Next is updated regularly with new features and bug fixes. To update to the latest build, follow these steps:

1. Host a HTTP server that points to a new `valenced.bundle` file.
2. Head to **Settings** > **Developer** (under the **Valenced** section).
3. Tap on the **Evaluate JavaScript** option.
4. Paste and evaluate the following snippet. Make sure to modify the URL to point to your newly hosted `valenced.bundle` file:

    ```js
    var VALENCED_UPDATE_URL = "<URL here, keep the quotes>";
    valenced.discord.native.FileModule.writeFile("documents", "pyoncord/loader.json", JSON.stringify({"customLoadUrl":{"enabled":true,"url":VALENCED_UPDATE_URL}}), "utf8");
    "URL updated, please reload Valenced"
    ```

5. Restart Discord.

## 👷 Developing with Valenced Next

You'll need to have [Deno](https://deno.land/) installed. Once you have Deno, follow these steps:

```sh
# Install dependencies
deno install
```

---

```sh
# Build Valenced Next
deno run build

# Build Valenced Next with debugging enabled (slow, don't use in production)
deno run build --dev
```

```sh
# Start the development server
deno run dev

# Build as production
deno run dev --prod
```

<sub>Builds are generated at `dist/valenced.bundle`.</sub>

### 📝 Generating types

```sh
# Build types for external consumers
deno run types
```

Types are generated at `dist/types`. To consume, simply add `@valenced/types` as a dependency and include the following in your TypeScript config:

```json
{
  "compilerOptions": {
    "types": ["@valenced/types"]
  }
}
```

Your bundler will need to map imports to property access on `valenced` by reading `@valenced/types/modules.importmap.json`, which maps every rewritable module to its access path.

```json
{
  "@valenced/discord/modules/main_tabs_v2": "valenced.discord.modules.mainTabsV2",
  "@valenced/components/Page": {
    "global": "valenced.components.Page",
    "interop": "default"
  }
}
```

An `interop` of `default` means the property contains the module's default export, not its namespace. A namespace/wildcard import of such a module must be rewritten specially.

`@valenced/types/modules.json` lists every module name the package declares, including type-only modules, which have no property on `valenced` and so never appear in the import map.

#### 🛠️ Developer Mode & Hidden API

The [hidden API](./lib/hidden) exposes internal fields and methods that are **not** part of the public API. They can break or change at any time, and are only meant for debugging.
Some experimental APIs may also be exposed through the hidden API before they are added to the public API.

You must enable **Developer Mode** in Valenced's settings in order to expose the [hidden API](./lib/hidden) to plugins that depend on [`valenced.api.hidden`](./src/plugins/preinit/api.hidden).

To consume the hidden API, add the following to your TypeScript config:

```json
{
  "compilerOptions": {
    "types": ["@valenced/types", "@valenced/types/hidden"]
  }
}
```

Hidden modules are kept out of `modules.json` and `modules.importmap.json`, and out of the public API. They live in `modules.hidden.json` and `modules.hidden.importmap.json` instead, with paths under `valenced.hidden`.
