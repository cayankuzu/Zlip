# Play Store'a yayınlama rehberi

## 1. Kalıcı kararlar (ilk yüklemeden önce)

- **Paket adı:** `com.zlip.app`. İlk yüklemeden sonra değiştirilemez. Değiştirmek istersen `app/build.gradle.kts` içindeki `applicationId` ve `namespace` değerlerini şimdi değiştir.
- **Uygulama adı:** `Zlip` (`app/src/main/res/values/strings.xml` → `app_name`).
- **Sürüm:** her yüklemede `app/build.gradle.kts` içindeki `versionCode` değerini 1 artır, `versionName` değerini güncelle.

## 2. İmza anahtarı (hazır)

Zlip'in imza anahtarı proje kökünde: `zlip-release.jks` (takma ad `zlip`) ve şifresiyle birlikte `keystore.properties`. İkisi de `.gitignore` içinde; asla paylaşma, **mutlaka yedekle**. GitHub'daki APK bu anahtarla imzalı; anahtar kaybolursa APK kullanıcılarına güncelleme verilemez.

Sertifika SHA-256: `C2:D5:A7:7D:0B:08:C6:EF:AF:81:B9:52:13:7F:05:32:C3:5F:9D:8E:B2:13:31:52:C7:C0:95:71:03:DE:E4:B2`

