[CmdletBinding()]
param(
   [string]$Bundle = 'R:\Git.Local\QRunIO\qbit-documents\artifacts\retained-contract-20261031-0352',
   [switch]$SkipSeed,
   [switch]$SkipBaseBuild
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'IsolatedBuildPaths.ps1')
$module = $PSScriptRoot
$scanner = (Resolve-Path (Join-Path $module '../..')).Path
$target = Join-Path $module 'target'
$repository = Join-Path $target 'isolated-m2'
$source = Join-Path $target 'scanner-source'
New-Item -ItemType Directory -Force -Path $repository | Out-Null
$successMarker = Join-Path $target 'verification-success.json'
if(Test-Path -LiteralPath $successMarker) { Remove-Item -LiteralPath $successMarker }
$bundleRoot = (Resolve-Path -LiteralPath $Bundle).Path
$expected = Get-Content -LiteralPath (Join-Path $bundleRoot 'sha256.json') -Raw | ConvertFrom-Json
foreach($entry in $expected)
{
   $path = (Resolve-Path -LiteralPath (Join-Path $bundleRoot $entry.path)).Path
   if(!$path.StartsWith($bundleRoot + '\') -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256)
   {
      throw "Retained contract checksum mismatch: $($entry.path)"
   }
}
Copy-Item -LiteralPath (Join-Path $bundleRoot 'sha256.json') -Destination (Join-Path $target 'consumed-bundle-sha256.json')
$expectedSource = Get-Content -LiteralPath (Join-Path $bundleRoot 'source-sha256.json') -Raw | ConvertFrom-Json
foreach($entry in $expectedSource)
{
   $path = (Resolve-Path -LiteralPath (Join-Path $bundleRoot ('source/' + $entry.path))).Path
   if(!$path.StartsWith($bundleRoot + '\source\') -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $entry.sha256)
   {
      throw "Retained source checksum mismatch: $($entry.path)"
   }
}
Copy-Item -LiteralPath (Join-Path $bundleRoot 'source-sha256.json') -Destination (Join-Path $target 'consumed-source-sha256.json')
if(!$SkipSeed)
{
   # Read-only seed: never install frozen candidates into the shared Maven cache.
   & robocopy (Join-Path $env:USERPROFILE '.m2/repository') $repository /E /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null
   if($LASTEXITCODE -ge 8) { throw 'Isolated Maven repository seeding failed.' }
}
if(!$SkipBaseBuild)
{
   Initialize-IsolatedScannerSource -ScannerRoot $scanner -ModuleRoot $module
}
$wrapper = Join-Path $source 'mvnw.cmd'
if(!(Test-Path -LiteralPath $wrapper)) { throw 'A verified base snapshot is required; omit SkipBaseBuild.' }
function Invoke-Maven([string[]]$Arguments)
{
   & $wrapper "-Dmaven.repo.local=$repository" @Arguments
   if($LASTEXITCODE -ne 0) { throw "Maven failed with exit code $LASTEXITCODE" }
}
if(!$SkipBaseBuild)
{
   Push-Location $source
   try { Invoke-Maven @('-B','-ntp','clean','verify','install') }
   finally { Pop-Location }
}
$collaborationPom = Join-Path $target 'qbit-collaboration-resolved.pom.xml'
Copy-Item -LiteralPath (Join-Path $bundleRoot 'qbit-collaboration.resolved.pom.xml') -Destination $collaborationPom
$artifacts = @(
   @('qbit-record-files-0.1.0-SNAPSHOT.jar','qbit-record-files.pom.xml'),
   @('qbit-collaboration-0.1.0-record-files-SNAPSHOT.jar','qbit-collaboration.pom.xml'),
   @('qqq-backend-core-4.1.0-isolated-record-files-SNAPSHOT.jar','qqq-backend-core.pom.xml'),
   @('qqq-backend-module-rdbms-4.1.0-isolated-record-files-SNAPSHOT.jar','qqq-backend-module-rdbms.pom.xml')
)
Push-Location $module
try
{
   foreach($artifact in $artifacts)
   {
      $pom = if($artifact[1] -eq 'qbit-collaboration.pom.xml') { $collaborationPom } else { Join-Path $bundleRoot $artifact[1] }
      Invoke-Maven @('-B','-ntp','org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file',"-Dfile=$(Join-Path $bundleRoot $artifact[0])","-DpomFile=$pom")
   }
   Invoke-Maven @('-B','-ntp','org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file',"-Dfile=$(Join-Path $bundleRoot 'qbit-record-files-0.1.0-SNAPSHOT-tests.jar')","-DpomFile=$(Join-Path $bundleRoot 'qbit-record-files.pom.xml')",'-DgroupId=com.kingsrook.qbits','-DartifactId=qbit-record-files','-Dversion=0.1.0-SNAPSHOT','-Dclassifier=tests','-Dpackaging=jar','-DgeneratePom=false')
   Remove-IsolatedBuildDirectory -ModuleRoot $module -LeafName 'module'
   Invoke-Maven @('-B','-ntp','clean','verify','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-Dmdep.includeScope=test','-Dmdep.outputFile=target/runtime-classpath.txt')
   $runtime = (Get-Content -LiteralPath (Join-Path $target 'runtime-classpath.txt') -Raw).Trim().Split(';')
   $manifest = foreach($path in $runtime) { @{path=$path;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()} }
   $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $target 'runtime-sha256.json') -Encoding utf8
   $cores = @($runtime | Where-Object { [IO.Path]::GetFileName($_) -match '^qqq-backend-core-' })
   if($cores.Count -ne 1 -or [IO.Path]::GetFileName($cores[0]) -ne 'qqq-backend-core-4.1.0-isolated-record-files-SNAPSHOT.jar') { throw 'Unexpected or duplicate core runtime.' }
   foreach($entry in $expected | Where-Object { $_.path.EndsWith('.jar') })
   {
      $resolved = @($manifest | Where-Object { [IO.Path]::GetFileName($_.path) -eq $entry.path })
      if($resolved.Count -ne 1 -or $resolved[0].sha256 -ne $entry.sha256) { throw "Runtime differs from retained binary: $($entry.path)" }
   }
   Add-Type -AssemblyName System.IO.Compression.FileSystem
   $classes = @{}
   foreach($path in $runtime)
   {
      $archive = [IO.Compression.ZipFile]::OpenRead($path)
      try
      {
         foreach($entry in $archive.Entries | Where-Object { $_.FullName.StartsWith('com/kingsrook/') -and $_.FullName.EndsWith('.class') })
         {
            if($classes.ContainsKey($entry.FullName)) { throw "Duplicate QQQ/QBit runtime class: $($entry.FullName)" }
            $classes[$entry.FullName] = $path
         }
      }
      finally { $archive.Dispose() }
   }
   $compiled = foreach($file in Get-ChildItem -LiteralPath (Join-Path $target 'module/classes'),(Join-Path $target 'module/test-classes') -File -Recurse)
   {
      @{path=$file.FullName;sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
   }
   $compiled | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $target 'compiled-sha256.json') -Encoding utf8
   $scannerArtifact = $manifest | Where-Object { [IO.Path]::GetFileName($_.path) -eq 'qbit-mobile-scanners-0.1.0-SNAPSHOT.jar' }
   @{duplicateQqqClasses=0;classCount=$classes.Count;h2Version='2.2.224';baseBuildMode=$(if($SkipBaseBuild) { 'module-only-prior-scanner-binary' } else { 'fresh-source-clean-base-build' });scannerJarSha256=$scannerArtifact.sha256;derivedCollaborationPomSha256=(Get-FileHash -LiteralPath $collaborationPom -Algorithm SHA256).Hash.ToLowerInvariant()} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $target 'runtime-validation.json') -Encoding utf8
   @{verifiedAtUtc=[DateTime]::UtcNow.ToString('O');runtimeManifestSha256=(Get-FileHash -LiteralPath (Join-Path $target 'runtime-sha256.json')).Hash;compiledManifestSha256=(Get-FileHash -LiteralPath (Join-Path $target 'compiled-sha256.json')).Hash} | ConvertTo-Json | Set-Content -LiteralPath $successMarker -Encoding utf8
}
finally { Pop-Location }
