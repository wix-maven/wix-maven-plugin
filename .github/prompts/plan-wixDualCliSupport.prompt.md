# Plan: WiX v3 + v4/5/6 Dual CLI Support

## TL;DR
Refactor the wix-maven-plugin to support both WiX Toolset v3 (separate tools: candle.exe, light.exe, etc.) and WiX v4+ (unified `wix.exe` CLI with subcommands), while preserving full backward compatibility for existing v3 POM configurations. Version is detected from the `wix.toolsPluginArtifactId` Maven artifact. New v4+ capabilities include the NuGet-style extension model and MSIX output type.

## Architecture Approach: Strategy Pattern

Introduce a `WixToolsetCommandBuilder` interface hierarchy that encapsulates version-specific CLI construction. Each Mojo delegates command-building to the appropriate builder. Maven goals and lifecycle phases remain identical — only the underlying CLI commands change.

```
WixToolsetCommandBuilder (interface)
├── WixV3CommandBuilder  (extracts current logic)
└── WixV4CommandBuilder  (new wix.exe unified CLI)
```

---

## Phase 1: Foundation — Version Detection & Builder Interface

### Step 1.1: Create `WixToolsetVersion` enum
- File: `src/main/java/com/github/wix_maven/WixToolsetVersion.java` (new)
- Values: `V3`, `V4_PLUS`
- Helper method: `detect(String toolsPluginArtifactId)` — maps artifact ID to version
  - `wix-toolset` → `V3` (existing default)
  - `wix-toolset4` (or configurable pattern) → `V4_PLUS`

### Step 1.2: Create `WixToolsetCommandBuilder` interface
- File: `src/main/java/com/github/wix_maven/WixToolsetCommandBuilder.java` (new)
- Methods matching each tool operation:
  - `File resolveToolExecutable(File toolDirectory, String toolName)` — returns path to the right exe
  - `Commandline buildCandleCommand(...)` — compile command
  - `Commandline buildLightCommand(...)` — link command for msi/msm/bundle
  - `Commandline buildLitCommand(...)` — link command for wixlib
  - `Commandline buildHeatCommand(...)` — harvest command
  - `Commandline buildTorchCommand(...)` — transform/diff command
  - `Commandline buildPyroCommand(...)` — patch application command
  - `Commandline buildInsigniaCommand(...)` — inscribe/detach/attach
  - `Commandline buildSmokeCommand(...)` — validation command
  - `void addGeneralOptions(Commandline cl)` — nologo, suppress, warn
  - `void addExtensions(Commandline cl, Set<Artifact> extensions)` — version-specific extension format
  - `String getToolSubdirectory()` — `"bin"` for v3, `""` or `"tools"` for v4

### Step 1.3: Add version field to `AbstractWixMojo`
- File: `src/main/java/com/github/wix_maven/AbstractWixMojo.java`
- Add `protected WixToolsetVersion wixVersion` field, lazily initialized from `toolsPluginArtifactId`
- Add `protected WixToolsetCommandBuilder getCommandBuilder()` factory method
- Keep all existing `@Parameter` fields — they remain the configuration surface

### Step 1.4: Update `ToolsetMojo`
- File: `src/main/java/com/github/wix_maven/ToolsetMojo.java`
- `unpackFileBasedResources()` must handle v4 layout (may not have `bin/` subfolder)
- Use `getCommandBuilder().getToolSubdirectory()` to determine extract path
- For v4: the tool artifact contains `wix.exe` (possibly at root or `tools/` level)

---

## Phase 2: Extract V3 Logic into `WixV3CommandBuilder`

### Step 2.1: Create `WixV3CommandBuilder`
- File: `src/main/java/com/github/wix_maven/WixV3CommandBuilder.java` (new)
- Extract current command-building logic from each Mojo into this class
- Key methods:
  - `resolveToolExecutable(toolDir, "candle")` → `new File(toolDir, "bin/candle.exe")`
  - `resolveToolExecutable(toolDir, "light")` → `new File(toolDir, "bin/light.exe")`
  - Same for lit, heat, torch, pyro, insignia, smoke
  - `addExtensions()` — adds `-ext <path-to-dll>` (current behavior)
  - `addGeneralOptions()` — `-nologo`, `-s<N>`, `-w<N>` (current behavior)

### Step 2.2: Refactor Mojos to use builder
- Files: `CandleMojo.java`, `LightMojo.java`, `LitMojo.java`, `HarvestMojo.java`, `PatchMojo.java`, `TransformMojo.java`, `SmokeMojo.java`, `InscribeMojo.java`, `DetachBundleEngineMojo.java`, `AttachBundleEngineMojo.java`
- Replace inline `new File(toolDirectory, "bin/candle.exe")` with `getCommandBuilder().resolveToolExecutable(toolDirectory, "candle")`
- Replace inline command-line argument building with builder delegation where it differs between versions
- Keep Mojo-specific logic (file scanning, dependency resolution, Maven integration) in the Mojos
- **Goal**: v3 behavior is identical, just routed through the builder

