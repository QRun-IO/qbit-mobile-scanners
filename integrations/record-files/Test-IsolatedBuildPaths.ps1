#Requires -Version 7.4
# Copyright (c) 2026 Kingsrook, LLC
# SPDX-License-Identifier: Apache-2.0

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'IsolatedBuildPaths.ps1')
$test = Join-Path $PSScriptRoot ('target/path-regression-' + [Guid]::NewGuid().ToString('N'))
$inputRoot = Join-Path $test 'input'
$moduleRoot = Join-Path $test 'module-root'
New-Item -ItemType Directory -Force -Path $inputRoot, $moduleRoot, (Join-Path $inputRoot '.mvn'), (Join-Path $inputRoot 'src'), (Join-Path $inputRoot 'checkstyle'), (Join-Path $moduleRoot 'target/isolated-m2') | Out-Null
foreach($name in @('pom.xml', 'mvnw.cmd', 'mvnw')) { Set-Content -LiteralPath (Join-Path $inputRoot $name) -Value 'fixture' }
$removedSource = Join-Path $inputRoot 'src/Removed.java'
Set-Content -LiteralPath $removedSource -Value 'old-source'
New-Item -ItemType Directory -Force -Path (Join-Path $inputRoot 'src/main/resources') | Out-Null
$removedResource = Join-Path $inputRoot 'src/main/resources/removed.txt'
Set-Content -LiteralPath $removedResource -Value 'old-resource'
$sentinel = Join-Path $moduleRoot 'target/isolated-m2/keep.txt'
Set-Content -LiteralPath $sentinel -Value 'repository-must-survive'
Initialize-IsolatedScannerSource -ScannerRoot $inputRoot -ModuleRoot $moduleRoot
$scratch = Join-Path $moduleRoot 'target/scanner-source'
New-Item -ItemType Directory -Force -Path (Join-Path $scratch 'target/classes') | Out-Null
Set-Content -LiteralPath (Join-Path $scratch 'target/classes/Removed.class') -Value 'stale-class'
Remove-Item -LiteralPath $removedSource
Remove-Item -LiteralPath $removedResource
Set-Content -LiteralPath (Join-Path $inputRoot 'src/Current.java') -Value 'current-source'
Initialize-IsolatedScannerSource -ScannerRoot $inputRoot -ModuleRoot $moduleRoot
if((Test-Path -LiteralPath (Join-Path $scratch 'src/Removed.java')) -or (Test-Path -LiteralPath (Join-Path $scratch 'target/classes/Removed.class'))) { throw 'Removed source/class survived the fresh snapshot.' }
if(Test-Path -LiteralPath (Join-Path $scratch 'src/main/resources/removed.txt')) { throw 'Removed resource survived the fresh snapshot.' }
Initialize-IsolatedScannerSource -ScannerRoot $inputRoot -ModuleRoot $moduleRoot
if(!(Test-Path -LiteralPath (Join-Path $scratch 'src/Current.java'))) { throw 'Current source was not copied.' }
if((Get-Content -LiteralPath $sentinel -Raw).Trim() -ne 'repository-must-survive') { throw 'Sibling repository changed.' }
New-Item -ItemType Directory -Force -Path (Join-Path $moduleRoot 'target/module') | Out-Null
Remove-IsolatedBuildDirectory -ModuleRoot $moduleRoot -LeafName 'module'
if(!(Test-Path -LiteralPath (Join-Path $scratch 'mvnw.cmd')) -or !(Test-Path -LiteralPath $sentinel)) { throw 'Module cleanup affected its sibling source or repository.' }
$denied = $false
try { Remove-IsolatedBuildDirectory -ModuleRoot $moduleRoot -LeafName '../outside' } catch { $denied = $true }
if(!$denied) { throw 'Non-approved scratch leaf was accepted.' }
$outside = Join-Path $test 'outside'
New-Item -ItemType Directory -Path $outside | Out-Null
New-Item -ItemType Junction -Path (Join-Path $moduleRoot 'target/module') -Target $outside | Out-Null
$denied = $false
try { Remove-IsolatedBuildDirectory -ModuleRoot $moduleRoot -LeafName 'module' } catch { $denied = $true }
if(!$denied -or !(Test-Path -LiteralPath $outside)) { throw 'Reparse tree was not refused.' }
'PASS: deleted Java/resource/class absent; repeat sync current; sibling cache/wrapper preserved; invalid leaf and reparse refused.'
