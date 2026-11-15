# DonutMod

Fabric 1.21.11 (Mojang mappings, Java 21) — DonutSMP tarzı para / shard / istatistik modu.

Klasör yapısı: repo kökünde `.github/` (derleme workflow'u) ve `donutmod/` (proje).

## Komutlar
- `/stats [oyuncu]`
- `/pay <oyuncu> <miktar>`
- `/gift <oyuncu>` (elindeki eşya) | `/gift <oyuncu> <eşya> [adet]`
- Sadece admin: `/admin <para|shard|sure|kill|death> <set|add|remove> <oyuncu> <miktar>` (süre dakika cinsinden), `/admin reload`

## Dosyalar (sunucuda)
- `config/donutmod/config.json`
- `config/donutmod/players.json`

Sunucuda `online-mode=true` olmalı.
