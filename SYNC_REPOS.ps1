$currentDir = Get-Location
Write-Host ">>> INICIANDO SINCRONIZACION (PULL & PUSH) DESDE: $currentDir <<<" -ForegroundColor Cyan

# Mapeo: Carpeta Local -> URL Remota (Solo los solicitados)
$mapping = @(
    @{ Path = "MSNucleoSwitch"; Url = "https://github.com/AlisonTamayo/ms-nucleo.git" },
    @{ Path = "MSCompensacionSwitch"; Url = "https://github.com/AlisonTamayo/ms-compensacion.git" },
    @{ Path = "MSDevolucionSwitch"; Url = "https://github.com/AlisonTamayo/ms-devolucion.git" },
    @{ Path = "Switch-ms-contabilidad"; Url = "https://github.com/AlisonTamayo/ms-contabilidad.git" },
    @{ Path = "ms-directorio"; Url = "https://github.com/AlisonTamayo/ms-directorio.git" },
    @{ Path = "switch-frontend"; Url = "https://github.com/AlisonTamayo/switch-frontend.git" }
)

foreach ($item in $mapping) {
    $folderName = $item.Path
    $remoteUrl = $item.Url
    
    Write-Host ""
    Write-Host "-----------------------------------------------------"
    
    if (Test-Path $folderName) {
        Write-Host "Procesando: $folderName" -ForegroundColor Yellow
        Push-Location $folderName

        # 1. Verificación Inicial de Git
        if (-not (Test-Path ".git")) {
            Write-Host "   [INIT] Inicializando git..."
            git init | Out-Null
            git branch -m main 2>$null
        }

        # 2. Configurar Remote
        $remotes = git remote -v 2>$null
        if ($remotes -match "origin") {
            Write-Host "   [REMOTE] Actualizando URL origin: $remoteUrl"
            git remote set-url origin $remoteUrl
        }
        else {
            Write-Host "   [REMOTE] Agregando origin: $remoteUrl"
            git remote add origin $remoteUrl
        }

        # 3. PULL (Bajar cambios)
        Write-Host "   [PULL] Bajando cambios..."
        # Intentamos pull normal, si falla por historias no relacionadas, intentamos con flag
        git pull origin main 2>&1 | Out-Null
        if ($LASTEXITCODE -ne 0) {
             Write-Host "   [PULL] Pull normal falló, intentando con --allow-unrelated-histories..."
             git pull origin main --allow-unrelated-histories 2>&1 | Out-Null
        }

        # 4. COMMIT (Guardar cambios locales)
        Write-Host "   [ADD] Agregando archivos..."
        git add -A 2>$null
        
        $status = git status --porcelain
        if ($status) {
            Write-Host "   [COMMIT] Realizando commit..."
            git commit -m "Sincronizacion Automatica: Pull & Push" | Out-Null
        }
        else {
            Write-Host "   [COMMIT] No hay cambios locales pendientes."
        }

        # 5. PUSH (Subir cambios)
        Write-Host "   [PUSH] Subiendo a GitHub..."
        git push -u origin main
        
        if ($?) {
            Write-Host "   [SUCCESS] $folderName Sincronizado Correctamente." -ForegroundColor Green
        }
        else {
            Write-Host "   [ERROR] Fallo el push en $folderName." -ForegroundColor Red
        }

        Pop-Location
    }
    else {
        Write-Host "ADVERTENCIA: Carpeta no encontrada: $folderName" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host ">>> FIN DEL PROCESO <<<" -ForegroundColor Cyan
