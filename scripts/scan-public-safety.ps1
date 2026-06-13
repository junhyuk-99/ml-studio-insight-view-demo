param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
)

$ErrorActionPreference = "Stop"

$excludeDirs = @(
    "\\.git\\",
    "\\node_modules\\",
    "\\dist\\",
    "\\build\\",
    "\\target\\",
    "\\.gradle\\",
    "\\.venv\\",
    "\\__pycache__\\"
)

$binaryExtensions = @(".jpg", ".jpeg", ".png", ".gif", ".ico", ".pdf", ".jar", ".zip", ".7z", ".rar")

$patterns = @(
    @{ Name = "IPv4 address"; Regex = "\b(?!(?:127|0)\.0\.0\.1\b)(?!0\.0\.0\.0\b)(?!(?:10|172\.(?:1[6-9]|2\d|3[0-1])|192\.168)\.)\d{1,3}(?:\.\d{1,3}){3}\b" },
    @{ Name = "Non-local MongoDB URI"; Regex = "mongodb(?:\+srv)?:\/\/(?!(?:localhost|127\.0\.0\.1)(?::\d+)?(?:\/|$))[^\s""']+" },
    @{ Name = "Secret assignment"; Regex = "(?i)\b(password|passwd|secret|token|api[_-]?key)\b\s*=\s*[^#\s]+" },
    @{ Name = "Production path hint"; Regex = "(?i)(/prod/|\\prod\\|/production/|\\production\\)" },
    @{ Name = "Model artifact"; Regex = "(?i)\.(joblib|pkl)$" }
)

$files = Get-ChildItem -Path $Root -Recurse -File -Force | Where-Object {
    $path = $_.FullName
    (-not ($excludeDirs | Where-Object { $path -match $_ })) -and
    ($binaryExtensions -notcontains $_.Extension.ToLowerInvariant())
}

$findings = New-Object System.Collections.Generic.List[string]

foreach ($file in $files) {
    $relative = Resolve-Path -Path $file.FullName -Relative

    if ($file.Name -eq ".env") {
        $findings.Add("${relative}:1 [.env file] Untracked local environment file should not be published.")
        continue
    }

    if ($file.Name -eq ".gitignore" -or $relative -eq ".\scripts\scan-public-safety.ps1") {
        continue
    }

    if ($file.Length -gt 2MB) {
        continue
    }

    $lineNo = 0
    foreach ($line in Get-Content -LiteralPath $file.FullName -Encoding UTF8) {
        $lineNo += 1

        foreach ($pattern in $patterns) {
            if ($line -match $pattern.Regex) {
                if ($pattern.Name -eq "Non-local MongoDB URI" -and $line -match "(localhost|127\.0\.0\.1|mongodb://mongo:)") {
                    continue
                }
                $findings.Add("${relative}:${lineNo} [$($pattern.Name)]")
            }
        }
    }
}

if ($findings.Count -eq 0) {
    Write-Output "Public safety scan passed. No risky public-release patterns were found."
    exit 0
}

Write-Output "Public safety scan found $($findings.Count) item(s):"
$findings | Sort-Object -Unique | ForEach-Object { Write-Output $_ }
exit 1
