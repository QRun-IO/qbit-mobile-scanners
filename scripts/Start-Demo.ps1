#Requires -Version 7.4
# Copyright (c) 2026 Kingsrook, LLC
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
   Start the loopback demo with per-user Windows-protected development passwords.
   Existing accounts and their passwords are preserved between starts.
#>
[CmdletBinding()]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSAvoidUsingConvertToSecureStringWithPlainText', '', Justification = 'Only a freshly generated random development password is converted in memory for DPAPI persistence.')]
param(
   [switch]$SkipBuild,
   [ValidateRange(1024, 65535)]
   [int]$Port = 8080
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if(-not $IsWindows)
{
   throw 'This launcher requires PowerShell 7 on Windows. See TC22-ACCEPTANCE.md for other platforms.'
}

$repositoryPath = Split-Path -Parent $PSScriptRoot
$credentialDirectory = Join-Path $env:LOCALAPPDATA 'QRunIO/MobileScannersDemo'
$credentialPath = Join-Path $credentialDirectory 'credentials.clixml'
if(-not (Test-Path -LiteralPath $credentialPath))
{
   if(Test-Path -LiteralPath (Join-Path $repositoryPath 'demo/data/mobile.mv.db'))
   {
      throw 'An existing demo database has no matching local credential file. Start with its original password environment variables; this launcher will not reset accounts.'
   }
   New-Item -ItemType Directory -Path $credentialDirectory -Force | Out-Null
   $credentials = @{}
   foreach($account in @('scanner', 'admin'))
   {
      $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
      $credentials[$account] = ConvertTo-SecureString -String $password -AsPlainText -Force
   }
   $credentials | Export-Clixml -LiteralPath $credentialPath
   $password = $null
}
$credentials = Import-Clixml -LiteralPath $credentialPath
$previousScannerPassword = $env:MOBILE_DEMO_SCANNER_PASSWORD
$previousAdminPassword = $env:MOBILE_DEMO_ADMIN_PASSWORD
Push-Location -LiteralPath $repositoryPath
try
{
   if(-not $SkipBuild)
   {
      & .\mvnw.cmd -B verify install
      if($LASTEXITCODE -ne 0) { throw 'QBit verification failed.' }
      & .\mvnw.cmd -B -f demo/pom.xml verify
      if($LASTEXITCODE -ne 0) { throw 'Demo verification failed.' }
   }
   $env:MOBILE_DEMO_SCANNER_PASSWORD = [Net.NetworkCredential]::new('', $credentials.scanner).Password
   $env:MOBILE_DEMO_ADMIN_PASSWORD = [Net.NetworkCredential]::new('', $credentials.admin).Password
   Set-Location -LiteralPath (Join-Path $repositoryPath 'demo')
   Write-Information "Starting QQQ Mobile demo at http://127.0.0.1:$Port/. Use Copy-DemoPassword.ps1 to sign in." -InformationAction Continue
   & ..\mvnw.cmd -B exec:java "-Dexec.args=$Port"
   if($LASTEXITCODE -ne 0) { throw 'Demo server exited unsuccessfully.' }
}
finally
{
   $env:MOBILE_DEMO_SCANNER_PASSWORD = $previousScannerPassword
   $env:MOBILE_DEMO_ADMIN_PASSWORD = $previousAdminPassword
   Pop-Location
}
