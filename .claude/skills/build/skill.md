---
name: build
description: This skill should be used when the user asks to "build", "compile", "run build", "check if it compiles", or wants to verify the project builds successfully. Use this skill before committing or after making code changes to confirm the build passes.
version: 0.1.0
---

# Build the FinanceApp

To build the project, use the **Monitor tool** (the Bash tool is not available in this environment). `build.ps1` sets `JAVA_HOME` to the Android Studio JBR and writes all output to `.tmp/gradle/log.txt`.

## Steps

1. Run the build via Monitor (timeout 600000ms, persistent false):

```
command: pwsh -File build.ps1; tail -30 .tmp/gradle/log.txt
description: Build FinanceApp
```

2. Wait for the Monitor notification. The last 30 log lines stream as events.

3. Report the outcome:
   - `BUILD SUCCESSFUL` → build passed
   - `BUILD FAILED` or any `e:` lines → show the relevant error lines to the user

## Notes

- Use `pwsh`, not `powershell` — `powershell` is not on PATH in this shell environment.
- Always run from the project root — never `cd` into a subdirectory first.
- Log paths use forward slashes (`/`) for the bash side (`tail`), backslashes for the PowerShell side.
- The full log is at `.tmp/gradle/log.txt` if more context is needed.
- Kotlin compiler errors appear as lines starting with `e:`.
