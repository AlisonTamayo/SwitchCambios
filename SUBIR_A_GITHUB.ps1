$currentDir = Get-Location
Write-Host ">>> INICIANDO SUBIDA DESDE: $currentDir <<<" -ForegroundColor Cyan

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

        # 1. Inicializar Git
        if (-not (Test-Path ".git")) {
            Write-Host "   - Inicializando git..."
            git init | Out-Null
        }

        # 2. Main Branch
        git branch -m main 2>$null

        # 3. Remote
        $remotes = git remote -v 2>$null
        if ($remotes -match "origin") {
            Write-Host "   - Actualizando remoto: $remoteUrl"
            git remote set-url origin $remoteUrl
        }
        else {
            Write-Host "   - Configurando remoto: $remoteUrl"
            git remote add origin $remoteUrl
        }

        # 4. Commit
        Write-Host "   - Git Add All (incluyendo borrados)..."
        git add -A 2>$null
        
        $status = git status --porcelain
        if ($status) {
            Write-Host "   - Git Commit..."
            git commit -m "Colas RabbitMQ flujo asincrono + guia implementacion bancos" | Out-Null
        }
        else {
            Write-Host "   - Nada nuevo por subir."
        }

        # 5. Push
        Write-Host "   Subiendo a GitHub..."
        git push -u origin main
        
        if ($?) {
            Write-Host "   OK: Configurado Correctamente." -ForegroundColor Green
        }
        else {
            Write-Host "   ERROR: Fallo el push. Intenta manual: git push -f" -ForegroundColor Red
        }

        Pop-Location
    }
    else {
        Write-Host "ADVERTENCIA: Carpeta no encontrada: $folderName" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host ">>> FIN DEL PROCESO <<<" -ForegroundColor Cyan
