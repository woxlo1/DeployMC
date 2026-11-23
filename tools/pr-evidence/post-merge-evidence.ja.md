## マージ後エビデンス

- 対象: {{ISSUE_AND_PR_SCOPE}}
- マージ方法: rebase merge
- マージコミット: `{{MERGE_COMMIT_SHA}}`
- マージ時刻: {{MERGED_AT_UTC}}
- main の状態: `{{MAIN_HEAD_SHA}}`

### マージ前検証の再掲

| 項目 | 結果 |
|---|---|
| ローカル `node tools/build/verify-all.cjs` | {{VERIFY_ALL_RESULT}} |
| ローカル `.\gradlew.bat spotlessCheck --console=plain --no-configuration-cache` | {{SPOTLESS_RESULT}} |
| その他のローカル検証 | {{OTHER_LOCAL_RESULT}} |
| PR 必須チェック | {{REQUIRED_CHECKS_RESULT}} |

### マージ後ワークフロー

| ワークフロー | Run ID | 結果 |
|---|---|---|
| CI | {{CI_RUN_ID}} | {{CI_RESULT}} |
| CodeQL | {{CODEQL_RUN_ID}} | {{CODEQL_RESULT}} |
| Scorecard | {{SCORECARD_RUN_ID}} | {{SCORECARD_RESULT}} |

失敗したワークフローがある場合は、再実行結果と原因を {{WORKFLOW_FAILURE_DETAIL}} に記載します。

### ドキュメントとイシュー

- 更新したステータスページ: {{UPDATED_STATUS_PAGES}}
- イシュー: {{ISSUE_NUMBER}} を {{ISSUE_STATE}} として {{ISSUE_CLOSE_REASON}}（{{ISSUE_CLOSED_AT_UTC}}）
- 最終エビデンス PR: {{FINAL_EVIDENCE_PR_NOTE}}

### 残存リスク・未実施項目

- {{REMAINING_LIMITATION}}

### 結論

- {{FINAL_CONCLUSION}}
