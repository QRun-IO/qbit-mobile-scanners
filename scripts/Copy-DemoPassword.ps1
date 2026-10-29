#Requires -Version 7.4
# Copyright (c) 2026 Kingsrook, LLC
# SPDX-License-Identifier: Apache-2.0
<#
.SYNOPSIS
   Copy one local demo password on explicit user request without logging it.
#>
[CmdletBinding()]
param(
   [Parameter(Mandatory)]
   [ValidateSet('scanner', 'admin')]
   [string]$Account
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if(-not $IsWindows)
{
   throw 'This helper requires PowerShell 7 on Windows.'
}
$credentialPath = Join-Path $env:LOCALAPPDATA 'QRunIO/MobileScannersDemo/credentials.clixml'
if(-not (Test-Path -LiteralPath $credentialPath))
{
   throw 'Run Start-Demo.ps1 first to create the local demo accounts.'
}
$credentials = Import-Clixml -LiteralPath $credentialPath
$password = [Net.NetworkCredential]::new('', $credentials[$Account]).Password
Set-Clipboard -Value $password
$password = $null
Write-Information "The $Account demo password is on the clipboard. Paste it into the login form, then clear the clipboard." -InformationAction Continue
