# 活动资料库冒烟（双项目集成任务5）：
# 上传 → 落库 + rag 入库 → rag 检索命中 → 列表可见 → 软删 → 列表消失 + rag 不可见 → 未登录 401
# 运行：powershell -File scripts\smoke_file_lib.ps1
$ErrorActionPreference = "Stop"
$BASE = "http://127.0.0.1:8093"
$RAG = "http://127.0.0.1:8090"
$CLUB_ID = "2092285569724362753"        # 篮球社_6958（org 空间 = club_id）
$PRESIDENT = "stu_7f36dc"
$PASS = "Test123456"
$RAG_KEY = (Select-String -Path "$PSScriptRoot\..\.env" -Pattern '^RAG_INTERNAL_KEY=(.*)').Matches[0].Groups[1].Value

$passCount = 0; $failCount = 0
function Check($name, $cond) {
    if ($cond) { $script:passCount++; Write-Host "PASS $name" }
    else { $script:failCount++; Write-Host "FAIL $name" }
}

function Invoke-Api($method, $uri, $headers, $body) {
    $req = [System.Net.HttpWebRequest]::Create($uri)
    $req.Method = $method
    $req.Timeout = 120000
    if ($headers) { foreach ($k in $headers.Keys) { $req.Headers.Add($k, $headers[$k]) } }
    if ($body) {
        $req.ContentType = "application/json; charset=utf-8"
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $req.ContentLength = $bytes.Length
        $stream = $req.GetRequestStream(); $stream.Write($bytes, 0, $bytes.Length); $stream.Close()
    }
    try {
        $resp = $req.GetResponse()
        $reader = [System.IO.StreamReader]::new($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
        $json = $reader.ReadToEnd(); $reader.Close(); $resp.Close()
        return ($json | ConvertFrom-Json)
    } catch [System.Net.WebException] {
        return @{ __status = [int]$_.Exception.Response.StatusCode }
    }
}

function Login($username) {
    $cap = Invoke-Api "GET" "$BASE/auth/captcha" $null $null
    $code = (& F:\Redis\redis-cli.exe GET "club:captcha:$($cap.data.captchaKey)")
    $body = @{ username = $username; password = $PASS; captchaKey = $cap.data.captchaKey; captchaCode = $code } | ConvertTo-Json
    $r = Invoke-Api "POST" "$BASE/auth/login" $null $body
    return $r.data.token
}

function Upload-Multipart($uri, $token, $filePath, $formName, $extraFields) {
    $boundary = [System.Guid]::NewGuid().ToString("N")
    $req = [System.Net.HttpWebRequest]::Create($uri)
    $req.Method = "POST"
    $req.Timeout = 120000
    $req.Headers.Add("Authorization", "Bearer $token")
    $req.ContentType = "multipart/form-data; boundary=$boundary"
    $ms = [System.IO.MemoryStream]::new()
    $enc = [System.Text.Encoding]::UTF8
    foreach ($k in $extraFields.Keys) {
        $part = "--$boundary`r`nContent-Disposition: form-data; name=`"$k`"`r`n`r`n$($extraFields[$k])`r`n"
        $b = $enc.GetBytes($part); $ms.Write($b, 0, $b.Length)
    }
    $fname = [System.IO.Path]::GetFileName($filePath)
    $head = "--$boundary`r`nContent-Disposition: form-data; name=`"$formName`"; filename=`"$fname`"`r`nContent-Type: application/octet-stream`r`n`r`n"
    $b = $enc.GetBytes($head); $ms.Write($b, 0, $b.Length)
    $fb = [System.IO.File]::ReadAllBytes($filePath); $ms.Write($fb, 0, $fb.Length)
    $tail = $enc.GetBytes("`r`n--$boundary--`r`n"); $ms.Write($tail, 0, $tail.Length)
    $bytes = $ms.ToArray(); $ms.Close()
    $req.ContentLength = $bytes.Length
    $stream = $req.GetRequestStream(); $stream.Write($bytes, 0, $bytes.Length); $stream.Close()
    $resp = $req.GetResponse()
    $reader = [System.IO.StreamReader]::new($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
    $json = $reader.ReadToEnd(); $reader.Close(); $resp.Close()
    return ($json | ConvertFrom-Json)
}

Write-Host "=== 活动资料库冒烟（双项目集成） ==="
$token = Login $PRESIDENT
Check "社长登录" ($null -ne $token -and $token.Length -gt 20)
$headers = @{ Authorization = "Bearer $token" }

# 准备测试资料文件（含可检索关键词）
$testFile = "$env:TEMP\_smoke_hiking_ref.md"
@"
# 徒步社秋季香山红叶活动参考资料

## 预算参考
全程预算1500元：包车800元，门票与保险420元，补给与应急280元。历史同类活动预算偏差在5%以内。

## 安全要点
香山北坡台阶湿滑，雨天必须改走南线；全员对讲机至少3台；队医随行。
"@ | Out-File -FilePath $testFile -Encoding utf8

# 1) 上传（管理层）
$up = Upload-Multipart "$BASE/clubs/$CLUB_ID/file-lib/upload" $token $testFile "file" @{ }
Check "上传成功" ($up.code -eq 200 -and $null -ne $up.data.id)
Check "rag 已受理（ragFileId 回填 + parsing）" ($null -ne $up.data.ragStatus -and $up.data.ragStatus -ne "pending")
$libId = $up.data.id
$detail = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/file-lib" $headers $null
$row = $detail.data | Where-Object { $_.id -eq $libId } | Select-Object -First 1
Write-Host "  libId=$libId ragStatus=$($row.ragStatus)"

# 2) 等 rag 解析完成（轮询 club 列表的 ragStatus）
$ok = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 3
    $d = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/file-lib" $headers $null
    $r = $d.data | Where-Object { $_.id -eq $libId } | Select-Object -First 1
    if ($r.ragStatus -eq "success" -or $r.ragStatus -eq "partial") { $ok = $true; break }
    if ($r.ragStatus -eq "failed") { break }
}
Check "rag 解析完成（success/partial）" $ok

# 3) rag 检索命中（org 空间 = club_id）
$reqBody = @{ query = "香山活动的预算是多少"; org_id = [long]$CLUB_ID; top_k = 8 } | ConvertTo-Json
$ret = Invoke-Api "POST" "$RAG/api/org/retrieve" @{ "X-Internal-Key" = $RAG_KEY } $reqBody
$hit = $ret.items | Where-Object { $_.filename -like "*_smoke_hiking_ref.md" } | Select-Object -First 1
Check "rag 检索命中上传资料" ($null -ne $hit -and $hit.content -like "*1500*")

# 4) 列表可见
Check "列表包含资料" ($null -ne $row)

# 5) 软删 → 列表消失 + rag 不可见
$del = Invoke-Api "DELETE" "$BASE/clubs/$CLUB_ID/file-lib/$libId" $headers $null
Check "删除成功" ($del.code -eq 200)
$d2 = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/file-lib" $headers $null
$gone = ($d2.data | Where-Object { $_.id -eq $libId }) -eq $null
Check "删除后列表不可见" $gone
$ret2 = Invoke-Api "POST" "$RAG/api/org/retrieve" @{ "X-Internal-Key" = $RAG_KEY } $reqBody
$hit2 = $ret2.items | Where-Object { $_.filename -like "*_smoke_hiking_ref.md" }
Check "删除后 rag 检索不可见" ($null -eq $hit2)

# 6) 未登录 → 业务码 401（club 惯例：HTTP 200 + body.code=401）
$anon = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/file-lib" $null $null
Check "未登录返回 401" ($anon.code -eq 401)

Remove-Item $testFile -Force -ErrorAction SilentlyContinue
Write-Host "=== 结果：PASS=$passCount FAIL=$failCount ==="
if ($failCount -gt 0) { exit 1 }
