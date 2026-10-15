# test_suite.ps1
$baseUrl = "http://localhost:8080"
$report = [System.Collections.Generic.List[PSCustomObject]]::new()

function Call-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $status = 0
    $data = $null
    try {
        $p = @{
            Uri = "$baseUrl$Path"
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
            UseBasicParsing = $true
        }
        if ($Body) { $p["Body"] = $Body }
        $raw = Invoke-WebRequest @p
        $status = [int]$raw.StatusCode
        if ($raw.Content) {
            try { $data = $raw.Content | ConvertFrom-Json } catch { $data = $raw.Content }
        }
    } catch {
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $rawJson = $reader.ReadToEnd()
            try { $data = $rawJson | ConvertFrom-Json } catch { $data = $rawJson }
        } else {
            $status = 500
            $data = $_.Exception.Message
        }
    }
    $sw.Stop()
    return [PSCustomObject]@{
        Status = $status
        TimeMs = $sw.ElapsedMilliseconds
        Data   = $data
    }
}

function Assert-Result {
    param(
        [string]$Modulo,
        [string]$Endpoint,
        [string]$Caso,
        [int]$EsperadoStatus,
        $ApiResult,
        [scriptblock]$Validation = $null,
        [string]$DefectoDesc = ""
    )
    $status = $ApiResult.Status
    $timeMs = $ApiResult.TimeMs
    $data = $ApiResult.Data
    
    $ok = ($status -eq $EsperadoStatus) -and ($timeMs -lt 1500)
    $obs = ""

    if ($Validation -and $ok) {
        $valid = & $Validation $data
        if ($valid -ne $true) {
            $ok = $false
            $obs = "Validación falló: $valid"
        }
    }

    if (-not $ok -and $DefectoDesc) {
        $obs = $DefectoDesc
    }

    $estado = if ($ok) { "OK" } else { "FALLA" }

    $row = [PSCustomObject]@{
        Modulo   = $Modulo
        Endpoint = $Endpoint
        Caso     = $Caso
        Esperado = "$EsperadoStatus (<1500ms)"
        Obtenido = "$status (${timeMs}ms)"
        Estado   = $estado
        Notas    = $obs
    }
    $report.Add($row)
}

# --- 1. AUTH ---
# Login Admin exitoso
$r1 = Call-Api "POST" "/api/v1/auth/login" @{} '{"nombre_usuario":"admin","pin":"1234"}'
Assert-Result "1. Auth" "POST /api/v1/auth/login" "Admin exitoso" 200 $r1 {
    param($d)
    return ($null -ne $d.token -and $d.rol -eq "ADMIN")
}
$token = $r1.Data.token
$authH = @{ "Authorization" = "Bearer $token" }

# Login PIN incorrecto - 401
$r2 = Call-Api "POST" "/api/v1/auth/login" @{} '{"nombre_usuario":"admin","pin":"9999"}'
Assert-Result "1. Auth" "POST /api/v1/auth/login" "PIN incorrecto - 401" 401 $r2 {
    param($d)
    return ($d.codigo -eq "AUTH-PIN-INCORRECTO" -and $d.error -ne $null)
}

