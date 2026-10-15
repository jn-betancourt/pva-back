# runner_httpclient.ps1
Add-Type -AssemblyName System.Net.Http

$client = New-Object System.Net.Http.HttpClient
$baseUrl = "http://localhost:8080"
$results = [System.Collections.Generic.List[PSCustomObject]]::new()

function Exec-Req {
    param(
        [string]$Modulo,
        [string]$Metodo,
        [string]$Ruta,
        [string]$Caso,
        [int]$EsperadoStatus,
        [string]$Token = $null,
        [string]$Body = $null,
        [scriptblock]$ExtraAssert = $null
    )

    $httpMethod = New-Object System.Net.Http.HttpMethod($Metodo)
    $uriObj = [System.Uri]::new($url)
    $req = New-Object System.Net.Http.HttpRequestMessage($httpMethod, $uriObj)
    
    if ($Token) {
        $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $Token)
    }
    if ($Body) {
        $req.Content = New-Object System.Net.Http.StringContent($Body, [System.Text.Encoding]::UTF8, "application/json")
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $resp = $client.SendAsync($req).Result
    $sw.Stop()

    $status = [int]$resp.StatusCode
    $timeMs = $sw.ElapsedMilliseconds
    $contentStr = $resp.Content.ReadAsStringAsync().Result
    
    $jsonObj = $null
    if ($contentStr) {
        try { $jsonObj = $contentStr | ConvertFrom-Json } catch {}
    }

    $ok = ($status -eq $EsperadoStatus) -and ($timeMs -lt 1500)
    $motivoFalla = ""

    if ($ExtraAssert) {
        try {
            $assertRes = & $ExtraAssert $jsonObj
            if ($assertRes -ne $true) {
                $ok = $false
                $motivoFalla = "ExtraAssert: $assertRes"
            }
        } catch {
            $ok = $false
            $motivoFalla = "ExtraAssert Exception: $_"
        }
    }

    $estado = if ($ok) { "OK" } else { "FALLA" }
    
    $obj = [PSCustomObject]@{
        Modulo   = $Modulo
        Endpoint = "$Metodo $Ruta"
        Caso     = $Caso
        Esperado = "$EsperadoStatus (<1500ms)"
        Obtenido = "$status (${timeMs}ms)"
        Estado   = $estado
        Detalle  = $motivoFalla
    }
    $results.Add($obj)
    return $jsonObj
}

# --- 1. AUTH ---
# 1.1 Login exitoso
$loginJson = Exec-Req -Modulo "1. Auth" -Metodo "POST" -Ruta "/api/v1/auth/login" -Caso "Admin exitoso" -EsperadoStatus 200 -Body '{"nombre_usuario":"admin","pin":"1234"}' -ExtraAssert {
    param($j)
    if ($j.token -and $j.rol -eq "ADMIN") { return $true }
    return "Token no presente o rol no es ADMIN"
}
$adminToken = $loginJson.token

# 1.2 PIN incorrecto (401)
Exec-Req -Modulo "1. Auth" -Metodo "POST" -Ruta "/api/v1/auth/login" -Caso "PIN incorrecto - 401" -EsperadoStatus 401 -Body '{"nombre_usuario":"admin","pin":"9999"}' -ExtraAssert {
    param($j)
    if ($j.codigo -eq "AUTH-PIN-INCORRECTO" -and $j.error) { return $true }
    return "Codigo de error no es AUTH-PIN-INCORRECTO"
}

# 1.3 Operario bloqueado (423)
# Creamos un cajero y lo bloqueamos con 3 intentos fallidos
$randAuth = Get-Random -Minimum 100000 -Maximum 999999
$cajeroBloq = "bloq_$randAuth"
$crearBloqBody = "{`"nombre_completo`":`"Cajero Bloqueo`",`"numero_documento`":`"98$randAuth`",`"nombre_usuario`":`"$cajeroBloq`",`"pin`":`"1111`",`"rol`":`"CAJERO`"}"
Exec-Req -Modulo "1. Auth" -Metodo "POST" -Ruta "/api/v1/operarios" -Caso "Setup: Crear usuario a bloquear" -EsperadoStatus 201 -Token $adminToken -Body $crearBloqBody | Out-Null

for ($i=0; $i -lt 3; $i++) {
    $reqErr = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::POST, "$baseUrl/api/v1/auth/login")
    $reqErr.Content = New-Object System.Net.Http.StringContent("{`"nombre_usuario`":`"$cajeroBloq`",`"pin`":`"0000`"}", [System.Text.Encoding]::UTF8, "application/json")
    $null = $client.SendAsync($reqErr).Result
}

