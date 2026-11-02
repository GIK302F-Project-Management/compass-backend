function Read-StudyDate([string]$label) {
    while ($true) {
        $text = Read-Host "$label (AAAA-MM-DD)"
        $date = [datetime]::MinValue

        $valid = [datetime]::TryParseExact(
            $text,
            "yyyy-MM-dd",
            [System.Globalization.CultureInfo]::InvariantCulture,
            [System.Globalization.DateTimeStyles]::None,
            [ref]$date
        )

        if ($valid) {
            return $date
        }

        Write-Host "Data inválida. Exemplo: 2026-11-05."
    }
}

function Read-StudyMinutes([string]$label, [int]$maximum) {
    while ($true) {
        $text = (Read-Host "$label em horas (ex.: 1,5)").Replace(",", ".")
        $hours = [decimal]0

        $valid = [decimal]::TryParse(
            $text,
            [System.Globalization.NumberStyles]::AllowDecimalPoint,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref]$hours
        )

        if ($valid -and $hours -gt 0 -and $hours -le ($maximum / 60)) {
            $minutes = $hours * 60

            if ($minutes -eq [decimal]::Truncate($minutes)) {
                return [int]$minutes
            }
        }

        Write-Host "Informe uma duração positiva, em minutos inteiros, até $($maximum / 60) horas."
    }
}

$start = Read-StudyDate "Data inicial"

$dailyMinutes = Read-StudyMinutes "Disponibilidade por dia" 1440

$goals = @()

do {
    do {
        $title = Read-Host "Título da meta"
    } while ([string]::IsNullOrWhiteSpace($title) -or $title.Length -gt 200)

    do {
        $deadline = Read-StudyDate "Prazo da meta"
        $days = ($deadline - $start).Days

        if ($days -lt 0 -or $days -gt 365) {
            Write-Host "O prazo deve ficar entre a data inicial e 365 dias depois."
        }
    } while ($days -lt 0 -or $days -gt 365)

    $estimatedMinutes = Read-StudyMinutes "Tempo total necessário para essa meta" 10080

    $goals += @{
        id = "goal-$($goals.Count + 1)"
        title = $title.Trim()
        deadline = $deadline.ToString("yyyy-MM-dd")
        estimatedMinutes = $estimatedMinutes
    }

    $another = if ($goals.Count -lt 100) {
        Read-Host "Adicionar outra meta? (s/n)"
    } else {
        "n"
    }
} while ($another -eq "s")

$body = @{
    startDate = $start.ToString("yyyy-MM-dd")
    dailyAvailableMinutes = $dailyMinutes
    goals = @($goals)
} | ConvertTo-Json -Depth 6

$params = @{
    Uri = "http://localhost:8080/api/study-plans"
    Method = "Post"
    ContentType = "application/json; charset=utf-8"
    Body = [System.Text.Encoding]::UTF8.GetBytes($body)
    TimeoutSec = 120
    ErrorAction = "Stop"
}

try {
    Write-Host "Gerando plano..."

    $plan = Invoke-RestMethod @params

    Write-Host "Gerador:" $plan.source
    Write-Host "Status:" $plan.status

    $plan.sessions |
        Sort-Object date |
        Select-Object `
            @{Name="Data"; Expression={$_.date}},
            @{Name="Meta"; Expression={$_.goalId}},
            @{Name="Atividade"; Expression={$_.title}},
            @{Name="Horas"; Expression={
                [math]::Round($_.durationMinutes / 60, 2)
            }} |
        Format-Table -AutoSize -Wrap

    foreach ($warning in $plan.warnings) {
        Write-Host "Aviso:" $warning
    }
} catch {
    Write-Host "Não foi possível gerar o plano:"
    Write-Host $_.Exception.Message

    if ($_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message
    }
}