# Login Operario bloqueado - 423
# Creamos un usuario de prueba e intentamos bloquearlo con 3 PIN erróneos
$randB = Get-Random -Minimum 100000 -Maximum 999999
$userBloq = "bloq_$randB"
$rSetup = Call-Api "POST" "/api/v1/operarios" $authH "{`"nombre_completo`":`"Cajero Bloq`",`"numero_documento`":`"98$randB`",`"nombre_usuario`":`"$userBloq`",`"pin`":`"1111`",`"rol`":`"CAJERO`"}"
for ($i=0; $i -lt 3; $i++) {
    $null = Call-Api "POST" "/api/v1/auth/login" @{} "{`"nombre_usuario`":`"$userBloq`",`"pin`":`"0000`"}"
}
$r3 = Call-Api "POST" "/api/v1/auth/login" @{} "{`"nombre_usuario`":`"$userBloq`",`"pin`":`"0000`"}"
Assert-Result "1. Auth" "POST /api/v1/auth/login" "Operario bloqueado - 423" 423 $r3 $null "Defecto: @Transactional en login hace rollback y no persiste intentos_fallidos (retorna 401 en vez de 423)"

# Logout - 204
$rTemp = Call-Api "POST" "/api/v1/auth/login" @{} '{"nombre_usuario":"admin","pin":"1234"}'
$r4 = Call-Api "POST" "/api/v1/auth/logout" @{ "Authorization" = "Bearer $($rTemp.Data.token)" }
Assert-Result "1. Auth" "POST /api/v1/auth/logout" "Logout exitoso" 204 $r4

# --- 2. OPERARIOS ---
# GET /operarios
$r5 = Call-Api "GET" "/api/v1/operarios" $authH
Assert-Result "2. Operarios" "GET /api/v1/operarios" "Listar operarios" 200 $r5 {
    param($d)
    return ($d -is [array])
}

# POST /operarios
$randO = Get-Random -Minimum 100000 -Maximum 999999
$cajeroU = "cajero_$randO"
$r6 = Call-Api "POST" "/api/v1/operarios" $authH "{`"nombre_completo`":`"Cajero Postman Test`",`"numero_documento`":`"10$randO`",`"telefono`":`"3001234567`",`"nombre_usuario`":`"$cajeroU`",`"pin`":`"4321`",`"rol`":`"CAJERO`"}"
Assert-Result "2. Operarios" "POST /api/v1/operarios" "Crear operario" 201 $r6 {
    param($d)
    return ($null -ne $d.id_operario -and $d.activo -eq $true)
}
$idOperario = $r6.Data.id_operario

# GET /operarios/{id}
$r7 = Call-Api "GET" "/api/v1/operarios/$idOperario" $authH
Assert-Result "2. Operarios" "GET /api/v1/operarios/{id_operario}" "Obtener operario por ID" 200 $r7 {
    param($d)
    return ($d.id_operario -eq $idOperario)
}

# PUT /operarios/{id}
$r8 = Call-Api "PUT" "/api/v1/operarios/$idOperario" $authH "{`"nombre_completo`":`"Cajero Modificado`",`"telefono`":`"3119876543`",`"nombre_usuario`":null,`"rol`":`"CAJERO`"}"
Assert-Result "2. Operarios" "PUT /api/v1/operarios/{id_operario}" "Actualizar operario" 200 $r8 {
    param($d)
    return ($d.nombre_completo -eq "Cajero Modificado")
}

# PATCH /operarios/{id}/pin
$r9 = Call-Api "PATCH" "/api/v1/operarios/$idOperario/pin" $authH '{"pin_nuevo":"5678"}'
Assert-Result "2. Operarios" "PATCH /api/v1/operarios/{id_operario}/pin" "Actualizar PIN" 200 $r9 {
    param($d)
    return ($null -ne $d.mensaje)
}

# PATCH /operarios/{id}/desbloquear
$r10 = Call-Api "PATCH" "/api/v1/operarios/$idOperario/desbloquear" $authH
Assert-Result "2. Operarios" "PATCH /api/v1/operarios/{id_operario}/desbloquear" "Desbloquear operario" 200 $r10 {
    param($d)
    return ($d.intentos_fallidos -eq 0)
}

# PATCH /operarios/{id}/inactivar
$r11 = Call-Api "PATCH" "/api/v1/operarios/$idOperario/inactivar" $authH
Assert-Result "2. Operarios" "PATCH /api/v1/operarios/{id_operario}/inactivar" "Inactivar operario" 200 $r11 {
    param($d)
    return ($d.activo -eq $false)
}

# --- 3. TURNOS DE CAJA ---
# Limpieza preventiva
$rActPre = Call-Api "GET" "/api/v1/turnos/activo" $authH
if ($rActPre.Data.id_turno) {
    $null = Call-Api "POST" "/api/v1/turnos/$($rActPre.Data.id_turno)/cerrar" $authH '{"monto_fisico_arqueo":50000.00,"observaciones_cierre":"Limpieza previa"}'
}

