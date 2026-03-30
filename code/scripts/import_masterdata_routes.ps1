param(
  [string]$BaseUrl = "http://127.0.0.1:5931",
  [string]$Token = "dev",
  [string]$Operator = "masterdata-route-importer",
  [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function New-RequestId {
  return "route-import-" + [DateTimeOffset]::UtcNow.ToString("yyyyMMddHHmmssfff")
}

function New-Steps {
  param([string[]]$ProcessCodes)
  $steps = @()
  for ($i = 0; $i -lt $ProcessCodes.Count; $i += 1) {
    $steps += @{
      process_code = $ProcessCodes[$i]
      dependency_type = "FS"
      sequence_no = $i + 1
    }
  }
  return $steps
}

function Get-ErrorText {
  param($ErrorRecord)
  if ($null -ne $ErrorRecord.ErrorDetails -and -not [string]::IsNullOrWhiteSpace($ErrorRecord.ErrorDetails.Message)) {
    return [string]$ErrorRecord.ErrorDetails.Message
  }
  if ($null -ne $ErrorRecord.Exception -and -not [string]::IsNullOrWhiteSpace($ErrorRecord.Exception.Message)) {
    return [string]$ErrorRecord.Exception.Message
  }
  return [string]$ErrorRecord
}

function Invoke-InternalApi {
  param(
    [string]$Path,
    [hashtable]$Body,
    [string]$BaseUrl,
    [hashtable]$Headers
  )
  $uri = ($BaseUrl.TrimEnd("/")) + $Path
  $json = $Body | ConvertTo-Json -Depth 10
  return Invoke-RestMethod -Method Post -Uri $uri -Headers $Headers -Body $json
}

$headers = @{
  Authorization = "Bearer $Token"
  "Content-Type" = "application/json"
}

$routes = @(
  @{
    product_code = "YXN.067.005.1006"
    process_codes = @("W150", "W160", "W130", "W140", "W030")
  },
  @{
    product_code = "A006.034.10191"
    process_codes = @("W160", "W030")
  },
  @{
    product_code = "YXN.009.020.1047"
    process_codes = @("W030", "W130", "W140", "W150", "W160")
  },
  @{
    product_code = "YXN.044.02.1028"
    process_codes = @("W130", "W140", "W160", "W150", "W030")
  }
)

$results = @()

foreach ($route in $routes) {
  $productCode = [string]$route.product_code
  $steps = New-Steps -ProcessCodes ([string[]]$route.process_codes)
  $payload = @{
    request_id = New-RequestId
    operator = $Operator
    product_code = $productCode
    steps = $steps
  }

  if ($DryRun) {
    Write-Host "[DRY-RUN] product=$productCode steps=$($route.process_codes -join ' -> ')"
    $results += @{
      product_code = $productCode
      action = "dry_run"
      status = "SKIPPED"
    }
    continue
  }

  try {
    $updateRes = Invoke-InternalApi `
      -Path "/internal/v1/internal/masterdata/routes/update" `
      -Body $payload `
      -BaseUrl $BaseUrl `
      -Headers $headers
    Write-Host "[UPDATED] product=$productCode request_id=$($updateRes.request_id)"
    $results += @{
      product_code = $productCode
      action = "update"
      status = "SUCCESS"
      request_id = $updateRes.request_id
    }
    continue
  } catch {
    $errorText = Get-ErrorText -ErrorRecord $_
    if ($errorText -match "Route not found|NOT_FOUND") {
      $createPayload = @{
        request_id = New-RequestId
        operator = $Operator
        product_code = $productCode
        steps = $steps
      }
      $createRes = Invoke-InternalApi `
        -Path "/internal/v1/internal/masterdata/routes/create" `
        -Body $createPayload `
        -BaseUrl $BaseUrl `
        -Headers $headers
      Write-Host "[CREATED] product=$productCode request_id=$($createRes.request_id)"
      $results += @{
        product_code = $productCode
        action = "create"
        status = "SUCCESS"
        request_id = $createRes.request_id
      }
      continue
    }
    throw
  }
}

Write-Host "`nImport summary:"
$results | Format-Table -AutoSize
