# DonutMod

Fabric 1.21.11 (Mojang mappings, Java 21) — DonutSMP tarzı para / shard / istatistik modu.

Klasör yapısı: repo kökünde `.github/` (derleme workflow'u) ve `donutmod/` (proje).

## Komutlar
- `/stats [oyuncu]`
- `/pay <oyuncu> <miktar>`
- `/gift <oyuncu>` (elindeki eşya) | `/gift <oyuncu> <eşya> [adet]`
- Shop / sell menüleri (DonutShop, ShopMenu, SellMenu)
- Cracked (TLauncher) oyuncular için: `/register <şifre> <şifre>` ve `/login <şifre>`
- Sadece admin: `/admin <para|shard|sure|kill|death> <set|add|remove> <oyuncu> <miktar>` (süre dakika cinsinden), `/admin reload`

## Giriş koruması (AuthManager)
- Premium (UUID sürüm 4) oyuncular otomatik doğrulanmış sayılır.
- Cracked oyuncular `/register` + `/login` yapana kadar para/eşya komutlarını ve admin komutlarını kullanamaz.
- Şifreler PBKDF2-HMAC-SHA256 ile salt'lanıp saklanır; 5 yanlış denemede oyuncu atılır.

## Dosyalar (sunucuda)
- `config/donutmod/config.json`
- `config/donutmod/players.json`
- `config/donutmod/auth.json`
