# Compilation — Entorno de compilación nativo (aarch64)

Entorno para compilar las herramientas C/C++ directamente desde el chroot
Android sin necesidad de cross-compilación desde PC.

## Prerequisitos

- Chroot Arch Linux ARM en `/data/local/aarchdroid/root/`
- gcc, g++, make, cmake instalados en el chroot
- Permisos root (su)

## Instalación rápida

```sh
# 1. Entrar al chroot
su -c "chroot /data/local/aarchdroid /bin/bash"

# 2. Instalar deps del sistema (si no están)
pacman -S --needed gcc make cmake pkg-config sqlite

# 3. Ejecutar setup (copia deps pre-compiladas, genera scripts)
cd /data/local/aarchdroid/root
./Compilation/setup.sh

# 4. Activar entorno
source Compilation/env.sh

# 5. Compilar todo
build-all
```

## Herramienta por herramienta

### WifiOneshot (la más simple)

Solo necesita SQLite3. Buena para verificar que el entorno funciona.

```sh
build-wifioneshot
# Output: build/wifioneshot/oneshot_aarch64
```

### Iris

Compila `libiris_static.a` (shared lib) + el binario iris.

```sh
build-iris
# Output: build/iris/iris
```

### Mandela

Requiere SQLite3 + libnl-3 + libnl-genl-3.

```sh
build-mandela
# Output: build/mandela/mandela_stripped
```

### meOrion

Compilación estática completa con OpenSSL + libssh2.

```sh
build-meorion
# Output: build/meorion/orion
```

### Cornea

Requiere Tesseract + ncnn. La build más pesada.

```sh
build-cornea
# Output: build/cornea/cornea_stripped
```

### Lexis

Fork de Cornea, requiere Tesseract (sin ncnn).

```sh
build-lexis
# Output: build/lexis/lexis_stripped
```

## Deploy al chroot

```sh
# Binarios compilados → ubicación final
cp build/wifioneshot/oneshot_aarch64 /data/local/aarchdroid/root/OneShot-C/oneshot
cp build/iris/iris /data/local/aarchdroid/root/iris
cp build/mandela/mandela_stripped /data/local/aarchdroid/root/mandela
cp build/meorion/orion /data/local/aarchdroid/root/orion
cp build/cornea/cornea_stripped /data/local/aarchdroid/root/cornea/cornea
cp build/lexis/lexis_stripped /data/local/aarchdroid/root/lexis/lexis
```

## Estructura

```
Compilation/
├── env.sh              # source para activar variables de entorno
├── setup.sh            # setup inicial (instalar deps, generar scripts)
├── README.md           # este archivo
├── deps/               # dependencias estáticas
│   ├── lib/            # .a files (tesseract, ncnn, openssl, etc.)
│   ├── include/        # headers
│   └── bin/            # binarios helpers
├── scripts/            # scripts de build por tool
│   ├── build-iris.sh
│   ├── build-mandela.sh
│   ├── build-wifioneshot.sh
│   ├── build-meorion.sh
│   ├── build-cornea.sh
│   ├── build-lexis.sh
│   └── build-all.sh
├── build/              # output por tool
│   ├── iris/
│   ├── mandela/
│   ├── wifioneshot/
│   ├── meorion/
│   ├── cornea/
│   └── lexis/
├── tessdata/           # modelos de Tesseract
└── logs/               # logs de build
```

## Solución de problemas

### "sqlite3.h not found"
```sh
# Compilar sqlite desde source
gcc -O3 -fPIC -c /tmp/sqlite3.c -o /tmp/sqlite3.o
ar rcs Compilation/deps/lib/libsqlite3.a /tmp/sqlite3.o
```

### "libnl-3.a not found"
```sh
# Instalar desde pacman
pacman -S libnl
# O copiar desde /tmp/libnl-aarch64/
cp -r /tmp/libnl-aarch64/include/* Compilation/deps/include/
cp /tmp/libnl-aarch64/lib/*.a Compilation/deps/lib/
```

### "libssh2.h not found"
```sh
# Copiar desde meOrion deps
cp -r Herramientas/meOrion/deps/libssh2-aarch64/include/* Compilation/deps/include/
cp Herramientas/meOrion/deps/libssh2-aarch64/lib/*.a Compilation/deps/lib/
```

### Build lento en el dispositivo
El chroot puede tener limitaciones de CPU/RAM. Compila una tool a la vez:

```sh
build-wifioneshot   # primero la más ligera
build-iris          # shared lib
build-mandela       # requiere libnl
build-meorion       # estático completo
build-cornea        # pesado (tesseract + ncnn)
build-lexis         # tesseract sin ncnn
```