# POST /turnos/abrir
$r12 = Call-Api "POST" "/api/v1/turnos/abrir" $authH '{"base_efectivo_inicial":50000.00}'
Assert-Result "3. Turnos de Caja" "POST /api/v1/turnos/abrir" "Abrir turno" 201 $r12 {
    param($d)
    return ($null -ne $d.id_turno -and $d.estado -eq "ABIERTO")
}
$idTurno = $r12.Data.id_turno

# GET /turnos/activo
$r13 = Call-Api "GET" "/api/v1/turnos/activo" $authH
Assert-Result "3. Turnos de Caja" "GET /api/v1/turnos/activo" "Consultar turno activo" 200 $r13 {
    param($d)
    return ($d.id_turno -eq $idTurno -and $d.estado -eq "ABIERTO")
}

# GET /turnos/{id}
$r14 = Call-Api "GET" "/api/v1/turnos/$idTurno" $authH
Assert-Result "3. Turnos de Caja" "GET /api/v1/turnos/{id_turno}" "Consultar turno por ID" 200 $r14 {
    param($d)
    return ($d.id_turno -eq $idTurno)
}

# POST /turnos/{id}/cerrar
$r15 = Call-Api "POST" "/api/v1/turnos/$idTurno/cerrar" $authH '{"monto_fisico_arqueo":50000.00,"observaciones_cierre":"Cierre normal"}'
Assert-Result "3. Turnos de Caja" "POST /api/v1/turnos/{id_turno}/cerrar" "Cerrar turno con arqueo" 200 $r15 {
    param($d)
    return ($d.estado -eq "CERRADO")
}

# --- 4. CATEGORÍAS ---
# POST /categorias
$randC = Get-Random -Minimum 100000 -Maximum 999999
$r16 = Call-Api "POST" "/api/v1/categorias" $authH "{`"nombre`":`"Bebidas $randC`",`"descripcion`":`"Refrescos`"}"
Assert-Result "4. Categorías" "POST /api/v1/categorias" "Crear categoría" 201 $r16 {
    param($d)
    return ($null -ne $d.id_categoria -and $d.activo -eq $true)
}
$idCategoria = $r16.Data.id_categoria

# GET /categorias
$r17 = Call-Api "GET" "/api/v1/categorias" $authH
Assert-Result "4. Categorías" "GET /api/v1/categorias" "Listar categorías" 200 $r17 {
    param($d)
    return ($d -is [array])
}

# PUT /categorias/{id}
$r18 = Call-Api "PUT" "/api/v1/categorias/$idCategoria" $authH "{`"nombre`":`"Bebidas Modificadas $randC`",`"descripcion`":`"Actualizada`"}"
Assert-Result "4. Categorías" "PUT /api/v1/categorias/{id_categoria}" "Actualizar categoría" 200 $r18 {
    param($d)
    return ($d.nombre -like "*Bebidas Modificadas*")
}

