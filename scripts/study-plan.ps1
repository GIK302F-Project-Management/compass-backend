function Read-StudyDate([string]$label) {
    while ($true) {
        $text = Read-Host "$label (YYYY-MM-DD)"
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

        Write-Host "Invalid date. Example: 2026-11-05."
    }
}

function Read-StudyMinutes([string]$label, [int]$maximum) {
    while ($true) {
        $text = (Read-Host "$label in hours (e.g. 1.5)").Replace(",", ".")
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

        Write-Host "Enter a positive duration, in whole minutes, up to $($maximum / 60) hours."
    }
}

# The API only answers to a logged-in user: sign in first and send the token with the request.
$apiUrl = "http://localhost:8080"
$email = Read-Host "Email"
$securePassword = Read-Host "Password" -AsSecureString
$password = [System.Net.NetworkCredential]::new("", $securePassword).Password

try {
    $login = Invoke-RestMethod `
        -Uri "$apiUrl/user/login" `
        -Method Post `
        -ContentType "application/json; charset=utf-8" `
        -Body ([System.Text.Encoding]::UTF8.GetBytes((@{ email = $email; password = $password } | ConvertTo-Json))) `
        -ErrorAction Stop
} catch {
    Write-Host "Could not sign in:"
    Write-Host $_.Exception.Message
    exit 1
}

Write-Host "Signed in as $($login.user.name)."

$start = Read-StudyDate "Start date"

$dailyMinutes = Read-StudyMinutes "Daily availability" 1440

$goals = @()

do {
    do {
        $title = Read-Host "Goal title"
    } while ([string]::IsNullOrWhiteSpace($title) -or $title.Length -gt 200)

    do {
        $deadline = Read-StudyDate "Goal deadline"
        $days = ($deadline - $start).Days

        if ($days -lt 0 -or $days -gt 365) {
            Write-Host "The deadline must be between the start date and 365 days after it."
        }
    } while ($days -lt 0 -or $days -gt 365)

    $estimatedMinutes = Read-StudyMinutes "Total time required for this goal" 10080

    $goals += @{
        id = "goal-$($goals.Count + 1)"
        title = $title.Trim()
        deadline = $deadline.ToString("yyyy-MM-dd")
        estimatedMinutes = $estimatedMinutes
    }

    $another = if ($goals.Count -lt 100) {
        Read-Host "Add another goal? (y/n)"
    } else {
        "n"
    }
} while ($another -eq "y")

$body = @{
    startDate = $start.ToString("yyyy-MM-dd")
    dailyAvailableMinutes = $dailyMinutes
    goals = @($goals)
} | ConvertTo-Json -Depth 6

$params = @{
    Uri = "$apiUrl/api/study-plans"
    Method = "Post"
    Headers = @{ Authorization = "Bearer $($login.token)" }
    ContentType = "application/json; charset=utf-8"
    Body = [System.Text.Encoding]::UTF8.GetBytes($body)
    TimeoutSec = 120
    ErrorAction = "Stop"
}

try {
    Write-Host "Generating plan..."

    $plan = Invoke-RestMethod @params

    Write-Host "Generator:" $plan.source
    Write-Host "Status:" $plan.status

    $plan.sessions |
        Sort-Object date |
        Select-Object `
            @{Name="Date"; Expression={$_.date}},
            @{Name="Goal"; Expression={$_.goalId}},
            @{Name="Activity"; Expression={$_.title}},
            @{Name="Hours"; Expression={
                [math]::Round($_.durationMinutes / 60, 2)
            }} |
        Format-Table -AutoSize -Wrap

    foreach ($warning in $plan.warnings) {
        Write-Host "Warning:" $warning
    }
} catch {
    Write-Host "Could not generate the plan:"
    Write-Host $_.Exception.Message

    if ($_.ErrorDetails.Message) {
        Write-Host $_.ErrorDetails.Message
    }
}