Exec-Req -Modulo "1. Auth" -Metodo "POST" -Ruta "/api/v1/auth/login" -Caso "Operario bloqueado - 423" -EsperadoStatus 423 -Body "{`"nombre_usuario`":`"$cajeroBloq`",`"pin`":`"0000`"}" -ExtraAssert {
    param($j)
    if ($j.codigo -eq "AUTH-OPERARIO-BLOQUEADO") { return $true }
    return "Codigo no coincide: $($j.codigo)"
}

# 1.4 Logout
# Obtenemos token temporal para hacer logout sin invalidar el de admin
$tempLogin = Exec-Req -Modulo "1. Auth" -Metodo "POST" -Ruta "/api/v1/auth/login" -Caso "Setup: Login temporal para logout" -EsperadoStatus 200 -Body '{"nombre_usuario":"admin","pin":"1234"}'
Exec-Req -Modulo "1. Auth" -Metodo "POST" -Ruta "/api/v1/auth/logout" -Caso "Logout exitoso" -EsperadoStatus 204 -Token $tempLogin.token

# --- 2. OPERARIOS ---
# 2.1 GET /operarios
Exec-Req -Modulo "2. Operarios" -Metodo "GET" -Ruta "/api/v1/operarios" -Caso "Listar operarios" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j -is [array] -or $j.Count -ge 1)
}

# 2.2 POST /operarios
$randOpe = Get-Random -Minimum 100000 -Maximum 999999
$cajeroUser = "cajero_$randOpe"
$crearOpe = "{`"nombre_completo`":`"Cajero Postman Test`",`"numero_documento`":`"10$randOpe`",`"telefono`":`"3001234567`",`"nombre_usuario`":`"$cajeroUser`",`"pin`":`"4321`",`"rol`":`"CAJERO`"}"
$opeRes = Exec-Req -Modulo "2. Operarios" -Metodo "POST" -Ruta "/api/v1/operarios" -Caso "Crear operario" -EsperadoStatus 201 -Token $adminToken -Body $crearOpe -ExtraAssert {
    param($j)
    return ($null -ne $j.id_operario -and $j.activo -eq $true)
}
$idOperario = $opeRes.id_operario

# 2.3 GET /operarios/{id}
Exec-Req -Modulo "2. Operarios" -Metodo "GET" -Ruta "/api/v1/operarios/$idOperario" -Caso "Obtener operario por ID" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.id_operario -eq $idOperario)
}

# 2.4 PUT /operarios/{id}
$putOpe = "{`"nombre_completo`":`"Cajero Modificado`",`"telefono`":`"3119876543`",`"nombre_usuario`":null,`"rol`":`"CAJERO`"}"
Exec-Req -Modulo "2. Operarios" -Metodo "PUT" -Ruta "/api/v1/operarios/$idOperario" -Caso "Actualizar operario" -EsperadoStatus 200 -Token $adminToken -Body $putOpe -ExtraAssert {
    param($j)
    return ($j.nombre_completo -eq "Cajero Modificado")
}

# 2.5 PATCH /operarios/{id}/pin
Exec-Req -Modulo "2. Operarios" -Metodo "PATCH" -Ruta "/api/v1/operarios/$idOperario/pin" -Caso "Actualizar PIN" -EsperadoStatus 200 -Token $adminToken -Body '{"pin_nuevo":"5678"}' -ExtraAssert {
    param($j)
    return ($null -ne $j.mensaje)
}

# 2.6 PATCH /operarios/{id}/desbloquear
Exec-Req -Modulo "2. Operarios" -Metodo "PATCH" -Ruta "/api/v1/operarios/$idOperario/desbloquear" -Caso "Desbloquear operario" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.intentos_fallidos -eq 0)
}

# 2.7 PATCH /operarios/{id}/inactivar
Exec-Req -Modulo "2. Operarios" -Metodo "PATCH" -Ruta "/api/v1/operarios/$idOperario/inactivar" -Caso "Inactivar operario" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.activo -eq $false)
}

