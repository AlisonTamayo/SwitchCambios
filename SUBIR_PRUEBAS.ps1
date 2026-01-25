$currentDir = Get-Location
$branchName = "pruebas"
Write-Host ">>> SUBIENDO CAMBIOS PARA EVALUACIÓN DE CÓDIGO A RAMA '$branchName' DESDE: $currentDir <<<" -ForegroundColor Cyan

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

        # 1. Crear y cambiar a la rama de pruebas (o resetearla si existe)
        Write-Host "   - Creando/Cambiando a rama '$branchName'..."
        git checkout -B $branchName

        # 2. Agregar cambios
        git add -A 2>$null
        $status = git status --porcelain
        if ($status) {
            Write-Host "   - Guardando cambios para evaluación..."
            git commit -m "Evaluación de código: Subida de cambios para pruebas RF-01" | Out-Null
        }

        # 3. Push a la rama de pruebas
        Write-Host "   - Subiendo rama '$branchName' a GitHub..."
        git push -u origin $branchName -f
        
        if ($?) {
            Write-Host "   OK: Cambios de evaluación subidos correctamente." -ForegroundColor Green
        }
        else {
            Write-Host "   ERROR: Fallo al subir los cambios." -ForegroundColor Red
        }
        
        # Opcional: Volver a main (comentar si se desea permanecer en pruebas)
        Write-Host "   - Regresando a 'main'..."
        git checkout main | Out-Null

        Pop-Location
    }
    else {
        Write-Host "ADVERTENCIA: Carpeta no encontrada: $folderName" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host ">>> PROCESO DE SUBIDA A PRUEBAS COMPLETADO <<<" -ForegroundColor Cyan
