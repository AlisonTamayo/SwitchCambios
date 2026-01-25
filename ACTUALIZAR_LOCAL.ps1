$currentDir = Get-Location
Write-Host ">>> ACTUALIZANDO REPOSITORIOS LOCALES EN: $currentDir <<<" -ForegroundColor Cyan

# Mapeo: Carpeta Local -> URL Remota
$mapping = @(
    @{ Path = "repo_switch_transaccional"; Url = "https://github.com/AlisonTamayo/switch-transaccional.git" },
    @{ Path = "MSNucleoSwitch"; Url = "https://github.com/AlisonTamayo/ms-nucleo.git" },
    @{ Path = "ms-directorio"; Url = "https://github.com/AlisonTamayo/ms-directorio.git" },
    @{ Path = "Switch-ms-contabilidad"; Url = "https://github.com/AlisonTamayo/ms-contabilidad.git" },
    @{ Path = "MSCompensacionSwitch"; Url = "https://github.com/AlisonTamayo/ms-compensacion.git" },
    @{ Path = "MSDevolucionSwitch"; Url = "https://github.com/AlisonTamayo/ms-devolucion.git" },
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

        # 1. Asegurar Remoto Correcto
        if (-not (Test-Path ".git")) {
            Write-Host "   - Inicializando git..."
            git init | Out-Null
        }
        
        $remotes = git remote -v 2>$null
        if ($remotes -match "origin") {
            Write-Host "   - asegurando origin -> $remoteUrl"
            git remote set-url origin $remoteUrl
        }
        else {
            Write-Host "   - configurando origin -> $remoteUrl"
            git remote add origin $remoteUrl
        }

        # 2. Hacer Pull
        Write-Host "   - Trayendo cambios (git pull)..."
        git pull origin main
        
        if ($?) {
            Write-Host "   OK: Actualizado." -ForegroundColor Green
        }
        else {
            Write-Host "   ERROR: Fallo el pull. Revisa si tienes conflictos locales." -ForegroundColor Red
        }

        Pop-Location
    }
    else {
        Write-Host "ADVERTENCIA: Carpeta no encontrada: $folderName" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host ">>> ACTUALIZACION COMPLETADA <<<" -ForegroundColor Cyan
