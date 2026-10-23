$rows = [System.Collections.Generic.List[string]]::new()
$rows.Add('level,mode,bot_pass_rate_not_measured,human_like_pass_rate_not_measured,peg_count,speed,perfect_window')
for ($level = 1; $level -le 50; $level++) {
  $count = [Math]::Min(40, 5 + [int][Math]::Floor(($level - 1) * 35 / 29))
  $speed = 0.0021 * (1 + [Math]::Max(0, $level - 16) * 0.027)
  $window = [Math]::Max(0.06, 0.20 - [Math]::Min($level - 1, 29) * (0.12 / 29))
  $rows.Add("$level,FREE_PLAY,not measured,not measured,$count,$('{0:F6}' -f $speed),$('{0:F4}' -f $window)")
}
$ids = @('pyramids','eiffel_tower','big_ben','mount_fuji','space_needle','leaning_tower_of_pisa','sydney_opera_house','burj_khalifa','golden_gate_bridge','stonehenge','taj_mahal','gateway_of_india','qutub_minar')
$asset = Get-Content -Raw (Join-Path $PSScriptRoot '..\app\src\main\assets\landmarks.json') | ConvertFrom-Json
$difficultySpeed = @{ EASY = .80; MEDIUM = 1.0; HARD = 1.25 }
$difficultyWindow = @{ EASY = 1.18; MEDIUM = 1.0; HARD = .82 }
for ($i = 0; $i -lt $ids.Count; $i++) {
  $definition = $asset | Where-Object id -eq $ids[$i]
  foreach ($d in @('EASY','MEDIUM','HARD')) {
    $level = $i * 3 + (@('EASY','MEDIUM','HARD').IndexOf($d)) + 1
    $baseSpeed = .0021 * (1 + [Math]::Max(0, $level - 16) * .027)
    $speed = $baseSpeed * $difficultySpeed[$d]
    $baseWindow = [Math]::Max(.06, [Math]::Min(.20, .20 - ($level - 1) * (.12 / 29)))
    $window = [Math]::Max(.06, [Math]::Min(.20, $baseWindow * $difficultyWindow[$d]))
    $rows.Add("$level`:$d,LANDMARK_$($ids[$i]),not measured,not measured,$($definition.pegs.Count),$('{0:F6}' -f $speed),$('{0:F4}' -f $window)")
  }
}
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot '..\difficulty_report.csv'), $rows)
