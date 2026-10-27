# Zlip

Galaxy Z Flip telefonların kapak (arka) ekranını hem telefon **açıkken** hem **katlıyken** kullanan Android uygulaması: piksel çizim (animasyonlu), fotoğraf, GIF, video (sesli/sessiz), kayan yazı, döngülü kolaj, tam ekran sırayla oynayan **gruplar**, tekrarlayan geri sayım, QR kartı ve kapak oyuncakları. Açık telefonda **ekran yansıtma** ve diğer uygulamalarda kapağı açık tutan **Mini kapak** da var.

## Nasıl çalışıyor

- Kapak ekranı, Jetpack WindowManager'ın **rear-display presentation (dual-screen)** moduyla açılır (`androidx.window` 1.5.1, `WindowAreaController.presentContentOnWindowArea`). Root, ADB ya da Shizuku gerekmez.
- Android bu modu yalnızca Zlip ekrandayken açık tutar. Telefon kilitlenince ya da katlanınca kapak ekranı da kapanır; Zlip'e dönünce kendiliğinden yeniden başlar.
- **Mini kapak** (Android 15 ve üstü; isteğe bağlı, varsayılan açık): kapak açıkken başka uygulamaya geçince Zlip küçük bir resim içinde resim penceresine dönüşür. Pencere ekranda kaldıkça kapak açık kalır; düğmeleriyle duraklat, sonraki ve kapağı aç/kapat. Pencereyi kapatmak kapağı da kapatır. Kaydedilmemiş değişiklik varken Zlip küçülmez; çıkınca kapak kapanır.
- Kapak ekranında dokunma varsayılan olarak **kilitli**: tek dokunuş hiçbir şey yapmaz. **Çift dokunuş** kilidi açar, **3 saniye** dokunulmazsa kilit geri gelir. Ekran kapanmaz.
- Kilit açıkken: kontroller görünür (önceki / oynat-duraklat / sonraki / ses); sağa/sola kaydırınca sıradaki öğeye ya da grubun sıradaki adımına geçer.
- **Ekran yansıtma** (Android 15 ve üstü): Ana sayfadaki "İçeriğim | Ekranı yansıt" seçimiyle iç ekran kapakta görünür (MediaProjection, `mediaProjection` türünde ön plan hizmeti). Android her seferinde izin ister ve "Tüm ekran" seçilmelidir. Görüntü yalnızca kapakta gösterilir; kaydedilmez, saklanmaz, hiçbir yere gönderilmez. "Tamamı / Doldur" görünümü seçilebilir; korumalı uygulamalar (bankacılık, Netflix) siyah görünür.
- Boşta kalınca kapat (5 / 10 / 30 dk / asla): Zlip tam ekranken iki ekrana da dokunulmazsa kapak kapanır, iç ekran karanlık kalır ve normal zaman aşımıyla kilitlenir. 15 saniye önce uyarı çıkar; bir sonraki dokunuşa kadar kendiliğinden yeniden açılmaz. Bölünmüş ekranda ya da TalkBack açıkken yalnızca ekranı açık tutmayı bırakır.

## İçerik

- **Galeriden**: tek seçicide fotoğraf, GIF ve video; birkaçı birden seçilebilir. Tek dosya düzenleyicide açılır (hiçbir şey değiştirmeden geri çıkınca "Kaldırılsın mı?" diye sorar); birkaç dosya birlikte gelir ve tek dokunuşla **grup** yapılabilir.
- **Grup**: seçilen içerikler sırayla, tam ekran oynar. Videolar sonuna kadar ve kendi sesiyle, fotoğraflar seçilen süre kadar kalır. Sıra sürükleyerek, oklarla ya da TalkBack eylemleriyle değişir.
- **Kolaj**: 2-4 hücre, her hücre kendi içeriklerini döngüde oynatır.
- **Geri sayım**: tek seferlik, her hafta ya da her yıl tekrarlayan (doğum günü, hafta sonu, yılbaşı); ileri sayım da var.
- **Oynatma listesi**: öğelere basılı tutup seçerek oluşturulur. "Liste" filtresindeki **Sırala** sayfasında sürükleyerek, oklarla ya da "En başa / En sona" ile sıralanır.
- **Zlip'e paylaş**: başka bir uygulamadan paylaşılan fotoğraf ve videolar galeriden seçilmiş gibi gelir; tek başına bir web bağlantısı yeni bir QR kartı, diğer yazılar yeni bir yazı olur.
- **Kısayollar** (uygulama simgesine basılı tut): "Kapağı aç", "Ekranı yansıt", "Yeni yazı".

