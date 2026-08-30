# 概念 AI 起草助手（D1 会话闭环）冒烟脚本
# 1) 登录 -> 2) 创建概念 -> 3) chat -> 4) session 重放 -> 5) 权限(401/403/1031) -> 6) 落表验证
# 运行：powershell -File scripts\smoke_d1_chat.ps1
$ErrorActionPreference = "Stop"
$BASE = "http://127.0.0.1:8093"
$CLUB_ID = "2092285569724362753"        # 篮球社_6958
$PRESIDENT = "stu_7f36dc"               # 社长
$OTHER_VP = "stu_4512db"                # 副社长（非发起人，用于 403 验证）
$PASS = "Test123456"

$passCount = 0; $failCount = 0
function Check($name, $cond) {
    if ($cond) { $script:passCount++; Write-Host "PASS $name" }
    else { $script:failCount++; Write-Host "FAIL $name" }
}

function Login($username) {
    $cap = Invoke-RestMethod -Uri "$BASE/auth/captcha" -TimeoutSec 10
    $code = (& F:\Redis\redis-cli.exe GET "club:captcha:$($cap.data.captchaKey)")
    $body = @{ username = $username; password = $PASS; captchaKey = $cap.data.captchaKey; captchaCode = $code } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$BASE/auth/login" -Method Post -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
    return $r.data.token
}

Write-Host "=== D1 会话闭环冒烟 ==="
$token = Login $PRESIDENT
Check "社长登录" ($null -ne $token -and $token.Length -gt 20)
$headers = @{ Authorization = "Bearer $token" }

# 1) 未登录 401（鉴权失败 = HTTP 200 + 业务码 401）
try {
    $r = Invoke-RestMethod -Uri "$BASE/clubs/$CLUB_ID/concepts/1/ai/chat" -Method Post -ContentType "application/json" -Body '{"message":"hi"}' -TimeoutSec 10
    Check "未登录 401" ($r.code -eq 401)
} catch { Check "未登录 401" $false }

# 2) 发起概念（若有活跃概念则复用）
$conceptId = $null
try {
    $list = Invoke-RestMethod -Uri "$BASE/clubs/$CLUB_ID/concepts?page=1&size=5&status=1" -Headers $headers -TimeoutSec 10
    if ($list.data.records.Count -gt 0) { $conceptId = $list.data.records[0].id; Write-Host "复用起草中概念 $conceptId" }
} catch {}
if (-not $conceptId) {
    $body = @{ reason = "下月5号文昌发射，组织骑行观看（75km）"; plannedTime = "2026-09-05 06:00"; plannedLocation = "学校东门"; content = "骑行看火箭发射" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$BASE/clubs/$CLUB_ID/concepts" -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 15
    $conceptId = $r.data.id
    Write-Host "新发起概念 $conceptId"
}
Check "概念存在(起草中)" ($null -ne $conceptId)

# 3) chat（真实 LLM，耗时约 5-30s）
$body = @{ message = "帮我分析下这次骑行活动要注意什么" } | ConvertTo-Json
$r = Invoke-RestMethod -Uri "$BASE/clubs/$CLUB_ID/concepts/$conceptId/ai/chat" -Method Post -Headers $headers -ContentType "application/json; charset=utf-8" -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) -TimeoutSec 120
$msgs = @($r.data)
Check "chat 返回成对消息" ($msgs.Count -ge 2 -and $msgs[0].role -eq "user" -and $msgs[-1].role -eq "assistant")
Check "assistant 回复非空" ($msgs[-1].content.Length -gt 50)

# 4) session 重放
$r = Invoke-RestMethod -Uri "$BASE/clubs/$CLUB_ID/concepts/$conceptId/ai/session" -Headers $headers -TimeoutSec 10
Check "session 重放一致" (@($r.data).Count -eq $msgs.Count)

# 5) 非发起人 403
$vpToken = Login $OTHER_VP
$vpHeaders = @{ Authorization = "Bearer $vpToken" }
try {
    $r = Invoke-RestMethod -Uri "$BASE/clubs/$CLUB_ID/concepts/$conceptId/ai/session" -Headers $vpHeaders -TimeoutSec 10
    Check "非发起人 403" ($r.code -eq 403)
} catch { Check "非发起人 403" $false }

# 6) 落表验证（user/assistant 成对）
$env:PGPASSWORD = "root"
$row = (& D:\PostgreSQL\bin\psql.exe -U postgres -h localhost -d club_agent -t -A -c "SELECT count(*) FROM concept_draft_session WHERE concept_id=$conceptId AND role IN ('user','assistant');")
Check "会话落表(>=2条)" ([int]$row -ge 2)

Write-Host "=== 结果：PASS=$passCount FAIL=$failCount ==="
if ($failCount -gt 0) { exit 1 }
