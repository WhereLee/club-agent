# 概念 AI 起草助手（D4 SKILL 生成）冒烟脚本
# 1) SKILL 落盘（人确认） -> 2) 文件校验（frontmatter 格式/UTF-8） -> 3) 非法 name 拒绝 ->
# 4) 非发起人 403 -> 5) trace 留痕
# 运行：powershell -File scripts\smoke_d4_skill.ps1
$ErrorActionPreference = "Stop"
$BASE = "http://127.0.0.1:8093"
$CLUB_ID = "2092285569724362753"
$PRESIDENT = "stu_7f36dc"
$PASS = "Test123456"

$passCount = 0; $failCount = 0
function Check($name, $cond) {
    if ($cond) { $script:passCount++; Write-Host "PASS $name" }
    else { $script:failCount++; Write-Host "FAIL $name" }
}function Invoke-Api($method, $uri, $headers, $body) {
    $req = [System.Net.HttpWebRequest]::Create($uri)
    $req.Method = $method
    $req.Timeout = 60000
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

Write-Host "=== D4 SKILL 生成冒烟 ==="
$token = Login $PRESIDENT
Check "社长登录" ($null -ne $token -and $token.Length -gt 20)
$headers = @{ Authorization = "Bearer $token" }# 1) 取最近概念（skill 也来自对话，source_concept 关联；状态不限）
$list = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/concepts?page=1&size=5" $headers $null
$concept = $list.data.records[0]
Check "概念存在" ($null -ne $concept -and $null -ne $concept.id)
$conceptId = $concept.id

# 2) SKILL 落盘（模拟前端确认卡片：AI 草拟的 SKILL.md -> 人确认 -> POST）
$body = @{
    name = "activity-prep-thinking"
    description = "活动筹备思考框架：强度分级到后勤风险全维度"
    whenToUse = "发起人提出活动想法时"
    body = "---
name: activity-prep-thinking
description: 活动筹备思考框架：强度分级到后勤风险全维度
when_to_use: 发起人提出活动想法时
---

# Activity Prep Thinking

1. 强度分级：先评估活动强度与成员体能匹配度
2. 时间窗口：确认日期与天气/交通窗口
3. 补给住宿：长途活动提前规划补给点与住宿
4. 风险预案：准备备用路线与应急联系人
"
    sourceConceptId = $conceptId
} | ConvertTo-Json
$r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/ai/skill" $headers $body
Check "SKILL 落盘成功" ($r.code -eq 200 -and $r.data -match "SKILL.md")# 3) 文件校验：落盘路径存在 + frontmatter 三字段
$file = "C:\Users\lrs\Desktop\py\rag\club-agent\skills\activity-prep-thinking\SKILL.md"
Check "SKILL.md 文件存在" (Test-Path $file)
$content = [System.IO.File]::ReadAllText($file, [System.Text.Encoding]::UTF8)
Check "frontmatter 三字段" ($content.StartsWith("---") -and $content -match "name: activity-prep-thinking" -and $content -match "description:" -and $content -match "when_to_use:")

# 4) 安全：非法 name（路径穿越）拒绝
$badBody = @{ name = "../../etc"; description = "x"; whenToUse = "x"; body = "---`nname: x`n---"; sourceConceptId = $conceptId } | ConvertTo-Json
$r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/ai/skill" $headers $badBody
Check "非法 name 拒绝" ($r.code -eq 400)

# 5) 权限：非发起人落盘 403（副社长不是发起人）
$vpToken = Login "stu_4512db"
try {
    $body2 = @{ name = "x-skill"; description = "x"; whenToUse = "x"; body = "---`nname: x-skill`n---"; sourceConceptId = $conceptId } | ConvertTo-Json
    $r = Invoke-Api "POST" "$BASE/clubs/$CLUB_ID/ai/skill" @{ Authorization = "Bearer $vpToken" } $body2
    Check "非发起人落盘 403" ($r.code -eq 403)
} catch { Check "非发起人落盘 403" $false }

# 6) trace 留痕 ai_skill（按 detail 过滤，不依赖 First/Last 排序——历史 trace 可能有多条）
$d = Invoke-Api "GET" "$BASE/clubs/$CLUB_ID/concepts/$conceptId" $headers $null
$aiTrace = @($d.data.traces) | Where-Object { $_.action -eq "ai_skill" -and $_.detail -match "activity-prep-thinking" } | Select-Object -First 1
Check "trace 留痕 ai_skill" ($null -ne $aiTrace)

Write-Host "=== 结果：PASS=$passCount FAIL=$failCount ==="
if ($failCount -gt 0) { exit 1 }