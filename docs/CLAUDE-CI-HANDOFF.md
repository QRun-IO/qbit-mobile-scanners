# CI helper crash handoff (Windows helper job)

Scope: PSScriptAnalyzer 1.25.0 throws NullReferenceException on
`integrations/record-files/Verify-Isolated.ps1` only (root reproduced; the other
five analyzed scripts report zero findings). Run 41337798368 was the hosted failure.

## Changes

- `integrations/record-files/Verify-Isolated.ps1`: the final two statements built
  one-line hashtables containing a `$(if ... else ...)` subexpression and several
  nested `(Get-FileHash ...)` calls, piped directly to `ConvertTo-Json`. That is
  the only construct in the repository's scripts combining those features, and
  the alignment/whitespace rules inspect hashtable key/value extents. The
  values are now computed into plain variables first and the two hashtables
  are multi-line with aligned `=`. Output keys, values and file names are
  unchanged. No rule, setting, version pin or test was relaxed.
- `.github/workflows/demo-acceptance.yml`: each file is announced before it is
  analyzed. A crash still terminates the step (no try/catch), and the last
  `Analyzing ...` line names the file.

## Confidence

The exact crashing AST node was not reproduced by me (no shell). The two
rewritten statements are the strongest candidate, not a proven cause. If the
analyzer still crashes, the next suspects in the same file are the one-line
hashtables at the `$manifest` and `$compiled` loops and the
`foreach($x in $a | Where-Object ...)` headers; extract them into variables the
same way. Pin a different analyzer version only if a minimal reproduction
shows a bug that source changes cannot avoid.

## Verification for the coordinator (read-only analysis)

```powershell
# from R:\Git.Local\QRunIO\qbit-mobile-scanners
pwsh -NoProfile -Command "Get-ChildItem -LiteralPath scripts,integrations/record-files -File -Filter *.ps1 | ForEach-Object { try { $r=@(Invoke-ScriptAnalyzer -Path $_.FullName -Settings scripts/PSScriptAnalyzerSettings.psd1); '{0}: {1} findings' -f $_.Name,$r.Count; $r | Format-Table RuleName,Line,Message -AutoSize } catch { '{0}: CRASH {1}' -f $_.Name,$_.Exception.GetType().Name } }"

# parser check of the edited script
pwsh -NoProfile -Command "$e=$null;$t=$null;[void][Management.Automation.Language.Parser]::ParseFile((Resolve-Path integrations/record-files/Verify-Isolated.ps1),[ref]$t,[ref]$e);$e.Count"

# existing path regression
pwsh -NoProfile -File integrations/record-files/Test-IsolatedBuildPaths.ps1
```

Expected: every file reports 0 findings with no CRASH, the parser prints 0, and
the path regression passes. Any new rule findings in the rewritten block must be
fixed in source, not suppressed.