# --- 3. TURNOS DE CAJA ---
# Limpieza preventiva: cerrar turno si ya había uno abierto
$reqAct = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::GET, "$baseUrl/api/v1/turnos/activo")
$reqAct.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $adminToken)
$respAct = $client.SendAsync($reqAct).Result
if ($respAct.StatusCode -eq 200) {
    $actJson = $respAct.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    if ($actJson.id_turno) {
        $reqClose = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::POST, "$baseUrl/api/v1/turnos/$($actJson.id_turno)/cerrar")
        $reqClose.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $adminToken)
        $reqClose.Content = New-Object System.Net.Http.StringContent('{"monto_fisico_arqueo":50000.00,"observaciones_cierre":"Limpieza"}', [System.Text.Encoding]::UTF8, "application/json")
        $null = $client.SendAsync($reqClose).Result
    }
}

# 3.1 POST /turnos/abrir
$turnoRes = Exec-Req -Modulo "3. Turnos de Caja" -Metodo "POST" -Ruta "/api/v1/turnos/abrir" -Caso "Abrir turno" -EsperadoStatus 201 -Token $adminToken -Body '{"base_efectivo_inicial":50000.00}' -ExtraAssert {
    param($j)
    return ($null -ne $j.id_turno -and $j.estado -eq "ABIERTO")
}
$idTurno = $turnoRes.id_turno

# 3.2 GET /turnos/activo
Exec-Req -Modulo "3. Turnos de Caja" -Metodo "GET" -Ruta "/api/v1/turnos/activo" -Caso "Consultar turno activo" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.id_turno -eq $idTurno -and $j.estado -eq "ABIERTO")
}

# 3.3 GET /turnos/{id}
Exec-Req -Modulo "3. Turnos de Caja" -Metodo "GET" -Ruta "/api/v1/turnos/$idTurno" -Caso "Consultar turno por ID" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.id_turno -eq $idTurno)
}

# 3.4 POST /turnos/{id}/cerrar
Exec-Req -Modulo "3. Turnos de Caja" -Metodo "POST" -Ruta "/api/v1/turnos/$idTurno/cerrar" -Caso "Cerrar turno con arqueo" -EsperadoStatus 200 -Token $adminToken -Body '{"monto_fisico_arqueo":50000.00,"observaciones_cierre":"Cierre normal"}' -ExtraAssert {
    param($j)
    return ($j.estado -eq "CERRADO")
}

# --- 4. CATEGORÍAS ---
# 4.1 POST /categorias
$randCat = Get-Random -Minimum 100000 -Maximum 999999
$catRes = Exec-Req -Modulo "4. Categorías" -Metodo "POST" -Ruta "/api/v1/categorias" -Caso "Crear categoría" -EsperadoStatus 201 -Token $adminToken -Body "{`"nombre`":`"Bebidas $randCat`",`"descripcion`":`"Refrescos`"}" -ExtraAssert {
    param($j)
    return ($null -ne $j.id_categoria -and $j.activo -eq $true)
}
$idCategoria = $catRes.id_categoria

# 4.2 GET /categorias
Exec-Req -Modulo "4. Categorías" -Metodo "GET" -Ruta "/api/v1/categorias" -Caso "Listar categorías" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j -is [array] -or $j.Count -ge 1)
}

# 4.3 PUT /categorias/{id}
Exec-Req -Modulo "4. Categorías" -Metodo "PUT" -Ruta "/api/v1/categorias/$idCategoria" -Caso "Actualizar categoría" -EsperadoStatus 200 -Token $adminToken -Body "{`"nombre`":`"Bebidas Modificadas $randCat`",`"descripcion`":`"Actualizada`"}" -ExtraAssert {
    param($j)
    return ($j.nombre -like "*Bebidas Modificadas*")
}

