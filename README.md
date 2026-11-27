# 🛴 Letzte Meile

**Hybride Routenplanung für E-Scooter, Fahrrad & ÖPNV im gesamten DACH-Raum.**

*Letzte Meile* kombiniert dein eigenes Mikromobilitäts-Fahrzeug mit dem regionalen Nahverkehr. Statt starrer Fußwege plant die App realistische Erste- und Letzte-Meile-Strecken über echte Rad- und Scooterwege, behält deine tatsächliche Akkureichweite im Blick und übergibt deine Verbindung direkt an deine lokale ÖPNV-App.

---

## 📥 Download & Installation

Die fertige, signierte Android-App (APK) findest du direkt unter den **[GitHub Releases](https://github.com/CimShedn/letztemeile/releases)**.

1. Lade die aktuelle `LetzteMeile-vX.X.X.apk` auf dein Android-Smartphone herunter.
2. Öffne die Datei und erlaube bei Bedarf die Installation aus unbekannten Quellen.
3. Beim ersten Start wählst du einmalig deine bevorzugte ÖPNV-Region – fertig!

---

## ✨ Features

* **Kombi-Routing (Scooter + ÖPNV):** Berechnet automatisch den Weg zur nächsten passenden Haltestelle, die ÖPNV-Strecke und die letzte Meile zum Ziel.
* **Echtes Rad- & Scooter-Routing:** Nutzt Straßen- und Radweg-Routing (OSRM) inklusive **Snap-to-Road**, sodass gesetzte Karten-Pins sauber auf den nächstgelegenen befahrbaren Weg einrasten.
* **Realistische Reichweiten-Prognose:** Berechnet Hin- und Rückweg inklusive Restreichweite – mit individuell einstellbarem **Real-Reichweiten-Faktor** (für Kälte, Steigungen und Stop-and-Go im Stadtverkehr).
* **Eigene Scooter-Profile:** Wähle aus gängigen Standard-Modellen oder lege deinen eigenen E-Scooter (Akku in Wh, Leistung, Reichweite, Geschwindigkeit) dauerhaft an.
* **DACH-weite ÖPNV-Anbindung:** Unterstützt u. a. **MVV (München), VBB/BVG (Berlin & Brandenburg), HVV (Hamburg), RMV (Frankfurt/Rhein-Main), VVS (Stuttgart), VRS (Köln/Bonn), ÖBB (Österreich) & SBB (Schweiz)**.
* **Nahtlose App-Übergabe & Kopier-Hilfe:** Übergibt bereinigte Adressen und Stationen direkt an deine ÖPNV-App oder Google Maps – inklusive praktischer Kopier-Hilfe für Live-Abfahrtszeiten und Splitscreen.
* **Modernes, barrierearmes Design:** Augenfreundliches Dark-Theme mit hohen Kontrasten für gute Lesbarkeit auch unterwegs bei Tageslicht.
* ![Downloads](https://img.shields.io/github/downloads/CimShedn/letztemeile/total)

---

## 🛠️ Tech Stack

* **Sprache & Plattform:** Kotlin / Android (Jetpack Compose)
* **Karten:** MapLibre & OpenFreeMap (OpenStreetMap)
* **Routing:** OSRM (Fahrrad-/Scooter-Profil)
* **ÖPNV-Daten:** Eigene, regionsbasierte OpenStreetMap-JSON-Struktur

---

## 💻 Selbst bauen (Build from Source)

1. Repository klonen oder als ZIP herunterladen:

```bash
git clone https://github.com/CimShedn/letztemeile.git
```