### Step 2.3: Verify — all existing integration tests pass unchanged
- No changes to integration tests in this phase
- Run `mvn verify -Prun-its` to confirm no regressions

---

## Phase 3: Implement `WixV4CommandBuilder`

### Step 3.1: Create `WixV4CommandBuilder`
- File: `src/main/java/com/github/wix_maven/WixV4CommandBuilder.java` (new)
- Key tool mappings:

| v3 Operation | v3 Tool | v4 Command | Notes |
|---|---|---|---|
| Compile | `candle.exe` | **Deferred to link** | v4 `wix build` is unified |
| Link (msi/msm) | `light.exe` | `wix.exe build -outputType Msi/Msm` | Includes compile |
| Link (wixlib) | `lit.exe` | `wix.exe build -outputType Library` | Includes compile |
| Link (bundle) | `light.exe` | `wix.exe build -outputType Bundle` | Includes compile |
| Harvest | `heat.exe` | `wix.exe heat` or built-in elements | Check v4+ heat support |
| Transform | `torch.exe` | `wix.exe msi transform` | May differ per v4 version |
| Patch | `pyro.exe` | `wix.exe msi patch` | Simplified in v4 |
| Inscribe | `insignia.exe` | `wix.exe burn detach/attach` | Bundle signing |
| Validate | `smoke.exe` | `wix.exe msi validate` | Built-in validation |

### Step 3.2: Handle the candle→light unification
- **CandleMojo in v4 mode**: Becomes a preparation step only
  - Gathers .wxs files, validates definitions, stores file list and definitions to shared state (via Maven properties or temp files)
  - Does NOT invoke any external tool
  - Returns immediately (the actual compilation is deferred to light/lit)
- **LightMojo/LitMojo in v4 mode**: Runs `wix.exe build` which compiles + links in one step
  - Picks up the .wxs files and definitions that CandleMojo would have gathered
  - Passes `-d` definitions, `-arch`, `-culture`, `-ext` etc. to `wix build`
  - Output: same .msi / .wixlib / .exe files

### Step 3.3: Map v4 CLI arguments
- v3 `-ext WixUIExtension` → v4 `-ext WixToolset.UI.wixext` (NuGet-style names)
  - Need an extension name mapping table or allow users to specify v4-style names
  - Could auto-detect: if the extension name contains `.`, it's v4 format already
- v3 `-cultures:en-US` → v4 `-culture en-US` (argument format may differ)
- v3 `-dFoo=Bar` → v4 `-d Foo=Bar` or `-define Foo=Bar`
- v3 `-nologo` → v4 may not have this flag (unified CLI)
- v3 `-arch x86` → v4 `-arch x86` or `-platform x86`
- v3 `-sval` → v4 equivalent validation suppression

### Step 3.4: v4 Extension model — string-based `<wixExtensions>`
- Add new `@Parameter` `Set<String> wixExtensions` on `AbstractWixMojo`
  - Users declare extension names directly: `<wixExtensions><ext>WixToolset.UI.wixext</ext></wixExtensions>`
  - Matches how `wix.exe -ext <name>` works in direct usage
- In v4 mode, `addWixExtensions()` iterates `wixExtensions` set and passes each as `-ext <name>` (string, not file path)
- In v3 mode, `wixExtensions` is ignored (existing Maven `<type>wixext</type>` dep model remains unchanged)
- Validation: if v4 mode and `wixExtensions` is empty but `getExtDependencySets()` finds `wixext` type Maven deps, log a warning suggesting migration to `<wixExtensions>`
- v3 `getExtDependencySets()` codepath unchanged — still resolves DLL file paths from Maven artifacts
- Document v3→v4 extension name mapping as migration reference

---

## Phase 4: New Capabilities

### Step 4.1: MSIX output type (*parallel with 3.x*)
- Add `msix` to `PACK_*` constants in `AbstractWixMojo`
- Add `msix` ArtifactHandler in `components.xml`
- Add `msix` lifecycle mapping in `components.xml` (similar to `msi`)
- Validate: only works with v4+ toolset (throw clear error if v3)

### Step 4.2: ARM64 platform support
- `getPlatforms()` in `AbstractWixMojo` already supports arbitrary platform strings
- Validate ARM64 against toolset version (v4+ only for arm64)
- May need to adjust `-arch arm64` mapping if v4 uses different syntax

### Step 4.3: v4 Extension management — documentation
- Create a migration doc section listing v3→v4 extension name mapping:
  - `WixUIExtension` → `WixToolset.UI.wixext`
  - `WixUtilExtension` → `WixToolset.Util.wixext`
  - `WixBalExtension` → `WixToolset.Bal.wixext`
  - `WixIISExtension` → `WixToolset.Iis.wixext`
  - `WixVSExtension` → `WixToolset.VisualStudio.wixext`
  - `WixNetFxExtension` → `WixToolset.Netfx.wixext`
  - `WixFirewallExtension` → `WixToolset.Firewall.wixext`
  - `WixDirectXExtension` → `WixToolset.DirectX.wixext`
  - `WixHttpExtension` → `WixToolset.Http.wixext`
