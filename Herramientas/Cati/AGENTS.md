# Cati — Image Viewer for AArchDroid Canvas

Visor de imágenes que carga archivos PNG/JPEG/BMP/GIF y los muestra
en el overlay flotante de Canvas (`canvas-display`, AF_UNIX).

## Dependencias

- `libiris_static.a` (canvas overlay + drawing)
- `stb_image.h` (single-header, descargado automáticamente)

## Build

```sh
cd Cati && aarch64-linux-gnu-g++ -O3 -static-libstdc++ \
  -I../app/src/main/cpp/lib/include \
  -Iinclude \
  src/main.cpp \
  -L../app/src/main/cpp/lib/build_aarch64 -liris_static \
  -o build/cati
```

O usar CMake:
```sh
mkdir -p build && cd build
cmake .. -DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc \
         -DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++
make -j$(nproc)
```

## Deploy

```sh
adb push build/cati_stripped /data/local/tmp/cati
su -c "dd if=/data/local/tmp/cati of=/data/local/aarchdroid/root/Cati/cati bs=1M"
adb push imagen.png /data/local/tmp/
su -c "cp /data/local/tmp/imagen.png /data/local/aarchdroid/root/Cati/"
```

## Ejecutar

```sh
su -c 'aarchrun.sh /root/Cati/cati /root/Cati/imagen.png'
```

## Uso

```
cati <imagen> [opciones]

Opciones:
  -d, --display SOCKET   Socket de display (default: canvas-display)
  -s, --size WxH         Forzar tamaño (default: auto)
  -f, --fit              Ajustar al overlay (default: mantener aspecto)
  -v, --verbose          Log detallado
```

Teclas táctiles: tap para salir.
