# 概念 AI 起草助手（D3 经验沉淀 + 想法简析）冒烟脚本
# 1) 沉淀经验/思考角度（人确认） -> 2) 检索复用（含注入） -> 3) 非发起人 403 ->
# 4) 提交 -> 5) 异步 ai_brief 生成 -> 6) 详情页可见 + trace 留痕
# 运行：powershell -File scripts\smoke_d3_experience.ps1
$ErrorActionPreference = "Stop"
$BASE = "http://127.0.0.1:8093"
$CLUB_ID = "2092285569724362753"        # 篮球社_6958
$PRESIDENT = "stu_7f36dc"               # 社长（发起人）
$PASS = "Test123456"

$passCount = 0; $failCount = 0
function Check($name, $cond) {
    if ($cond) { $script:passCount++; Write-Host "PASS $name" }
    else { $script:failCount++; Write-Host "FAIL $name" }
}

# 统一 API 调用（PS 5.1 无 charset JSON 响应按 Latin-1 解码导致中文乱码，必须显式 UTF-8）
function Invoke-Api($method, $uri, $headers, $body) {
    $req = [System.Net.HttpWebRequest]::Create($uri)
    $req.Method = $method
    $req.Timeout = 200000
    if ($headers) { foreach ($k in $headers.Keys) { $req.Headers.Add($k, $headers[$k]) } }
    if ($body) {
        $req.ContentType = "application/json; charset=utf-8"
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
        $req.ContentLength = $bytes.Length
        $stream = $req.GetRequestStream(); $stream.Write($bytes, 0, $bytes.Length); $stream.Close()
    }
    $resp = $req.GetResponse()
    $reader = [System.IO.StreamReader]::new($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
    $json = $reader.ReadToEnd()
    $reader.Close(); $resp.Close()
    return ($json | ConvertFrom-Json)
}

function Login($username) {
    $cap = Invoke-Api "GET" "$BASE/auth/captcha" $null $null
    $code = (& F:\Redis\redis-cli.exe GET "club:captcha:$($cap.data.captchaKey)")
    $body = @{ username = $username; password = $PASS; captchaKey = $cap.data.captchaKey; captchaCode = $code } | ConvertTo-Json
    $r = Invoke-Api "POST" "$BASE/auth/login" $null $body
    return $r.data.token
}

Write-Host "=== D3 经验沉淀 + 想法简析冒烟 ==="
$token = Login $PRESIDENT
Check "社长登录" ($null -ne $token -and $token.Length -gt 20)
$headers = @{ Authorization = "Bearer $token" }

# 1) 找起草中概念（发起人信息）
$list = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/concepts?page=1&size=5&status=1" $headers $null
$concept = $list.data.records[0]
Check "起草中概念存在" ($null -ne $concept -and $null -ne $concept.id)
$conceptId = $concept.id
$ownerId = $concept.userId

# 2) 沉淀经验（模拟前端确认卡片：AI 草拟 -> 人确认 -> POST）
$body = @{
    category = "筹备知识"
    title = "长途骑行筹备要点"
    content = "75公里级骑行需提前规划补给点与修车站点，编队骑行配备领队与收尾，出发前全员车辆检查，头盔强制。"
    sourceConceptId = $conceptId
} | ConvertTo-Json
$r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/ai/experience" $headers $body
Check "沉淀经验成功" ($r.code -eq 200)

# 3) 沉淀思考角度（ownerId = 发起人）
$body = @{
    category = "thinking_pattern"
    title = "发起人思考角度"
    content = "先定强度再看时间；关注成员体验；风险优先"
    ownerId = $ownerId
    sourceConceptId = $conceptId
} | ConvertTo-Json
$r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/ai/experience" $headers $body
Check "沉淀思考角度成功" ($r.code -eq 200)

# 4) 检索复用：关键词命中经验
$r = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/ai/experience?q=%E9%AA%91%E8%A1%8C" $headers $null
$hit = @($r.data.items) | Where-Object { $_.title -eq "长途骑行筹备要点" } | Select-Object -First 1
Check "经验检索命中" ($null -ne $hit)

# 5) 注入验证：无关键词也返回该发起人 thinking_pattern
$r = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/ai/experience?q=" $headers $null
$tp = @($r.data.items) | Where-Object { $_.category -eq "thinking_pattern" } | Select-Object -First 1
Check "thinking_pattern 注入" ($null -ne $tp -and $tp.content.Contains("先定强度"))

# 6) 权限：非发起人沉淀 403（副社长不是发起人）
$vpToken = Login "stu_4512db"
try {
    $body = @{ category = "筹备知识"; title = "x"; content = "y"; sourceConceptId = $conceptId } | ConvertTo-Json
    $r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/ai/experience" @{ Authorization = "Bearer $vpToken" } $body
    Check "非发起人沉淀 403" ($r.code -eq 403)
} catch { Check "非发起人沉淀 403" $false }

# 7) 提交概念（表单已就绪；提交不阻塞，ai_brief 异步生成）
$r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/concepts/$conceptId/submit" $headers $null
Check "提交成功" ($r.code -eq 200 -and $r.data.status -eq 2)

# 8) 轮询 ai_brief（MiMo 生成约 40-90s，最多等 150s）
$brief = $null
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 10
    $d = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/concepts/$conceptId" $headers $null
    if ($d.data.aiBrief) { $brief = $d.data.aiBrief; break }
}
Check "ai_brief 已生成" ($null -ne $brief -and $brief.Length -gt 30)
if ($brief) { Write-Host ("  brief 预览: " + $brief.Substring(0, [Math]::Min(60, $brief.Length))) }

# 9) trace 留痕 ai_brief
$d = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/concepts/$conceptId" $headers $null
$aiTrace = @($d.data.traces) | Where-Object { $_.action -eq "ai_brief" } | Select-Object -First 1
Check "trace 留痕 ai_brief" ($null -ne $aiTrace)

Write-Host "=== 结果：PASS=$passCount FAIL=$failCount ==="
if ($failCount -gt 0) { exit 1 }
