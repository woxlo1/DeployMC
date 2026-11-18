# License comparison

- **Prepared:** 2026-11-18
- **Decision already made:** the owner selected Apache License 2.0 on 2026-11-16. This document
  records the comparison behind that decision; it does not reopen it. See
  [ADR-0013](../adr/ADR-0013-license.md) and
  [decisions-pending.md](../development/decisions-pending.md) (read 2026-11-18).
- **Standard:** every factual claim has a source link and an explicit access date. **Assessment**
  marks this project's interpretation. This is not legal advice; no legal review was performed.

## Options compared

| License and sourced characteristics | Source | Accessed |
|---|---|---|
| MIT permits use, copying, modification, merging, publication, distribution, sublicensing, and sale, provided the copyright and permission notices are included; the software is provided "as is" without warranty. | [OSI: The MIT License](https://opensource.org/license/mit) | 2026-11-18 |
| Apache-2.0 grants a perpetual, worldwide, non-exclusive, royalty-free, irrevocable patent license from each contributor, subject to the license's conditions and termination rules. | [Apache License 2.0, section 3](https://www.apache.org/licenses/LICENSE-2.0.txt) | 2026-11-18 |
| Apache-2.0 requires recipients to retain copyright, patent, trademark, and attribution notices and to state changes to files; a `NOTICE` file, when present in the work, must be preserved in distributions. | [Apache License 2.0, section 4](https://www.apache.org/licenses/LICENSE-2.0.txt) | 2026-11-18 |
| GPL-3.0 is a free, copyleft license; the GNU project's quick guide explains that modified versions distributed to users must be licensed under GPLv3 with source available. | [GNU quick guide to GPLv3](https://www.gnu.org/licenses/quick-guide-gplv3.en.html), [GNU GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html) | 2026-11-18 |
| AGPL-3.0 extends GPLv3's copyleft to network use: users interacting with a modified program over a network must be able to obtain the corresponding source. | [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.en.html) | 2026-11-18 |
| The GNU license pages were reachable with HTTP 200 from this host on 2026-11-18, but detailed page extraction timed out during this review; the GNU pages and their published summaries were used, and a legal reviewer should re-read the full texts before any relicensing. | [GNU quick guide to GPLv3](https://www.gnu.org/licenses/quick-guide-gplv3.en.html), [GNU AGPLv3](https://www.gnu.org/licenses/agpl-3.0.en.html) | 2026-11-18 |

## Practical comparison for DeployMC

| Criterion | MIT | Apache-2.0 | GPL-3.0 | AGPL-3.0 | Basis |
|---|---|---|---|---|---|
| Permissive vs copyleft | Permissive | Permissive | Strong copyleft | Strong copyleft, including network use | [MIT](https://opensource.org/license/mit), [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0.txt), [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html), [AGPLv3](https://www.gnu.org/licenses/agpl-3.0.en.html), all accessed 2026-11-18 |
| Explicit contributor patent grant | Not in the OSI license text | Yes, section 3 | Not chosen for this reason | Not chosen for this reason | [MIT](https://opensource.org/license/mit), [Apache-2.0 section 3](https://www.apache.org/licenses/LICENSE-2.0.txt), accessed 2026-11-18 |
| Attribution and notice burden | Retain copyright and permission notice | Retain notices, state changes, preserve `NOTICE` | License and source obligations for distributed modifications | Same plus network-source obligations | [MIT](https://opensource.org/license/mit), [Apache-2.0 section 4](https://www.apache.org/licenses/LICENSE-2.0.txt), [GPLv3](https://www.gnu.org/licenses/gpl-3.0.en.html), [AGPLv3](https://www.gnu.org/licenses/agpl-3.0.en.html), accessed 2026-11-18 |
| Fit for a self-hosted plugin exposing an HTTP API | **Assessment:** simple, but no patent grant | **Assessment:** good: patent grant, familiar to Java users, no network-copyleft obligation | **Assessment:** copyleft would reach distributed modifications | **Assessment:** network-copyleft is a poor fit for a self-hosted server tool | License texts linked above, accessed 2026-11-18; project design in [ADR-0013](../adr/ADR-0013-license.md), read 2026-11-18 |

## Bukkit, Spigot, and Paper considerations

| Finding | Source | Accessed |
|---|---|---|
| Paper states that it inherits its licensing from upstream projects and is licensed under GPLv3, inherited from Spigot, which in turn inherits from Bukkit and CraftBukkit. | [Paper LICENSE.md](https://raw.githubusercontent.com/PaperMC/Paper/master/LICENSE.md) | 2026-11-18 |
| Paper states that authors not listed should be presumed to have released their work under the original GPL, while contributors may choose MIT and the listed authors have chosen MIT. | [Paper LICENSE.md](https://raw.githubusercontent.com/PaperMC/Paper/master/LICENSE.md) | 2026-11-18 |
| DeployMC's ADR-0013 requires bundled dependencies to be compatible with Apache-2.0 and with distribution inside the plugin JAR, and forbids bundling GPL-only or otherwise incompatible dependencies without a documented, owner-accepted exception. | [ADR-0013](../adr/ADR-0013-license.md) | 2026-11-18 |
| No third-party runtime dependency is bundled today; Shadow is configured but the produced JAR is an empty scaffold. | [implementation status](../development/implementation-status.md) | 2026-11-18 |

**Assessment:** compiling against platform APIs and bundling third-party code are different
questions. The distribution check must be repeated for each dependency when it is actually added,
including relocated/shaded packages. Do not infer that a dependency is safe solely because a
similar project uses it.

## Recommendation and follow-up

| Recommendation | Basis | Accessed |
|---|---|---|
| Keep Apache-2.0 for repository code, the Java API, and SDKs, as already decided. | [ADR-0013](../adr/ADR-0013-license.md), [decisions-pending.md](../development/decisions-pending.md) | 2026-11-18 |
| Add a third-party notice file and preserve `NOTICE` obligations when the first dependency is bundled. | [Apache-2.0 section 4](https://www.apache.org/licenses/LICENSE-2.0.txt), [ADR-0013](../adr/ADR-0013-license.md) | 2026-11-18 |
| Verify each new runtime dependency's license and distribution terms before adding it; reject or replace GPL-only dependencies for the bundled JAR unless the owner accepts a documented exception. | [Paper LICENSE.md](https://raw.githubusercontent.com/PaperMC/Paper/master/LICENSE.md), [ADR-0013](../adr/ADR-0013-license.md) | 2026-11-18 |
| Do not change the license without a new ADR and an owner decision; this document is not a legal opinion and no legal review has been performed. | [ADR-0013](../adr/ADR-0013-license.md) | 2026-11-18 |