# 4.4 PATCH /categorias/{id}/inactivar (usando categoría secundaria)
$catAux = Exec-Req -Modulo "4. Categorías" -Metodo "POST" -Ruta "/api/v1/categorias" -Caso "Setup: Categoría para inactivar" -EsperadoStatus 201 -Token $adminToken -Body "{`"nombre`":`"Cat Inact $randCat`",`"descripcion`":`"Aux`"}"
Exec-Req -Modulo "4. Categorías" -Metodo "PATCH" -Ruta "/api/v1/categorias/$($catAux.id_categoria)/inactivar" -Caso "Inactivar categoría" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.activo -eq $false)
}

# --- 5. PRODUCTOS ---
# 5.1 POST /productos
$randProd = Get-Random -Minimum 100000 -Maximum 999999
$barcode = "770" + $randProd.ToString().PadLeft(10, '0')
$crearProd = "{`"codigo_barras`":`"$barcode`",`"nombre`":`"Gaseosa $randProd`",`"descripcion`":`"500ml`",`"id_categoria`":$idCategoria,`"precio_venta`":4500.00,`"costo_inicial`":2500.00,`"stock_inicial`":50,`"aplica_iva`":true,`"porcentaje_iva`":19.00,`"stock_minimo`":10,`"unidad_medida`":`"UNIDAD`"}"
$prodRes = Exec-Req -Modulo "5. Productos" -Metodo "POST" -Ruta "/api/v1/productos" -Caso "Crear producto" -EsperadoStatus 201 -Token $adminToken -Body $crearProd -ExtraAssert {
    param($j)
    return ($null -ne $j.id_producto -and $j.codigo_barras -eq $barcode)
}
$idProducto = $prodRes.id_producto

# 5.2 GET /productos
Exec-Req -Modulo "5. Productos" -Metodo "GET" -Ruta "/api/v1/productos" -Caso "Listar productos" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j -is [array] -or $j.Count -ge 1)
}

# 5.3 GET /productos/{id}
Exec-Req -Modulo "5. Productos" -Metodo "GET" -Ruta "/api/v1/productos/$idProducto" -Caso "Obtener producto por ID" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.id_producto -eq $idProducto)
}

# 5.4 GET /productos/barcode/{codigo}
Exec-Req -Modulo "5. Productos" -Metodo "GET" -Ruta "/api/v1/productos/barcode/$barcode" -Caso "Buscar por código de barras" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.codigo_barras -eq $barcode)
}

# 5.5 PUT /productos/{id}
$putProd = "{`"codigo_barras`":`"$barcode`",`"nombre`":`"Gaseosa Modificada $randProd`",`"descripcion`":`"600ml`",`"id_categoria`":$idCategoria,`"precio_venta`":4800.00,`"aplica_iva`":true,`"porcentaje_iva`":19.00,`"stock_minimo`":8,`"unidad_medida`":`"UNIDAD`"}"
Exec-Req -Modulo "5. Productos" -Metodo "PUT" -Ruta "/api/v1/productos/$idProducto" -Caso "Actualizar producto" -EsperadoStatus 200 -Token $adminToken -Body $putProd -ExtraAssert {
    param($j)
    return ($j.precio_venta -eq 4800.00)
}

# 5.6 PATCH /productos/{id}/inactivar (usando producto secundario)
$prodAux = Exec-Req -Modulo "5. Productos" -Metodo "POST" -Ruta "/api/v1/productos" -Caso "Setup: Producto para inactivar" -EsperadoStatus 201 -Token $adminToken -Body "{`"codigo_barras`":`"99$randProd`",`"nombre`":`"Prod Inact $randProd`",`"descripcion`":`"aux`",`"id_categoria`":$idCategoria,`"precio_venta`":2000.00,`"costo_inicial`":1000.00,`"stock_inicial`":5,`"aplica_iva`":false,`"stock_minimo`":1,`"unidad_medida`":`"UNIDAD`"}"
Exec-Req -Modulo "5. Productos" -Metodo "PATCH" -Ruta "/api/v1/productos/$($prodAux.id_producto)/inactivar" -Caso "Inactivar producto" -EsperadoStatus 200 -Token $adminToken -ExtraAssert {
    param($j)
    return ($j.activo -eq $false)
}

# --- 6. INVENTARIO ---
# 6.1 POST /inventario/compra
$compraBody = "{`"id_producto`":$idProducto,`"cantidad`":20,`"costo_unitario`":2800.00,`"id_referencia`":`"FAC-PROV-9988`",`"motivo_ajuste`":null}"
Exec-Req -Modulo "6. Inventario" -Metodo "POST" -Ruta "/api/v1/inventario/compra" -Caso "Registrar compra y recalcular CPP" -EsperadoStatus 201 -Token $adminToken -Body $compraBody -ExtraAssert {
    param($j)
    return ($j.tipo_movimiento -eq "COMPRA" -and $j.saldo_resultante -eq 70)
}

# Mostrar tabla filtrando los pasos de setup
$filtered = $results | Where-Object { $_.Caso -notlike "Setup:*" }
$filtered | Select-Object Modulo, Endpoint, Caso, Esperado, Obtenido, Estado | Format-Table -AutoSize
$filtered | ConvertTo-Json -Depth 3 | Out-File -FilePath "postman/test_results.json" -Encoding utf8
