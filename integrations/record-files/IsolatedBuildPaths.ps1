#Requires -Version 7.4
# Copyright (c) 2026 Kingsrook, LLC
# SPDX-License-Identifier: Apache-2.0

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

<#
.SYNOPSIS
   Remove one verified module scratch tree before a fresh isolated compilation.
.DESCRIPTION
   Restricts deletion to the approved target leaves and refuses reparse points.
   Deterministic cleanup prevents stale source or classes entering verification.
#>
function Remove-IsolatedBuildDirectory
{
   [CmdletBinding()]
   [Diagnostics.CodeAnalysis.SuppressMessageAttribute('PSUseShouldProcessForStateChangingFunctions', '', Justification = 'Internal verification cleanup must either remove the validated scratch tree or fail; an interactive skip would permit stale sources and classes in the next verification.')]
   param(
      [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$ModuleRoot,
      [Parameter(Mandatory)][ValidateSet('scanner-source', 'module')][string]$LeafName
   )
   $root = (Resolve-Path -LiteralPath $ModuleRoot -ErrorAction Stop).Path
   $target = [IO.Path]::GetFullPath((Join-Path $root 'target'))
   $expected = [IO.Path]::GetFullPath((Join-Path $target $LeafName))
   if([IO.Path]::GetDirectoryName($expected) -ne $target -or !$expected.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase))
   {
      throw 'Refusing a scratch path outside the module target.'
   }
   foreach($parent in @($root, $target))
   {
      if((Test-Path -LiteralPath $parent) -and ((Get-Item -LiteralPath $parent -Force).Attributes -band [IO.FileAttributes]::ReparsePoint))
      {
         throw 'Refusing a reparse point in the scratch path.'
      }
   }
   if(Test-Path -LiteralPath $expected)
   {
      $actual = (Resolve-Path -LiteralPath $expected -ErrorAction Stop).Path
      if(![StringComparer]::OrdinalIgnoreCase.Equals($actual, $expected)) { throw 'Unexpected resolved scratch path.' }
      $items = @((Get-Item -LiteralPath $actual -Force)) + @(Get-ChildItem -LiteralPath $actual -Recurse -Force)
      if($items | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }) { throw 'Refusing a scratch tree containing a reparse point.' }
      Remove-Item -LiteralPath $actual -Recurse -Force -ErrorAction Stop
   }
}

<#
.SYNOPSIS
   Snapshot the current scanner source into a freshly cleaned module scratch tree.
.DESCRIPTION
   Copies only the wrapper, Maven configuration, source and style inputs needed
   for isolated verification; sibling artifact caches remain untouched.
#>
function Initialize-IsolatedScannerSource
{
   [CmdletBinding()]
   param([Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$ScannerRoot, [Parameter(Mandatory)][ValidateNotNullOrEmpty()][string]$ModuleRoot)
   Remove-IsolatedBuildDirectory -ModuleRoot $ModuleRoot -LeafName 'scanner-source'
   $destination = Join-Path $ModuleRoot 'target/scanner-source'
   New-Item -ItemType Directory -Path $destination -Force | Out-Null
   foreach($name in @('pom.xml', 'mvnw.cmd', 'mvnw', '.mvn', 'src', 'checkstyle'))
   {
      Copy-Item -LiteralPath (Join-Path $ScannerRoot $name) -Destination $destination -Recurse -Force
   }
}