Play Console'da uygulamayı oluştururken **Play App Signing**'de "Use a different key → Export and upload a key from Java keystore" seç ve bu anahtarı (Google'ın PEPK aracıyla) yükle. Böylece Play'deki sürüm de GitHub APK'sı da aynı imzayı taşır; kullanıcılar birinden diğerine sorunsuz güncelleyebilir. Google'ın kendi ürettiği anahtarı seçersen APK ile kurulmuş Zlip, Play sürümüyle güncellenemez (önce kaldırmak gerekir).

## 3. Paketi derle

```bash
./gradlew :app:bundleRelease
```

Çıktı: `app/build/outputs/bundle/release/app-release.aab` (yaklaşık 5 MB, R8 ile küçültülmüş ve imzalanmış).

## 4. Play Console ayarları

| Bölüm | Ne seçilmeli |
| --- | --- |
| Uygulama erişimi | Tüm işlevler kısıtlamasız |
| Reklamlar | Reklam yok |
| İçerik derecelendirmesi | Anketin tümüne "Hayır" → Herkes / 3+ |
| Hedef kitle | 13+ (çocuklara yönelik değil; Aileler politikası devreye girmesin) |
| Veri güvenliği | **Veri toplanmıyor, paylaşılmıyor.** Uygulamada internet izni yok. Fotoğraf ve videolar sistem seçicisiyle (ya da kullanıcının paylaşmasıyla) alınır, yalnızca cihazda işlenir. Ekran yansıtmada görüntü yalnızca kapak ekranında gösterilir; kaydedilmez, saklanmaz, gönderilmez. |
| İzinler | `POST_NOTIFICATIONS` (isteğe bağlı bildirim kontrolleri). İsteğe bağlı "Kapakta hep göster" için `SYSTEM_ALERT_WINDOW` (kullanıcı sistem ayarından verir; beyan formu gerekmez), `FOREGROUND_SERVICE_SPECIAL_USE` ve `RECEIVE_BOOT_COMPLETED`. Ekran yansıtma için `FOREGROUND_SERVICE_MEDIA_PROJECTION` (Android her yansıtmada kullanıcıya ayrıca sorar). Konum, depolama izni yok. |
| Ön plan hizmeti beyanı: Special use | **App content → Foreground service permissions → Special use.** Açıklama: "Kullanıcının açtığı kapak ekran koruyucusu: telefon katlıyken kapak uyandığı anda kullanıcının seçtiği içeriği kapak kilit ekranının önünde gösterir. Hizmet yalnızca ekran açılma/kapanma olaylarını dinler; ertelenirse özellik çalışmaz. Kullanıcı bildirimden kapatabilir." Demo videosu: Ana sayfada "Kapakta hep göster"i açma, "Üstte göster" iznini verme, telefonu katlayıp kapağın uyanınca Zlip'i göstermesi (30-60 sn, liste dışı YouTube bağlantısı). |
| Ön plan hizmeti beyanı: Media projection | **App content → Foreground service permissions → Media projection.** Açıklama: "Ekran yansıtma: kullanıcı Zlip'te 'Ekranı yansıt'ı seçip Android'in ekran paylaşımı iznini verdiğinde, açık telefonun iç ekranı telefonun kendi kapak (arka) ekranında gösterilir; örneğin karşıdaki kişi kamera vizörünü görsün diye. Görüntü yalnızca o kapak ekranında gösterilir; kaydedilmez, saklanmaz, ağ üzerinden gönderilmez (uygulamanın internet izni yoktur). Hizmet yalnızca yansıtma sürerken çalışır; kullanıcı bildirimden, Zlip'ten ya da Mini kapak penceresinden durdurur; telefon kilitlenince ya da katlanınca kendiliğinden biter." Demo videosu: "Ekranı yansıt"a dokunma, sistem izin penceresinde "Tüm ekran"ı seçme, kamera uygulamasını açıp vizörün kapakta görünmesi, bildirimden durdurma (30-60 sn, liste dışı YouTube bağlantısı). |
| Gizlilik politikası | https://zlip-web.vercel.app/gizlilik (`PRIVACY_POLICY.md` ile aynı metin; kaynak: github.com/cayankuzu/Zlip_web). İletişim: memodee333@gmail.com |
| Uygulama kategorisi | Kişiselleştirme |

### Sadece Z Flip cihazları hedeflemek

1. Manifest'teki `android.hardware.sensor.hinge_angle` özelliği uygulamayı zaten yalnızca katlanabilir telefonlara açar.
2. **Play Console → Test ve yayınla → Cihaz kataloğu** bölümünde Samsung dışındaki üreticileri ve Galaxy Z Fold modellerini hariç tut. Z Flip modelleri (SM-F7xx) açık kalsın.

### Yeni kişisel geliştirici hesabı kuralı

Kişisel hesabın Kasım 2023'ten sonra açıldıysa, üretime çıkmadan önce **en az 12 test kullanıcısıyla 14 gün kesintisiz kapalı test** yapman gerekir. Önce "Kapalı test" kanalına yükle.

## 5. Mağaza sayfası metinleri

**Uygulama adı (en fazla 30 karakter):** `Zlip – Arka Ekran Sanatı`

**Kısa açıklama (en fazla 80 karakter):**
`Flip kapak ekranında fotoğraf, video, yazı, ekran yansıtma: açıkken, katlıyken.`

**Uzun açıklama:**

```
Zlip, Galaxy Z Flip'inin kapak ekranını telefon açıkken de kullanmanı sağlar.
Sen iç ekranı kullanırken arkadaki ekranda kendi fotoğrafların, videoların,
yazıların ve çizimlerin görünür. İstersen iç ekranın kendisi.

ARKADA NE İSTERSEN
• Galeriden: fotoğraf, GIF ve videoları tek seferde birkaç tane seç; tek dokunuşla grup yap.
• Grup: seçtiklerin sırayla, tam ekran oynar. Fotoğraf süresi, karışık sıra, tekrar ve
  yumuşak geçiş senin; videolar baştan sona oynar.
• Ekran yansıtma: iç ekranı kapakta göster; karşındaki kişi kamera vizörünü, bir
  fotoğrafı ya da haritayı görsün. Görüntü kaydedilmez, telefondan çıkmaz.
• Piksel çizim editörü: kalem, silgi, doldurma, damlalık, ayna modu, geri al/yinele,
  16×16'dan 48×48'e kadar tuval, yakınlaştırma.
• Fotoğraf ve GIF: sığdır/doldur, döngü, piksel art için keskin ölçekleme.
• Kadraj: kapakta da iki parmakla yakınlaştır, yakınken kaydırarak gez.
• Video: ne kadar uzunsa baştan sona oynar; kapak açılınca sesli ya da sessiz başlar.
  Kapak ekranı için otomatik optimize edilir.
• Kayan yazı: LED tabela gibi; renk, boyut ve hız senin elinde. Hazır mesajlar tek dokunuşla.
• Kolaj: 2-4 hücre, her hücre kendi içeriklerini döngüde oynatır.
• Animasyonlu piksel çizim: kare kare çiz, kapakta canlansın.
• Geri sayım: tatile, doğum gününe kalan gün; her hafta ya da her yıl kendiliğinden yenilenir.
• QR kartı: Wi-Fi, bağlantı ya da kartvizit; kilidi açmadan gösterilir.
• Kapak oyuncakları: zar, yazı tura, sihirli küre, şişe çevirme, karar çarkı, sayaç.
• LED matris görünümü: kapaktaki her şey nokta nokta parlasın.
• Oynatma listesi: istediğin öğeleri seç, sürükleyerek sırala; otomatik ilerleme fotoğrafları
  süre dolunca, videoları ve kayan yazıları bitince değiştirir. Karıştır ve döngü.
• Zlip'e paylaş: galeriden ya da başka bir uygulamadan paylaş; bağlantılar QR kartı,
  yazılar kayan yazı olur.
• Canlı önizleme: düzenlediğin her şey kaydetmeden önce de arkada anında görünür.

BAŞKA UYGULAMADAYKEN DE
• Mini kapak: başka uygulamaya geçince Zlip küçük bir pencereye dönüşür ve kapak açık
  kalır. Duraklat, sığdır/doldur, geç, kapağı aç ya da kapat; hepsi o pencereden.
• Uygulama simgesine basılı tut: "Kapağı aç", "Ekranı yansıt", "Yeni yazı".

AKILLI VE PİLE DOST
• Kapak parlaklığı ayarı: içerik karartılır, OLED'de koyu piksel daha az pil demek.
• Güç modları: Tasarruf (20 Hz), Dengeli (30 Hz), Akıcı (60 Hz).
• İç ekran bekleme modu: boşta kalınca iç ekran en düşük parlaklıkta kararır.
• Boşta kalınca kapat: kimse dokunmazsa kapak kendiliğinden kapanır.
• Sen açmadıkça arka planda çalışmaz; internet yok.

KATLIYKEN DE
• Zlip kapak widget'ı: oynatma listen kapak ekranında, Zlip kapalıyken bile döner.
• Ekran koruyucu: widget'a dokun, içeriklerin kilit ekranının önünde oynasın. Kilidi
  açmadan yana kaydırarak gez, yukarı kaydırarak menüyü aç, aşağı kaydırınca kilit
  ekranına dön.
• Hep açık ve şarjdayken komodin saati modu; telefonu açınca kendiliğinden çekilir.
• İsteğe bağlı "Kapakta hep göster": kapak her uyandığında Zlip kendiliğinden gelir.

KONTROL SENDE
• Yanlış dokunmaya karşı kilit: kapak ekranı sen çift dokunana kadar dokunmaları
  yok sayar, 3 saniye sonra kendiliğinden yeniden kilitlenir.
• Bildirim panelinden duraklat, sonrakine geç ya da kapağı kapat (katlıyken
  ekran koruyucuda da).
• Hızlı ayarlar kutucuğuyla tek dokunuşla aç/kapat.

GİZLİLİK
• Reklam yok, internet izni yok, veri toplama yok. Her şey telefonunda kalır.

Notlar:
• Telefon açıkken Android, kapak ekranını yalnızca Zlip ekrandayken kullandırır:
  Zlip açık ya da Mini kapak olarak. Telefon kilitlenince kapak kapanır; Zlip'e
  döndüğünde otomatik olarak yeniden açılır. Katlıyken kapak widget'ı Zlip'ten
  bağımsız çalışır.
• Ekran yansıtmada Android her seferinde izin ister; korumalı uygulamalar
  (bankacılık, Netflix) siyah görünür.
• One UI 6 veya üstü bir Galaxy Z Flip gerekir. Mini kapak ve ekran yansıtma
  One UI 7 (Android 15) veya üstü gerektirir.
```

**English short description:** `Photos, videos, text and screen mirroring on your Flip's cover, open or folded.`

## 6. Görseller

Hazır olanlar `store/` klasöründe:

- `store/play_icon_512.png`: Uygulama simgesi (512×512).
- `store/feature_graphic_1024x500.png`: Öne çıkan grafik (1024×500).

Eksik olan, en az 2 telefon ekran görüntüsü. Telefon bağlıyken şu komutla alınabilir:

```bash
adb shell screencap -p /sdcard/ekran1.png
adb pull /sdcard/ekran1.png
```

Kapak ekranının görüntüsü için (kapak açıkken):

```bash
adb shell screencap -d 4633128672291735938 -p /sdcard/kapak.png
adb pull /sdcard/kapak.png
```

> Not: `adb exec-out screencap -p > dosya.png` kullanmayın. Çok ekranlı cihazlarda adb'nin "[Warning] Multiple displays..." uyarısı PNG'nin başına yazılıyor ve dosya bozuluyor. PowerShell'de `>` yönlendirmesi de ikili veriyi bozabiliyor.
