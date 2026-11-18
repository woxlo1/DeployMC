# Naming check: DeployMC

- **Prepared:** 2026-11-18
- **Scope:** repository names and package-registry names checked for the DeployMC project.
- **Standard:** every factual claim below has a source link and an explicit access date. Local
  commands were run on 2026-11-18 from the repository root.

## Result

**Assessment, not an originality claim:** the exact name "DeployMC" is already used by unrelated
GitHub repositories. No matching package was found in npm, crates.io, PyPI, or Maven Central at the
time checked. The project can keep the name, but its documentation must not claim uniqueness, and
registry names should be reserved before publication and rechecked at each release.

## GitHub repository names

| Finding | Source | Accessed |
|---|---|---|
| Repository search for `DeployMC` returned `FarNys/DeployMC` and `nyaundid/deploymc` in addition to this project. | [GitHub repository search](https://github.com/search?q=DeployMC&type=repositories) | 2026-11-18 |
| `FarNys/DeployMC` has no description, was created on 2021-12-12, updated on 2021-12-13, and is not archived. | [GitHub REST API](https://api.github.com/repos/FarNys/DeployMC) | 2026-11-18 |
| `nyaundid/deploymc` has no description, was created and updated on 2019-09-04, and is not archived. | [GitHub REST API](https://api.github.com/repos/nyaundid/deploymc) | 2026-11-18 |
| `woxlo1/DeployMC` is this project; its description is "Secure, collaborative plugin deployment and file management for Minecraft servers, with a first-class public API." | [GitHub REST API](https://api.github.com/repos/woxlo1/DeployMC) | 2026-11-18 |
| Search also returned adjacent names such as `tnacomtr/deploymcserver`, `Alfredooe/DeployMC-Neo`, `deployMCtokens/deployMCtokens`, and `molka-makri/DeployMcp`. | [GitHub repository search](https://github.com/search?q=DeployMC&type=repositories) | 2026-11-18 |
| Repository search results are returned by GitHub's repository search; this is a name check, not a trademark or legal clearance. | [GitHub Docs: Searching for repositories](https://docs.github.com/en/search-github/searching-on-github/searching-for-repositories) | 2026-11-18 |

Exact local commands and results:

```text
$ gh search repos DeployMC --limit 30 --json fullName,description,url
# Included: FarNys/DeployMC, nyaundid/deploymc, woxlo1/DeployMC,
#           tnacomtr/deploymcserver, Alfredooe/DeployMC-Neo,
#           deployMCtokens/deployMCtokens, molka-makri/DeployMcp

$ gh api repos/FarNys/DeployMC --jq '[.full_name,.description,.created_at,.updated_at,.html_url,.archived] | @tsv'
FarNys/DeployMC		2021-12-12T21:51:08Z	2021-12-13T05:15:07Z	https://github.com/FarNys/DeployMC	false

$ gh api repos/nyaundid/deploymc --jq '[.full_name,.description,.created_at,.updated_at,.html_url,.archived] | @tsv'
nyaundid/deploymc		2019-09-04T16:45:58Z	2019-09-04T16:45:58Z	https://github.com/nyaundid/deploymc	false
```

## Package registries

| Finding | Source | Accessed |
|---|---|---|
| npm has no package named `deploymc`: the registry returned HTTP 404. | [npm registry entry](https://registry.npmjs.org/deploymc) | 2026-11-18 |
| crates.io search for `deploymc` returned `meta.total = 0`. | [crates.io search API](https://crates.io/api/v1/crates?q=deploymc&per_page=10) | 2026-11-18 |
| PyPI has no `deploymc` project: the JSON API returned HTTP 404. | [PyPI JSON API](https://pypi.org/pypi/deploymc/json) | 2026-11-18 |
| Maven Central search for `deploymc` returned `response.numFound = 0`. | [Maven Central Solr search API](https://search.maven.org/solrsearch/select?q=deploymc&rows=20&wt=json) | 2026-11-18 |

Exact local commands and results:

```text
$ npm view deploymc name version
npm error code E404
npm error 404 Not Found - GET https://registry.npmjs.org/deploymc - Not found
(exit code 1)

$ cargo search deploymc --limit 10
(no output)

$ Invoke-RestMethod -Uri 'https://crates.io/api/v1/crates?q=deploymc&per_page=10'
crates count: 0

$ Invoke-RestMethod -Uri 'https://pypi.org/pypi/deploymc/json'
HTTP 404 (localized PowerShell error text omitted)

$ Invoke-RestMethod -Uri 'https://search.maven.org/solrsearch/select?q=deploymc&rows=20&wt=json'
maven numFound: 0
```

`cargo search` produced no output; the crates.io API result above is the recorded evidence for that
registry (accessed 2026-11-18 at the linked endpoint).

## Risks and recommendation

| Risk or recommendation | Basis | Accessed |
|---|---|---|
| Risk: two exact-name GitHub repositories exist and are not archived, so users can confuse them with this project. | [FarNys/DeployMC](https://github.com/FarNys/DeployMC), [nyaundid/deploymc](https://github.com/nyaundid/deploymc) | 2026-11-18 |
| Risk: registry availability changes; an empty result on one date is not a permanent reservation. | [npm registry](https://registry.npmjs.org/deploymc), [crates.io API](https://crates.io/api/v1/crates?q=deploymc&per_page=10), [PyPI API](https://pypi.org/pypi/deploymc/json), [Maven Central API](https://search.maven.org/solrsearch/select?q=deploymc&rows=20&wt=json) | 2026-11-18 |
| Gap: no trademark database or legal review was performed; this document is not legal advice. | [GitHub Docs: Searching for repositories](https://docs.github.com/en/search-github/searching-on-github/searching-for-repositories) describes repository search only, not trademark search. | 2026-11-18 |
| Recommendation (assessment): keep `DeployMC` as the project and repository name, but never claim it is unique or original. | Findings in the tables above. | 2026-11-18 |
| Recommendation (assessment): reserve `deploymc` in each registry before first publication and repeat the checks in the release checklist. | Findings in the tables above; release gates are described in [ROADMAP.md](../../ROADMAP.md). | 2026-11-18 |

## Method note

Repository and package searches were performed through the GitHub CLI, npm CLI, Cargo CLI, and the
public HTTPS APIs linked above on 2026-11-18. No claim of ownership, originality, or trademark
availability is made.
