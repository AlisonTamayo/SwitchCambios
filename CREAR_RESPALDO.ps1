$currentDir = Get-Location
$branchName = "respaldo-estable"
Write-Host ">>> CREANDO RAMA DE RESPALDO '$branchName' DESDE: $currentDir <<<" -ForegroundColor Cyan

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
    
    Write-Host ""
    Write-Host "-----------------------------------------------------"
    
    if (Test-Path $folderName) {
        Write-Host "Procesando: $folderName" -ForegroundColor Yellow
        Push-Location $folderName

        # 1. Crear y cambiar a la rama de respaldo
        # git checkout -B crea la rama si no existe, o la resetea si ya existe
        Write-Host "   - Creando/Cambiando a rama '$branchName'..."
        git checkout -B $branchName

        # 2. Agregar cambios por si acaso (aunque ya debería estar limpio)
        git add -A 2>$null
        $status = git status --porcelain
        if ($status) {
            Write-Host "   - Guardando cambios pendientes..."
            git commit -m "Backup: Punto de restauración estable" | Out-Null
        }

        # 3. Push a la nueva rama
        Write-Host "   - Subiendo rama '$branchName' a GitHub..."
        git push -u origin $branchName -f
        
        if ($?) {
            Write-Host "   OK: Respaldo subido correctamente." -ForegroundColor Green
        }
        else {
            Write-Host "   ERROR: Fallo al subir el respaldo." -ForegroundColor Red
        }
        
        # Opcional: Volver a main si prefieres seguir trabajando allí
        Write-Host "   - Regresando a 'main'..."
        git checkout main | Out-Null

        Pop-Location
    }
    else {
        Write-Host "ADVERTENCIA: Carpeta no encontrada: $folderName" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host ">>> RESPALDO COMPLETADO <<<" -ForegroundColor Cyan
