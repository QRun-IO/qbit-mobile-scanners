function Remove-IsolatedBuildDirectory
{
   [CmdletBinding()]
   param(
      [Parameter(Mandatory)][string]$ModuleRoot,
      [Parameter(Mandatory)][ValidateSet('scanner-source','module')][string]$LeafName
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

function Initialize-IsolatedScannerSource
{
   [CmdletBinding()]
   param([Parameter(Mandatory)][string]$ScannerRoot, [Parameter(Mandatory)][string]$ModuleRoot)
   Remove-IsolatedBuildDirectory -ModuleRoot $ModuleRoot -LeafName 'scanner-source'
   $destination = Join-Path $ModuleRoot 'target/scanner-source'
   New-Item -ItemType Directory -Path $destination -Force | Out-Null
   foreach($name in @('pom.xml','mvnw.cmd','mvnw','.mvn','src','checkstyle'))
   {
      Copy-Item -LiteralPath (Join-Path $ScannerRoot $name) -Destination $destination -Recurse -Force
   }
}