- Document POM changes: v3 uses `<type>wixext</type><classifier>WixUIExtension</classifier>` from `wix-toolset`; v4 uses `<wixExtensions><ext>WixToolset.UI.wixext</ext></wixExtensions>` in plugin configuration

---

## Phase 5: Integration Tests

### Step 5.1: v4 test infrastructure
- Create `src/it/it-parent-v4/pom.xml` — v4 common parent config
  - Sets `wix.toolsPluginArtifactId` to `wix-toolset4`
  - References appropriate v4 toolset Maven artifact
- Create v4 toolset Maven artifact for tests (or document how to provide it)

### Step 5.2: v4 integration tests (*parallel steps, depends on 5.1*)
- `it1001` — Basic MSI with v4 (mirrors it0001)
- `it1004` — MSI lifecycle with v4 (mirrors it0004)
- `it1005` — Definitions and dependencies with v4 (mirrors it0005)
- `it1010` — Multi-module with v4 (mirrors it0010)
- `it1020` — Harvest with v4 (mirrors it0020)

### Step 5.3: Dual-version CI matrix
- Update CI/build to test both v3 and v4 toolset configurations
- Consider Maven profiles: `-Pwix3` and `-Pwix4`

---

## Relevant Files

**New files:**
- `src/main/java/com/github/wix_maven/WixToolsetVersion.java` — version enum + detection
- `src/main/java/com/github/wix_maven/WixToolsetCommandBuilder.java` — builder interface
- `src/main/java/com/github/wix_maven/WixV3CommandBuilder.java` — extracted v3 logic
- `src/main/java/com/github/wix_maven/WixV4CommandBuilder.java` — new v4 CLI logic

**Modified files:**
- `src/main/java/com/github/wix_maven/AbstractWixMojo.java` — add version detection, builder factory, keep existing params
- `src/main/java/com/github/wix_maven/ToolsetMojo.java` — handle v4 tool layout
- `src/main/java/com/github/wix_maven/CandleMojo.java` — delegate to builder; in v4 mode, become no-op prep step
- `src/main/java/com/github/wix_maven/LightMojo.java` — delegate to builder; in v4 mode, run `wix build`
- `src/main/java/com/github/wix_maven/LitMojo.java` — delegate to builder; in v4 mode, run `wix build -outputType Library`
- `src/main/java/com/github/wix_maven/HarvestMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/AbstractTorchMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/PatchMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/TransformMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/SmokeMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/AbstractInsigniaMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/InscribeMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/DetachBundleEngineMojo.java` — delegate to builder
- `src/main/java/com/github/wix_maven/AttachBundleEngineMojo.java` — delegate to builder
- `src/main/resources/META-INF/plexus/components.xml` — add `msix` artifact handler + lifecycle
- `pom.xml` — update default wix tools version, potentially add v4 dependency

**Test files (new):**
- `src/it/it-parent-v4/pom.xml`
- `src/it/it1001/` through `src/it/it1020/` (v4 test mirrors)

---

## Verification

1. **Phase 2 gate**: Run `mvn verify -Prun-its` — all existing v3 integration tests pass with refactored builder delegation (no behavior change)
2. **Phase 3 gate**: Run v4 smoke test — `it1001` (basic MSI) builds successfully with `wix.exe build`
3. **Phase 4 gate**: Run MSIX test with v4 toolset, confirm `.msix` output artifact attached
4. **Phase 5 gate**: Full CI matrix — both `-Pwix3` and `-Pwix4` profiles green
5. **Manual**: Verify an existing real-world v3 project builds without POM changes after plugin upgrade

---

## Decisions

- **Version detection**: Based on `wix.toolsPluginArtifactId` artifact (user's choice), not runtime detection
- **Tool provisioning**: Maven artifact only (no PATH discovery), matching current v3 approach
- **Backward compatibility**: Full — existing v3 POMs work unchanged
- **Candle in v4**: Becomes a no-op preparation step; actual compilation happens in light/lit goal via `wix build`
- **Extension naming**: Users must update extension dependencies for v4+. No auto-translation. Document the v3→v4 mapping as reference.
- **v4 extension model**: String-based — new `<wixExtensions>` config element. Users declare NuGet extension names directly, plugin passes them as `-ext <name>` to `wix.exe`. Matches native wix.exe usage. v3 Maven dep model (`type=wixext`) unchanged.
- **v4+ builder structure**: Single `WixV4CommandBuilder`. Expand with version sub-strategies only when a concrete breaking CLI change between v4/v5/v6 is identified.
- **v4 toolset Maven artifact**: Separate packaging project (outside this repo) that downloads official WiX v4+ and repackages as Maven artifact (e.g., `org.wixtoolset.maven:wix-toolset4`).
- **Scope included**: v4/5/6 CLI, NuGet extensions, MSIX output type
- **Scope excluded**: Cross-platform (Linux/Mac) support, `wix convert` migration tooling, ARM64 (deferred to follow-up)
