# Fiche technique — autoradio Android à équiper

Document destiné à être **donné tel quel à un agent IA** avant toute demande de
développement pour cet appareil. Chaque contrainte listée ici a été vérifiée, et
plusieurs d'entre elles ont déjà causé des pannes réelles ; elles ne sont pas
théoriques.

---

## 1. Identité de l'appareil

| Champ | Valeur |
|---|---|
| Modèle affiché | `QUAD-CORE T3 K2001M` |
| APPVER | `K20M_K2001M_NWD_GB-20180427.18.48` |
| Version logicielle | `v.18.03.01 [c.13]` |
| CANbox | Xin Po, `GD V1.01.001BYPS` |
| Véhicule | Dacia Duster mk1 phase 2, 2016, 1.5 dCi 90 ch |

Autoradio chinois de rechange (aftermarket), monté en 2 DIN. Vendu sous de
multiples marques ; seule la chaîne APPVER identifie réellement la variante.

---

## 2. Matériel

| Élément | Détail |
|---|---|
| SoC | **Allwinner T3** — identique au **R40** (`sun8i`), variante automobile de la série A40i |
| CPU | Quad-core ARM **Cortex-A7 @ 1,2 GHz**, 32 Ko L1 I/D, 512 Ko L2 |
| ABI | **`armeabi-v7a` uniquement — 32 bits.** Jamais `arm64-v8a` |
| Extensions | NEON, VFPv4, Thumb-2 |
| GPU | **Mali-400 MP2** |
| API graphiques | **OpenGL ES 1.1 / 2.0 + OpenVG 1.1.** Pas d'ES 3.0. Pas de Vulkan |
| RAM | **1 Go** DDR3 |
| Stockage | 16 Go eMMC |
| Écran | **1024 × 600**, paysage, densité ~171 dpi (bucket `mdpi`) |
| Connectivité | Wi-Fi, GPS, Bluetooth. **Pas de modem cellulaire** |
| Horloge | **Pas de pile RTC** — l'heure dérive à chaque coupure |

### Conséquence n°1 à retenir

`OpenGL ES 2.0` est un plafond dur. Toute application exigeant ES 3.0 ou Vulkan
est **hors sujet, quel que soit son poids ou sa RAM**. C'est ce qui élimine
Organic Maps, alors que l'app tiendrait sans peine en mémoire.

### Conséquence n°2

Pas de cellulaire : tout usage « en ligne » suppose un partage de connexion
depuis un téléphone. Une application qui suppose une connexion permanente est
inutilisable en conduite réelle.

---

## 3. Système

| Élément | Détail |
|---|---|
| Android | **6.0.1**, API **23** (Marshmallow) |
| WebView | Chromium ~**44** |
| Play Services | Ne pas supposer présents |
| Mises à jour | **Aucune.** Abandonné par le fabricant en 2018 ; le bouton `UPDATE` de l'écran d'info ne met à jour que le firmware CANbox depuis une clé USB (`/mnt/udiskh/canupdate/can_app.UPDE`), jamais Android |
| Cross-flash | Les branches `K2001N` / `K2001Q` (Android 7/8/10) sont **d'autres branches**. Les tentatives M→N rapportées finissent en boot loop |

### Ce qu'Android 6 rend **plus** simple

- `BOOT_COMPLETED` est un broadcast implicite normal. Aucune des restrictions
  d'Oreo ne s'applique.
- Pas de limite sur les services d'arrière-plan.
- Avec `targetSdk ≤ 22`, **toutes les permissions sont accordées à
  l'installation** — y compris `SYSTEM_ALERT_WINDOW`. Pas de dialogue runtime.

### Ce qu'Android 6 rend impossible

- **EVS** (External View System, caméra de recul AOSP) : Android 8+ / Automotive
  uniquement. **N'existe pas ici.** Toute suggestion allant dans ce sens est à
  rejeter.
- Android Auto : ~~hors de portée~~ **fonctionne** depuis 2026-11-08 via le récepteur Open Headunit (mode « Headunit Server », sans Bluetooth) — voir `HEADUNIT-INTEL.md` §5.

---

## 4. Construction d'un APK pour cet appareil

```gradle
minSdkVersion 21      // targetSdk ne peut pas être < minSdk
targetSdkVersion 22   // VOLONTAIRE : permissions accordées à l'installation
compileSdkVersion 33  // libre
```

- **Java 8** en source/target (`javac --release 8` ou `-source 8 -target 8`).
- **Pas d'AndroidX**, pas de bibliothèque de support : inutiles ici, et chaque
  Mo compte sur 1 Go.
- Chaîne hors Gradle possible et testée : `aapt` → `javac` → `dx`
  (`dalvik-exchange`) → `aapt add` → `zipalign` → `apksigner`.
  Le SDK Android API 23 et les build-tools sont dans les dépôts Ubuntu
  (`android-sdk-platform-23`, `aapt`, `dalvik-exchange`, `apksigner`).
- Déclarer `<uses-sdk>` **dans le manifeste** : `aapt` ne lit pas `build.gradle`,
  et un APK sans `uses-sdk` se retrouve en `minSdkVersion 1`.

---

## 5. Contraintes WebView — impératif si l'UI est en HTML

