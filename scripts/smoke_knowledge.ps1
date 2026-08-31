# 双源知识检索冒烟（双项目集成任务6）：
# 上传资料 → /ai/knowledge 返回 sqlItems + fileItems + 水位 → rag 降级不阻断 → 未登录 401 → 清理
# 运行：powershell -File scripts\smoke_knowledge.ps1
$ErrorActionPreference = "Stop"
$BASE = "http://127.0.0.1:8093"
$CLUB_ID = "2092285569724362753"
$PRESIDENT = "stu_7f36dc"
$PASS = "Test123456"

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
    $resp = $req.GetResponse()
    $reader = [System.IO.StreamReader]::new($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
    $json = $reader.ReadToEnd(); $reader.Close(); $resp.Close()
    return ($json | ConvertFrom-Json)
}

function Login($username) {
    $cap = Invoke-Api "GET" "$BASE/auth/captcha" $null $null
    $code = (& F:\Redis\redis-cli.exe GET "club:captcha:$($cap.data.captchaKey)")
    $body = @{ username = $username; password = $PASS; captchaKey = $cap.data.captchaKey; captchaCode = $code } | ConvertTo-Json
    $r = Invoke-Api "POST" "$BASE/auth/login" $null $body
    return $r.data.token
}

function Upload-Multipart($uri, $token, $filePath) {
    $boundary = [System.Guid]::NewGuid().ToString("N")
    $req = [System.Net.HttpWebRequest]::Create($uri)
    $req.Method = "POST"
    $req.Timeout = 120000
    $req.Headers.Add("Authorization", "Bearer $token")
    $req.ContentType = "multipart/form-data; boundary=$boundary"
    $ms = [System.IO.MemoryStream]::new()
    $enc = [System.Text.Encoding]::UTF8
    $fname = [System.IO.Path]::GetFileName($filePath)
    $head = "--$boundary`r`nContent-Disposition: form-data; name=`"file`"; filename=`"$fname`"`r`nContent-Type: application/octet-stream`r`n`r`n"
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

Write-Host "=== 双源知识检索冒烟 ==="
$token = Login $PRESIDENT
$headers = @{ Authorization = "Bearer $token" }
Check "社长登录" ($null -ne $token -and $token.Length -gt 20)

# 准备并上传资料（rag org 空间种子数据）
$testFile = "$env:TEMP\_smoke_knowledge_seed.md"
@"
# 露营活动筹备手册

## 装备清单
帐篷按两人一顶准备，防潮垫与睡袋按夜间最低温度选型；炊具统一由后勤组携带。

## 营地安全
营地选址远离河滩与枯树；夜间值守两人一班；明火区与帐篷区保持10米距离。
"@ | Out-File -FilePath $testFile -Encoding utf8
$up = Upload-Multipart "$BASE/clubs/$CLUB_ID/file-lib/upload" $token $testFile
Check "种子资料上传" ($up.code -eq 200)
$libId = $up.data.id

# 等解析完成（轮询列表懒同步）
$ok = $false
for ($i = 0; $i -lt 20; $i++) {
    Start-Sleep -Seconds 3
    $d = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/file-lib" $headers $null
    $r = $d.data | Where-Object { $_.id -eq $libId } | Select-Object -First 1
    if ($r.ragStatus -eq "success" -or $r.ragStatus -eq "partial") { $ok = $true; break }
    if ($r.ragStatus -eq "failed") { break }
}
Check "种子资料解析完成" $ok

# 双源检索：文件命中 + 结构字段齐全
$k = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/ai/knowledge?q=%E9%9C%B2%E8%90%A5%E5%9C%B0%E9%80%89%E5%9D%80%E8%A6%81%E6%B3%A8%E6%84%8F%E4%BB%80%E4%B9%88&topK=8" $headers $null
Check "返回结构含三字段" ($null -ne $k.data.sqlItems -and $null -ne $k.data.fileItems -and $null -ne $k.data.similarActivityCount)
$fileHit = $k.data.fileItems | Where-Object { $_.content -like "*河滩*" -or $_.content -like "*营地*" } | Select-Object -First 1
Check "文件源命中（含来源溯源）" ($null -ne $fileHit -and $fileHit.filename -like "*_smoke_knowledge_seed.md")

# 未登录 → 业务码 401（HTTP 200 + body.code=401，club 惯例）
$anon = [System.Net.HttpWebRequest]::Create("$BASE/clubs/$CLUB_ID/ai/knowledge?q=test")
$anon.Method = "GET"; $anon.Timeout = 30000
$respA = $anon.GetResponse()
$readerA = [System.IO.StreamReader]::new($respA.GetResponseStream(), [System.Text.Encoding]::UTF8)
$anonJson = $readerA.ReadToEnd() | ConvertFrom-Json
$readerA.Close(); $respA.Close()
Check "未登录返回 401" ($anonJson.code -eq 401)

# 清理：删除种子资料（rag 侧同步失效）
$del = Invoke-Api "DELETE" "$BASE/clubs/$CLUB_ID/file-lib/$libId" $headers $null
Check "清理种子资料" ($del.code -eq 200)
Remove-Item $testFile -Force -ErrorAction SilentlyContinue

Write-Host "=== 结果：PASS=$passCount FAIL=$failCount ==="
if ($failCount -gt 0) { exit 1 }
