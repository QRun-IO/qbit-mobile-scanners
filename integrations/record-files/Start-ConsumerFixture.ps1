[CmdletBinding()]
param(
   [ValidateRange(1,3600)][int]$LifetimeSeconds = 1800,
   [ValidateRange(1,60)][int]$StartupTimeoutSeconds = 30,
   [string]$PrivateDirectory = (Join-Path $env:USERPROFILE '.cache/qqq-mobile-record-files-fixture')
)
$ErrorActionPreference = 'Stop'
$module = $PSScriptRoot
$runtimeFile = Join-Path $module 'target/runtime-classpath.txt'
if(!(Test-Path -LiteralPath $runtimeFile)) { throw 'Run Verify-Isolated.ps1 successfully first.' }
$markerFile = Join-Path $module 'target/verification-success.json'
if(!(Test-Path -LiteralPath $markerFile)) { throw 'The last isolated verification did not complete successfully.' }
$marker = Get-Content -LiteralPath $markerFile -Raw | ConvertFrom-Json
foreach($entry in @(@('runtime-sha256.json','runtimeManifestSha256'), @('compiled-sha256.json','compiledManifestSha256')))
{
   $manifestPath = Join-Path $module ('target/' + $entry[0])
   if((Get-FileHash -LiteralPath $manifestPath).Hash -ne $marker.($entry[1])) { throw 'Verification manifest changed after the successful run.' }
   foreach($artifact in (Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json))
   {
      if((Get-FileHash -LiteralPath $artifact.path).Hash -ne $artifact.sha256) { throw 'A runtime artifact changed after verification.' }
   }
}
New-Item -ItemType Directory -Force -Path $PrivateDirectory | Out-Null
$privateRoot = (Resolve-Path -LiteralPath $PrivateDirectory).Path
if((Get-Item -LiteralPath $privateRoot -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Private fixture directory must not be a reparse point.' }
$run = [Guid]::NewGuid().ToString('N')
$runDirectory = Join-Path $privateRoot $run
New-Item -ItemType Directory -Path $runDirectory | Out-Null
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = New-Object Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true, $false)
$acl.SetOwner($identity)
$rule = New-Object Security.AccessControl.FileSystemAccessRule($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
$acl.AddAccessRule($rule)
Set-Acl -LiteralPath $runDirectory -AclObject $acl
$credentials = Join-Path $runDirectory 'credentials.json'
$arguments = Join-Path $runDirectory 'java.args'
$classpath = (Join-Path $module 'target/module/test-classes') + ';' + (Join-Path $module 'target/module/classes') + ';' + (Get-Content -LiteralPath $runtimeFile -Raw).Trim()
if($classpath.IndexOfAny([char[]]"`r`n`"") -ge 0 -or $credentials.IndexOfAny([char[]]"`r`n`"") -ge 0) { throw 'Invalid Java argument path.' }
if(!(Test-Path -LiteralPath (Join-Path $module 'target/module/test-classes/com/kingsrook/qbits/mobilescanners/MobileRecordFilesIntegrationTest.class'))) { throw 'Compile the current isolated fixture before starting it.' }
$lines = @('-XX:ActiveProcessorCount=2', '-Xmx512m', '-Dqqq.logger.logSessionId.disabled=true', '-cp', ('"' + $classpath.Replace('\','/') + '"'),
   'com.kingsrook.qbits.mobilescanners.MobileRecordFilesIntegrationTest', ('"' + $credentials.Replace('\','/') + '"'), "$LifetimeSeconds")
$lines | Set-Content -LiteralPath $arguments -Encoding utf8NoBOM
$java = if($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java.exe).Source }
$process = Start-Process -FilePath $java -ArgumentList ('@"' + $arguments + '"') -WorkingDirectory $module -WindowStyle Hidden -PassThru `
   -RedirectStandardOutput (Join-Path $runDirectory 'stdout.log') -RedirectStandardError (Join-Path $runDirectory 'stderr.log')
$deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
while(!(Test-Path -LiteralPath $credentials))
{
   if($process.HasExited) { throw "Fixture startup exited with code $($process.ExitCode); inspect the private log directory." }
   if([DateTime]::UtcNow -ge $deadline)
   {
      if(!$process.HasExited) { $process.Kill() }
      throw 'Fixture startup timed out; its process was stopped. Inspect the private log directory.'
   }
   Start-Sleep -Milliseconds 250
}
[pscustomobject]@{ ProcessId=$process.Id; CredentialsPath=$credentials; ExpiresAfterSeconds=$LifetimeSeconds; LogDirectory=$runDirectory }
