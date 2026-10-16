#requires -Version 5.1
# Offline regression for the pre-Open failure. Never invoke the harness or Open-Connection.
[CmdletBinding()]
param([string]$HarnessPath)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# PS 5.1 can leave PSScriptRoot empty while evaluating parameter defaults.
# Resolve the optional default in the script body, independent of the caller's working directory.
if (-not $PSBoundParameters.ContainsKey('HarnessPath')) {
    $HarnessPath = Join-Path $PSScriptRoot '../../docs/sql/auth/invocation-validation/harness.ps1'
}
if ($PSVersionTable.PSEdition -cne 'Desktop' -or $PSVersionTable.PSVersion.Major -ne 5 -or
    $PSVersionTable.PSVersion.Minor -ne 1) {
    throw 'Native Windows PowerShell 5.1 Desktop required; other runtimes do not certify this regression'
}
Add-Type -AssemblyName System.Data
function Assert-Equal($Expected, $Actual, [string]$Contract) {
    if ($Expected -cne $Actual) { throw ('Connection builder regression: ' + $Contract) }
}
$tokens = $null; $errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $HarnessPath).Path, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw 'Harness must parse in Windows PowerShell 5.1' }
$functions = @($ast.FindAll({param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Open-Connection'
}, $true))
Assert-Equal 1 $functions.Count 'exactly one Open-Connection function'
$statements = @($functions[0].Body.EndBlock.Statements)
$construction = @($statements | Where-Object {
    $_ -is [Management.Automation.Language.AssignmentStatementAst] -and $_.Left.Extent.Text -ceq '$con'
})
Assert-Equal 1 $construction.Count 'one connection construction boundary'
# Evaluate actual production statements only through the SqlConnection constructor. AST boundaries
# tolerate comments/formatting/CRLF. No authentication branch, Query, try block or Open() is executed.
$prefix = @($statements | Where-Object { $_.Extent.EndOffset -le $construction[0].Extent.EndOffset })
if (@($prefix | Where-Object { $_ -isnot [Management.Automation.Language.AssignmentStatementAst] }).Count) {
    throw 'Review offline extraction: construction prefix must contain assignments only'
}
$constructionScript = [scriptblock]::Create(($prefix | ForEach-Object { $_.Extent.Text }) -join "`n")
# Reserved .invalid endpoint and deliberately quoted database value prove builder escaping, not
# concatenation. These are test inputs only; no facts, approvals, credentials or state files are used.
foreach ($database in @('auth-invocation-test', 'offline;Encrypt=False;"quoted"')) {
    $script:State = [pscustomobject]@{Facts=[pscustomobject]@{Endpoint='ps51-offline.invalid,1433';Database=$database}}
    $con = $null
    try {
        . $constructionScript
        Assert-Equal ([Data.ConnectionState]::Closed) $con.State 'offline connection remains closed'
        $roundtrip = New-Object Data.SqlClient.SqlConnectionStringBuilder
        $roundtrip.set_ConnectionString($con.ConnectionString)
        $expected = [ordered]@{
            'Data Source'='ps51-offline.invalid,1433'; 'Initial Catalog'=$database
            'Encrypt'=$true; 'TrustServerCertificate'=$false; 'Connect Timeout'=30
            'Pooling'=$false; 'Asynchronous Processing'=$true
            'Application Name'='SyntheticInvocationValidation'
        }
        foreach ($key in $expected.Keys) {
            Assert-Equal $expected[$key] $builder[$key] ('builder ' + $key)
            Assert-Equal $expected[$key] $roundtrip[$key] ('connection roundtrip ' + $key)
        }
        Assert-Equal 8 $builder.Count 'only the eight intended connection settings'
        Assert-Equal $false $roundtrip['Integrated Security'] 'no implicit Windows identity'
        Assert-Equal '' $roundtrip['User ID'] 'no embedded identity'
        Assert-Equal '' $roundtrip['Password'] 'no embedded secret'
        Assert-Equal $null $con.Credential 'no SQL credential used by offline test'
        Assert-Equal $null $con.AccessToken 'no token used by offline test'
        foreach ($method in @('BeginExecuteNonQuery', 'EndExecuteNonQuery')) {
            if (-not ([Data.SqlClient.SqlCommand].GetMethods() | Where-Object Name -CEQ $method)) {
                throw ('Required async API unavailable: ' + $method)
            }
        }
    } finally { if ($con) { $con.Dispose() } }
}
Write-Output ('PASS native Windows PowerShell ' + $PSVersionTable.PSVersion + ' offline connection construction; no Open/SQL/authentication')
