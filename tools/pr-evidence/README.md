# Pull request evidence templates

These templates standardize the Japanese owner-facing comments required by rule 22 in
[AGENTS.md](../../AGENTS.md) and by [the parallel workflow](../../docs/development/parallel-workflow.md).
They are localized operational content; do not replace them with English comments unless the owner asks.

## Files

| Template | When to use it |
|---|---|
| `verification-comment.ja.md` | After local checks and before merge, once the PR's required checks are known |
| `post-merge-evidence.ja.md` | After merge, once post-merge CI, CodeQL, and Scorecard results are known |

## Usage

1. Copy the template and replace every `{{PLACEHOLDER}}` with an observed value.
2. For commands, paste the exact command and its actual result. Write `未実施` for anything not run.
   Never write `pass`, `成功`, or a run ID that was not observed.
3. For intermediate implementation PRs, write that the issue remains open and status pages were not
   edited. Only the final consolidated evidence PR records `Closes #NNN` and status updates.
4. Check GitHub's required checks directly. The local commands do not replace CI as merge authority.
5. Post the rendered body with `gh pr comment {{PR_NUMBER}} --body-file <rendered-file>`, or paste it
   into the PR conversation.

Keep placeholder values short and factual. Run URLs and status-page links are optional, but if they are
included they must match the observed run. Record `N/A` for items that do not apply to the PR, with a
one-line reason.