# PATCH /categorias/{id}/inactivar
$rCatAux = Call-Api "POST" "/api/v1/categorias" $authH "{`"nombre`":`"Cat Inact $randC`",`"descripcion`":`"Aux`"}"
$r19 = Call-Api "PATCH" "/api/v1/categorias/$($rCatAux.Data.id_categoria)/inactivar" $authH
Assert-Result "4. Categorías" "PATCH /api/v1/categorias/{id_categoria}/inactivar" "Inactivar categoría" 200 $r19 {
    param($d)
    return ($d.activo -eq $false)
}

# --- 5. PRODUCTOS ---
# POST /productos
$randP = Get-Random -Minimum 100000 -Maximum 999999
$barcode = "770" + $randP.ToString().PadLeft(10, '0')
$r20 = Call-Api "POST" "/api/v1/productos" $authH "{`"codigo_barras`":`"$barcode`",`"nombre`":`"Gaseosa $randP`",`"descripcion`":`"500ml`",`"id_categoria`":$idCategoria,`"precio_venta`":4500.00,`"costo_inicial`":2500.00,`"stock_inicial`":50,`"aplica_iva`":true,`"porcentaje_iva`":19.00,`"stock_minimo`":10,`"unidad_medida`":`"UNIDAD`"}"
Assert-Result "5. Productos" "POST /api/v1/productos" "Crear producto" 201 $r20 {
    param($d)
    return ($null -ne $d.id_producto -and $d.stock_actual -eq 50)
}
$idProducto = $r20.Data.id_producto

# GET /productos
$r21 = Call-Api "GET" "/api/v1/productos" $authH
Assert-Result "5. Productos" "GET /api/v1/productos" "Listar productos" 200 $r21 {
    param($d)
    return ($d -is [array])
}

# GET /productos/{id}
$r22 = Call-Api "GET" "/api/v1/productos/$idProducto" $authH
Assert-Result "5. Productos" "GET /api/v1/productos/{id_producto}" "Obtener producto por ID" 200 $r22 {
    param($d)
    return ($d.id_producto -eq $idProducto)
}

# GET /productos/barcode/{codigo}
$r23 = Call-Api "GET" "/api/v1/productos/barcode/$barcode" $authH
Assert-Result "5. Productos" "GET /api/v1/productos/barcode/{codigo_barras}" "Buscar por código de barras" 200 $r23 {
    param($d)
    return ($d.codigo_barras -eq $barcode)
}

# PUT /productos/{id}
$r24 = Call-Api "PUT" "/api/v1/productos/$idProducto" $authH "{`"codigo_barras`":`"$barcode`",`"nombre`":`"Gaseosa Modificada $randP`",`"descripcion`":`"600ml`",`"id_categoria`":$idCategoria,`"precio_venta`":4800.00,`"aplica_iva`":true,`"porcentaje_iva`":19.00,`"stock_minimo`":8,`"unidad_medida`":`"UNIDAD`"}"
Assert-Result "5. Productos" "PUT /api/v1/productos/{id_producto}" "Actualizar producto" 200 $r24 {
    param($d)
    return ($d.precio_venta -eq 4800.00)
}

# PATCH /productos/{id}/inactivar
$rProdAux = Call-Api "POST" "/api/v1/productos" $authH "{`"codigo_barras`":`"99$randP`",`"nombre`":`"Prod Inact $randP`",`"descripcion`":`"aux`",`"id_categoria`":$idCategoria,`"precio_venta`":2000.00,`"costo_inicial`":1000.00,`"stock_inicial`":5,`"aplica_iva`":false,`"stock_minimo`":1,`"unidad_medida`":`"UNIDAD`"}"
$r25 = Call-Api "PATCH" "/api/v1/productos/$($rProdAux.Data.id_producto)/inactivar" $authH
Assert-Result "5. Productos" "PATCH /api/v1/productos/{id_producto}/inactivar" "Inactivar producto" 200 $r25 {
    param($d)
    return ($d.activo -eq $false)
}

# --- 6. INVENTARIO ---
# POST /inventario/compra
$r26 = Call-Api "POST" "/api/v1/inventario/compra" $authH "{`"id_producto`":$idProducto,`"cantidad`":20,`"costo_unitario`":2800.00,`"id_referencia`":`"FAC-PROV-9988`",`"motivo_ajuste`":null}"
Assert-Result "6. Inventario" "POST /api/v1/inventario/compra" "Abastecimiento y recálculo de CPP" 201 $r26 {
    param($d)
    return ($d.tipo_movimiento -eq "COMPRA" -and $d.saldo_resultante -eq 70)
}

# Salida formateada
$report | Format-Table -AutoSize Modulo, Endpoint, Caso, Esperado, Obtenido, Estado, Notas
$report | ConvertTo-Json -Depth 3 | Out-File -FilePath "postman/test_results.json" -Encoding utf8
