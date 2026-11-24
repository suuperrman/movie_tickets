# 压测一键执行脚本（Windows PowerShell）
# 用法：
#   .\run-bench.ps1                                  # 默认参数
#   .\run-bench.ps1 -ThreadsOrder 50 -ThreadsOversell 100
#   .\run-bench.ps1 -JmeterHome "D:\apache-jmeter-5.6.3"

param(
    [string]$HostName = "localhost",
    [string]$Port = "8085",
    [int]$ThreadsSeatmap = 20,
    [int]$ThreadsOrder = 30,
    [int]$ThreadsOrders = 20,
    [int]$ThreadsPublic = 20,
    [int]$ThreadsOversell = 50,
    [int]$ThreadsPay = 20,
    [int]$ThreadsCancel = 20,
    [int]$ThreadsRefund = 10,
    [int]$ThreadsLogin = 20,
    [int]$LoopsSeatmap = 5,
    [int]$RampSeatmap = 10,
    [int]$LoopsLogin = 3,
    [string]$JmeterHome = $env:JMETER_HOME,
    [string]$ResultDir = ""
)

$ErrorActionPreference = "Stop"
$benchDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $benchDir

# ---------- 定位 JMeter ----------
function Find-Jmeter {
    param([string]$Hint)
    if ($Hint) {
        $exe = Join-Path $Hint "bin\jmeter.bat"
        if (Test-Path $exe) { return $exe }
    }
    $onPath = Get-Command jmeter.bat -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    $candidates = Get-ChildItem -Path "C:\", "D:\", "$env:USERPROFILE" -Filter "apache-jmeter-*" -Directory -ErrorAction SilentlyContinue
    foreach ($c in $candidates) {
        $exe = Join-Path $c.FullName "bin\jmeter.bat"
        if (Test-Path $exe) { return $exe }
    }
    return $null
}

$jmeter = Find-Jmeter -Hint $JmeterHome
if (-not $jmeter) {
    Write-Host "[错误] 未找到 JMeter。" -ForegroundColor Red
    Write-Host "  1) 下载 Apache JMeter 5.6.x 并解压，例如 D:\apache-jmeter-5.6.3"
    Write-Host "  2) 设置环境变量 JMETER_HOME，或运行时传参 -JmeterHome <路径>"
    Write-Host "  3) 需要 JDK 8+（当前 JAVA_HOME=$env:JAVA_HOME）"
    exit 1
}
Write-Host "[1/4] JMeter: $jmeter"

# ---------- 检查数据文件 ----------
foreach ($f in @("movie-bench.jmx", "targets.csv", "order-seats.csv", "oversell-seats.csv", "tokens.csv",
                 "pay-orders.csv", "cancel-orders.csv", "refund-orders.csv", "login-users.csv")) {
    if (-not (Test-Path $f)) {
        Write-Host "[错误] 缺少 $f，请先执行: python prepare_bench.py" -ForegroundColor Red
        exit 1
    }
}
$tokenCount = ((Get-Content "tokens.csv" | Measure-Object -Line).Lines - 1)
Write-Host "[2/4] 测试账号 $tokenCount 个"

# ---------- 后端连通性 ----------
try {
    $r = Invoke-WebRequest -Uri "http://${HostName}:${Port}/movie/now-showing?pageNo=1&pageSize=1" -TimeoutSec 10 -UseBasicParsing
    Write-Host "      后端 http://${HostName}:${Port} 可用 (HTTP $($r.StatusCode))"
} catch {
    Write-Host "[错误] 后端不可达：$($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ---------- 输出目录 ----------
if (-not $ResultDir) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $ResultDir = Join-Path $benchDir "results\$stamp"
}
New-Item -ItemType Directory -Force -Path $ResultDir | Out-Null
$jtl = Join-Path $ResultDir "result.jtl"
$html = Join-Path $ResultDir "html"

# ---------- 运行 ----------
Write-Host "[3/4] 开始压测，输出目录：$ResultDir"
$jmeterArgs = @(
    "-n",
    "-t", "movie-bench.jmx",
    "-Jhost=$HostName",
    "-Jport=$Port",
    "-Jbench.dir=$($benchDir -replace '\\','/')",
    "-Jthreads.seatmap=$ThreadsSeatmap",
    "-Jthreads.order=$ThreadsOrder",
    "-Jthreads.orders=$ThreadsOrders",
    "-Jthreads.public=$ThreadsPublic",
    "-Jthreads.oversell=$ThreadsOversell",
    "-Jthreads.pay=$ThreadsPay",
    "-Jthreads.cancel=$ThreadsCancel",
    "-Jthreads.refund=$ThreadsRefund",
    "-Jthreads.login=$ThreadsLogin",
    "-Jloops.seatmap=$LoopsSeatmap",
    "-Jramp.seatmap=$RampSeatmap",
    "-Jloops.login=$LoopsLogin",
    "-l", $jtl,
    "-e", "-o", $html
)
& $jmeter @jmeterArgs
$code = $LASTEXITCODE

if ($code -ne 0) {
    Write-Host "[错误] JMeter 退出码 $code" -ForegroundColor Red
    exit $code
}

# ---------- 汇总 ----------
Write-Host "[4/4] 生成指标汇总"
python summarize.py $jtl "oversell-seats.csv"

Write-Host ""
Write-Host "HTML 报告：$html\index.html"
Write-Host "原始结果：$jtl"
