# Spike 2: Windows JAR replacement

Status: `OBSERVED` locally on Windows; `SOURCE-VERIFIED` against Microsoft documentation. No real
Minecraft server was used.

## Question

Can an in-use plugin JAR be replaced on Windows, and what does that imply for the Phase 5 deployment
design?

## External evidence

Sources accessed 2026-11-23:

- [Moving and Replacing Files](https://learn.microsoft.com/en-us/windows/win32/fileio/moving-and-replacing-files)
  states that `ReplaceFile` replaces one file with another and preserves attributes of the original,
  and that a destination file that already exists and is open must have been opened with applicable
  sharing permissions.
- [ReplaceFileW](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-replacefilew)
  documents the replacement API and the required delete permissions on the destination.

Paper's [updating guide](https://docs.papermc.io/paper/updating/) explicitly advises not replacing a
JAR in a running server unless the consequences are understood. That is operator guidance, not a
claim about every server implementation.

## Local probe

The reproducible script is
[`tests/spikes/windows-jar-replacement.ps1`](../../../tests/spikes/windows-jar-replacement.ps1).
It uses text files as JAR stand-ins because the relevant behavior is Win32 file sharing, not ZIP
contents. From the repository root on Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tests/spikes/windows-jar-replacement.ps1
```

Observed output on 2026-11-23:

```text
replace-open-without-share-delete=fail: MethodInvocationException inner=IOException hresult=0x80070020
replace-open-with-share-delete=pass
target-content=new-share-delete
backup-content=old-share-delete
dotnet=4.0.30319.42000
```

The destination was opened once with `FileShare.None` and once with
`FileShare.ReadWrite | FileShare.Delete`. `File.Replace` reached a sharing violation in the first
case and succeeded in the second, leaving the original content in the backup file.

## Result

Windows replacement is conditional on how the running server opened the JAR. A design that assumes
an unconditional in-place overwrite would fail closed on a locked JAR. The safer Phase 5 strategy is
to keep update-folder or restart-time installation as the default path and treat in-place replacement
as an optional, capability-gated path that must be verified against a real server before use.

## Not verified

No Paper, Spigot, proxy, or Folia server opened a real JAR during this spike. The probe does not
show how any particular server opens its plugin files, does not test antivirus or OneDrive locks, and
does not prove that `ReplaceFile` is atomic for every filesystem. No deployment capability or support
tier is claimed from it.
