# 概念 AI 起草助手（D2 工具接入）冒烟脚本
# 1) 登录 -> 2) 对话触发生成草案（工具调用） -> 3) 采纳落表 -> 4) trace 留痕 -> 5) 表单同步
# 运行：powershell -File scripts\smoke_d2_tools.ps1
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

# 统一 API 调用：PS 5.1 Invoke-RestMethod 对无 charset 的 JSON 响应按 ISO-8859-1 解码导致中文乱码，
# 这里用 HttpWebRequest 拿原始字节并显式 UTF-8 解码（浏览器链路本就 UTF-8，仅脚本层需要）
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

Write-Host "=== D2 工具接入冒烟 ==="
$token = Login $PRESIDENT
Check "社长登录" ($null -ne $token -and $token.Length -gt 20)
$headers = @{ Authorization = "Bearer $token" }

# 1) 社团上下文接口（get_club_context 数据源）
$r = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/ai/context" $headers $null
Check "context 返回管理层" ($r.code -eq 200 -and @($r.data.managers).Count -ge 3 -and $null -ne $r.data.clubName)

# 2) 经验检索接口（冷启动返回空 items）
$r = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/ai/experience?q=%E9%AA%91%E8%A1%8C" $headers $null
Check "experience 冷启动空" ($r.code -eq 200 -and @($r.data.items).Count -eq 0)

# 3) 找到起草中概念（复用 D1 的）
$list = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/concepts?page=1&size=5&status=1" $headers $null
$conceptId = $list.data.records[0].id
Check "起草中概念存在" ($null -ne $conceptId)

# 4) AI 对话触发生成草案（模型行为有随机性：表单已填满时可能只读不生成，最多重试 3 轮）
$draftMsg = $null
for ($try = 0; $try -lt 3 -and $null -eq $draftMsg; $try++) {
    $prompt = "根据之前讨论的，你直接帮我生成一版企划草案吧"
    if ($try -gt 0) { $prompt = "请重新生成一版完整的企划草案，覆盖表单全部字段（reason/时间/地点/content），不要只描述" }
    $body = @{ message = $prompt } | ConvertTo-Json
    $r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/concepts/$conceptId/ai/chat" $headers $body
    $msgs = @($r.data)
    $draftMsg = $msgs | Where-Object { $_.role -eq "tool" -and $_.toolName -eq "generate_draft" } | Select-Object -Last 1
}
Check "工具消息落库(generate_draft)" ($null -ne $draftMsg)
# 草案 JSON 在 content（工具输出）；toolArgs 是入参（审计用）
Check "草案 JSON 可解析" ($null -ne $draftMsg -and $null -ne ($draftMsg.content | ConvertFrom-Json))
$draft = $draftMsg.content | ConvertFrom-Json
Check "草案字段完整" ($draft.reason -and $draft.planned_time -and $draft.planned_location -and $draft.content -and $draft.decision_note)
Check "assistant 转述存在" ($msgs[-1].role -eq "assistant" -and $msgs[-1].content.Length -gt 20)

# 5) 采纳（人确认前置 → PUT ai-draft → 落表 + trace）
$body = @{ reason = $draft.reason; plannedTime = $draft.planned_time; plannedLocation = $draft.planned_location; content = $draft.content; note = $draft.decision_note } | ConvertTo-Json
$r = Invoke-Api "PUT" "$BASE/clubs/$CLUB_ID/concepts/$conceptId/ai-draft" $headers $body
Check "采纳成功返回详情" ($r.code -eq 200 -and $null -ne $r.data.plannedTime)
Check "表单已同步" ($r.data.reason -eq $draft.reason -and $r.data.plannedTime -eq $draft.planned_time)

# 6) trace 留痕（ai_draft 动作）
$detail = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/concepts/$conceptId" $headers $null
$aiTrace = @($detail.data.traces) | Where-Object { $_.action -eq "ai_draft" } | Select-Object -First 1
Check "trace 留痕 ai_draft" ($null -ne $aiTrace -and $null -ne $aiTrace.operatorName)

# 7) 权限：非发起人采纳 403（业务码）
$vpToken = Login "stu_4512db"
try {
    $r = Invoke-Api "PUT" "$BASE/clubs/$CLUB_ID/concepts/$conceptId/ai-draft" @{ Authorization = "Bearer $vpToken" } '{"note":"x"}'
    Check "非发起人采纳 403" ($r.code -eq 403)
} catch { Check "非发起人采纳 403" $false }

Write-Host "=== 结果：PASS=$passCount FAIL=$failCount ==="
if ($failCount -gt 0) { exit 1 }
