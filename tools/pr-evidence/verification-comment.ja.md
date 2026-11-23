## 検証結果（マージ前）

- 対象: {{ISSUE_AND_PR_SCOPE}}
- ブランチ: `{{BRANCH}}`
- 基準: `origin/main` @ `{{BASE_SHA}}`
- HEAD: `{{HEAD_SHA}}`
- マージ予定順: {{MERGE_ORDER_NOTE}}

### 変更内容

- {{CHANGE_SUMMARY_1}}
- {{CHANGE_SUMMARY_2}}

### ローカル検証

| コマンド | 結果 |
|---|---|
| `git fetch origin main` | {{FETCH_RESULT}} |
| `npx --yes @commitlint/cli --config commitlint.config.cjs --from origin/main --to HEAD` | {{COMMITLINT_RESULT}} |
| `node tools/build/verify-all.cjs` | {{VERIFY_ALL_RESULT}} |
| `.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache` | {{SPOTLESS_RESULT}} |
| `git diff --check` | {{DIFF_CHECK_RESULT}} |

補足: {{LOCAL_RESULT_DETAIL}}

### PR 必須チェック

- 必須チェック: {{REQUIRED_CHECKS_RESULT}}（{{CHECK_TIME_UTC}} 時点）
- 失敗または保留のチェック: {{FAILED_OR_PENDING_CHECKS}}
- CI がマージの最終判断権を持ちます。ローカルの成功をもって必須チェックの代替とはしません。

### 未実施・制限

- {{NOT_RUN_OR_LIMITATION}}

### ステータスページとイシュー

- `implementation-status.md` / `project-status.md` / `HANDOFF.md`: {{STATUS_PAGE_RESULT}}
- イシュー: {{ISSUE_STATE_NOTE}}

### マージ判断

- {{MERGE_DECISION}}