## Katlıyken

- **Kapak widget'ı** (Samsung Flex Window, `display="sub_screen"`): oynatma listesinin önceden çizilmiş karelerini sistem kendisi döndürür, Zlip kapalıyken de çalışır. Kareler `CoverImageProvider` üzerinden (salt okunur, tahmin edilemez adlar) verilir.
- **Ekran koruyucu** (`CoverActivity`, `showWhenLocked`): widget'a dokununca kapak ekranında (display 1) açılır ve **kilit ekranının önünde** oynar. Kapak uyuduğunda kapanmaz; kapak her uyandığında kilit ekranının önüne geri gelir.
  - Yana kaydır: önceki/sonraki. Yukarı kaydır: kapak menüsü. Aşağı kaydır: kilit ekranına dön. Ses açma tuşu: sesli videonun sesini açar.
  - Açık kalma süresi (kapak varsayılanı / 1-30 dk / hep açık). Hep açıkta 1 dk sonra "dinlenme": kısık, durağan kare + saat (AOD gibi). Şarjdayken hep açık (komodin saati).
  - Telefon açılınca (iç ekran yanınca) kendini kapatır; iç ekran asla etkilenmez.
  - **"Kapakta hep göster"** (Ana sayfada ve Ayarlar'da): "Üstte göster" izni + `specialUse` ön plan hizmeti (yalnızca ekran açılıp kapanmalarını dinler, sensör/wakelock yok). Kapak kararınca ekran koruyucuyu yeniden hazırlar. Bu ayar kapalıyken katlanmış kapakta yalnızca widget görünür. Hizmetin bildirimi kaydırılarak kaldırılabilir; "Hep göstermeyi kapat" düğmesi özelliği kapatır.

## Pil tasarrufu

| Önlem | Etkisi |
| --- | --- |
| İnternet ve wakelock yok; arka plan hizmeti yalnızca kullanıcı "Kapakta hep göster"i açarsa (o da sadece ekran olaylarını dinler) | Uygulama kapalıyken sıfıra yakın tüketim |
| Ekran koruyucu dinlenme modu: durağan kare, ek karartma | Video/GIF çözücü çalışmaz |
| Kolajda aynı anda tek video çözülür, diğer video hücreleri önizleme karesi gösterir | Çoklu çözücü yükü yok |
| Sessiz videolarda ses izi hiç seçilmez (kod çözülmez, ses odağı istenmez) | Müziğin kesilmez, ses donanımı uyur |
| LED görünümü GPU gölgelendiricisi (AGSL) ile tek geçişte | Ek kare maliyeti neredeyse sıfır |
| Kapak parlaklığı: içerik yazılımla karartılır (One UI kapak penceresinin parlaklık değerini yok sayıyor) | OLED'de koyu piksel = daha az güç |
| Güç modları 20 / 30 / 60 kare/sn | Kayan yazı ve kapak yenilemesi sınırlanır |
| Videolar içe aktarılırken 720p / 30 fps H.264 + 128 kbps AAC'ye dönüştürülür | Hafif donanım kod çözme |
| Fotoğraflar 1440 px WebP'ye küçültülür | Her gösterimde daha az kod çözme |
| Kitaplık küçük resimleri durağan; videolar için önizleme karesi | Listede hiç kod çözücü çalışmaz |
| Kapak açıkken iç ekrandaki önizleme durur; Mini kapak penceresi durağan bir karedir | Aynı içerik iki kez çizilmez |
| Yansıtılan görüntü iç ekran boyutunda değil, kapakta gösterilen boyutta üretilir | Daha küçük görüntü, daha az GPU işi |
| İç ekran bekleme modu, her ekranda (düzenleyiciler ve açık sayfalar dahil) | Boşta kalınca iç ekran OLED siyahı + en düşük parlaklık |
| Saf siyah arayüz | OLED piksellerini kapatır |
| Ayarlar diske gecikmeli yazılır | Kaydırıcı sürüklerken sürekli yazma olmaz |

## Kontroller

- Bildirim panelinde Duraklat/Oynat, Sonraki (sesli videoda Ses aç/kapat), Kapağı kapat: telefon açıkken kapak ekranı için de, katlıyken ekran koruyucu için de (sıradan bildirim). Grup oynarken başlıkta "Grup · 2/4" görünür.
- Mini kapak penceresinin düğmeleri: duraklat/oynat, sonraki, kapağı aç/kapat; yansıtırken yansıtmayı durdur.
- Hızlı ayarlar kutucuğu: kapak ekranını tek dokunuşla aç/kapat. Uzun basınca ayarlar açılır.
- Başlatıcı kısayolları `ShortcutActivity` üzerinden gelir; açık bir Zlip (ve kaydedilmemiş düzenleme) kapanmaz.

## Geliştirme

Gerekenler: JDK 17 (`JAVA_HOME`), Android SDK (`ANDROID_HOME`), USB hata ayıklaması açık bir Z Flip.

```bash
./gradlew :app:runDebug
```

Debug sürümünü kurar ve açar (`com.zlip.app.debug`, Play Store sürümüyle yan yana durabilir). Debug sürümünün kısayolları `app/src/debug/res/xml/shortcuts.xml` içindedir (paket adı farklı); kısayol değişince iki dosyayı birlikte güncelle.

Canlı döngü: her kayıtta yeniden derleyip kurar ve açar:

```bash
powershell -ExecutionPolicy Bypass -File dev.ps1
```

Birim testleri:

```bash
./gradlew :app:testDebugUnitTest
```

## Proje yapısı

| Yol | Ne yapar |
| --- | --- |
| `MainActivity.kt` | İç ekran: kapak oturumu, Mini kapak (resim içinde resim), bekleme modu, paylaşım ve kısayol istekleri |
| `ShortcutActivity.kt` | Başlatıcı kısayollarını açık Zlip'e iletir |
| `cover/CoverDisplayController.kt` | Kapak ekranı oturumunu açar/kapatır, parlaklık ve yenileme hızını uygular |
| `cover/CoverScreen.kt` | Kapak ekranında görünen arayüz, dokunma kilidi, kontroller, dinlenme modu |
| `cover/mirror/` | Ekran yansıtma: sanal ekran, görünüm (Tamamı / Doldur), kapaktaki yüzey |
| `CoverActivity.kt` | Katlıyken ekran koruyucu (kilit ekranının önünde) |
| `system/CoverGuard.kt` | Ekran koruyucuyu kapakta açma, isteğe bağlı "Kapakta hep göster" hizmeti |
| `system/MirrorService.kt` | Yansıtma için `mediaProjection` ön plan hizmeti |
| `render/CollageContent.kt` | Döngülü kolaj |
| `render/GroupContent.kt` | Grup: adımlar sırayla, tam ekran |
| `render/LedMatrix.kt` | LED matris görünümü (AGSL) |
| `render/DotMatrixText.kt` | LED nokta yazı tipi |
| `cover/TouchLock.kt` | Çift dokunuş / 3 sn kilit mantığı (birim testli) |
| `cover/CoverPlayback.kt` | Duraklatma, sonraki/önceki, grup adımları, otomatik ilerleme (Akıllı / Süreli) |
| `data/ContentRepository.kt` | Kitaplık, seçim, ayarlar; `files/items.json` ve `files/media/` |
| `data/MediaImport.kt` | Fotoğraf küçültme, GIF algılama, video dönüştürme |
| `render/CoverContent.kt` | Piksel çizim, fotoğraf/GIF, video ve kayan yazı çizimi |
| `system/` | Bildirim kontrolleri, hızlı ayarlar kutucuğu |
| `widget/` | Kapak widget'ı, kare çizimi ve kare sağlayıcısı |
| `ui/` | Ana sayfa, ayarlar, tanıtım, editörler ve paylaşılan içerik (`IncomingContent.kt`) |
| `ui/components/` | Ortak parçalar: sıralanabilir liste, içerik seçici, Mini kapak kartı, bekleme modu |

Yayınlama için bkz. [PLAY_STORE.md](PLAY_STORE.md).

## Lisans

[MIT](LICENSE) © 2026 MeMoDe