Le WebView est de génération Chromium ~44. **Rien de ce qui suit n'est
disponible :**

| Interdit | Remplacer par |
|---|---|
| `let`, `const`, arrow functions, template literals, `class` | **ES5 strict** : `var`, `function()`, concaténation |
| CSS variables (`--x`, `var()`) | Valeurs littérales |
| CSS Grid | Flexbox **avec préfixes `-webkit-`** |
| `clamp()`, `min()`, `max()` | Calcul en JS |
| `font-variant-numeric: tabular-nums` | Boîtes de largeur fixe par caractère |
| `position: sticky` | `absolute` |

### Pièges vérifiés sur cet appareil

1. **`document.documentElement.clientWidth` peut valoir `0`** au premier appel.
   Un calcul d'échelle basé dessus donne `scale(0)` → **écran entièrement noir,
   sans la moindre erreur JavaScript**. Toujours prévoir une chaîne de replis
   (`clientWidth || innerWidth || screen.width || valeur fixe`) et un plancher.
2. **Seuls `transform` et `opacity` s'animent correctement.** Sur un Cortex-A7,
   animer `width`, `top` ou `left` saccade.
3. **Indépendance de résolution** : scène de taille fixe (ex. 1000 × 600 px)
   puis `transform: scale()`.
4. **Polices** : embarquer en base64 après sous-ensemble (`pyftsubset`).
   Aucun accès réseau en roulant.
5. **Images** : encoder chaque image **une seule fois** et réutiliser la même
   source (`el.src = autre.src`).

---

## 6. Règles de comportement propres à une voiture

### Caméra de recul — règle absolue

Le recul est géré par le **MCU, au niveau matériel**, pas par Android. Le fil de
recul ou un message CAN via le CANbox bascule l'entrée vidéo directement
(image en 1–2 s, contre 10–15 s pour une app Camera2/UVC à froid).

1. **Ne jamais écrire d'application de caméra de recul.**
2. **Ne jamais dessiner une UI en `SYSTEM_ALERT_WINDOW` de façon permanente.**
   Une superposition se placerait par-dessus la vue de recul. Problème de
   sécurité, pas d'esthétique.
3. **Ne rien relancer sur minuterie sans vérifier l'état du recul.**

Pour détecter le recul :

```bash
adb shell getprop > avant.txt
# passer la marche arrière et la maintenir
adb shell getprop > pendant.txt
diff avant.txt pendant.txt
```

Lire ensuite via réflexion sur `android.os.SystemProperties`. Candidates :
`persist.sys.reverse`, `sys.backcar`, `persist.sys.backcar`, `sys.reverse.state`.

> **Mise à jour 2026-11-08 :** sur cet appareil, aucune de ces propriétés n'existe. Le recul arrive du MCU →
> `Settings.System mcu_backcar_state`, broadcast `com.android.action.ACTION_BACKCAR_STATE_CHANGE` (extra **byte**
> `extra_back_state`) et nœud `/sys/devices/soc.0/car_reverse.14/state`. Voir `rom-analysis/backcar.md`.

### Launcher

Le launcher d'origine porte la radio, le Bluetooth et l'intégration CANbox.
**Ne jamais déclarer `CATEGORY_HOME`.** HOME doit toujours ramener au launcher.

### Écran allumé

`FLAG_KEEP_SCREEN_ON` sur la fenêtre, **pas** un `WakeLock`.

### `isScreenOn()` n'est pas fiable

Certains ROMs répondent `false` en permanence. Ne pas s'en servir comme
condition bloquante.

### Minuteries

**`SystemClock.elapsedRealtime()`**, pas `uptimeMillis()`. Préférer un **tick
périodique qui compare un horodatage** à un `postDelayed` unique.

### Services

Les ROMs de ce type ont leur **propre liste blanche « Démarrage auto »**, et
l'application doit souvent avoir été **ouverte au moins une fois à la main**.
Prévoir un chien de garde `AlarmManager`.

### Heure

Pas de pile RTC : activer la **synchronisation GPS de l'heure**.

---

## 7. Conception d'interface pour cet écran

- 1024 × 600, regardé à ~1 m, en conduisant. Repère : 1,5 s max par coup d'œil.
- Humaniste pour les petits textes ; carrées réservées aux logos/gros corps.
- Pas de mouvement permanent.
- Nuit : contraste négatif. L'opacité CSS ne baisse pas le rétroéclairage LCD.
- Une seule couleur d'accent.

---

## 8. Compatibilité applicative constatée

| Application | Verdict |
|---|---|
| MapFactor Navigator | ✅ jusqu'à 7.3.51 |
| OsmAnd | ✅ jusqu'à 4.7.10 |
| Organic Maps | ❌ exige OpenGL ES 3.0 / Vulkan |
| Google Maps Go / Google Maps / Waze | ❌ |
| F-Droid | ✅ 1.23.2 max (2.x exige API 24) ; installer d'abord ISRG Root X1, dépôt « Archive » — désinstallé le 2026-11-08 à la demande du propriétaire |
| VLC | ✅ 3.7.2 (build armeabi-v7a) |
| AndrOBD | ✅ 2.7.10 |
| Open Headunit (récepteur Android Auto) | ✅ 3.5.0 |
